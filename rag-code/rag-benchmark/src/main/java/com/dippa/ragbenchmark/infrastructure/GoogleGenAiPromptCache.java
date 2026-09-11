package com.dippa.ragbenchmark.infrastructure;

import com.dippa.ragbenchmark.domain.CacheLookupResult;
import com.dippa.ragbenchmark.domain.CacheRequest;
import com.dippa.ragbenchmark.domain.PromptCache;
import com.dippa.ragbenchmark.domain.PromptCacheUnavailableException;
import com.dippa.ragbenchmark.infrastructure.config.PromptCacheProperties;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.google.genai.cache.CachedContentRequest;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContent;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContentService;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Production prompt cache adapter integrating with Google GenAI / Vertex AI Context Caching.
 *
 * <p>Provides:
 * <ul>
 *   <li>Single-flight concurrency to guarantee only one remote cache is created per unique key</li>
 *   <li>Local TTL and expiration tracking to prevent stale cache reuse</li>
 *   <li>Application-instance ownership tracking for safe, targeted cleanup</li>
 *   <li>Automatic resource cleanup on application shutdown via {@link DisposableBean}</li>
 * </ul>
 * </p>
 */
@Component
@ConditionalOnProperty(name = "rag.prompt-cache.provider", havingValue = "google-genai")
public class GoogleGenAiPromptCache implements PromptCache, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(GoogleGenAiPromptCache.class);

    private final GoogleGenAiCachedContentService cachedContentService;
    private final PromptCacheProperties properties;
    private final String instanceId;

    private final ConcurrentMap<String, CacheEntry> localEntries = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CompletableFuture<CacheEntry>> inFlightCreations = new ConcurrentHashMap<>();
    private final Set<String> ownedResourceNames = ConcurrentHashMap.newKeySet();

    public record CacheEntry(String cacheId, long tokenCount, Instant expiresAt) {
        public boolean isExpired() {
            return expiresAt != null && expiresAt.isBefore(Instant.now());
        }
    }

    @Autowired
    public GoogleGenAiPromptCache(
            ObjectProvider<GoogleGenAiCachedContentService> serviceProvider,
            PromptCacheProperties properties) {
        this(serviceProvider != null ? serviceProvider.getIfAvailable() : null, properties);
    }

    public GoogleGenAiPromptCache(
            GoogleGenAiCachedContentService cachedContentService,
            PromptCacheProperties properties) {
        this(cachedContentService, properties, UUID.randomUUID().toString().substring(0, 8));
    }

    public GoogleGenAiPromptCache(
            GoogleGenAiCachedContentService cachedContentService,
            PromptCacheProperties properties,
            String instanceId) {
        this.cachedContentService = cachedContentService;
        this.properties = properties != null ? properties : PromptCacheProperties.defaultProperties();
        this.instanceId = instanceId != null ? instanceId : UUID.randomUUID().toString().substring(0, 8);
    }

    public String getInstanceId() {
        return instanceId;
    }

    public Set<String> getOwnedResourceNames() {
        return Set.copyOf(ownedResourceNames);
    }

    @Override
    public boolean isConfigured() {
        return cachedContentService != null;
    }

    @Override
    public CacheLookupResult getOrCreate(CacheRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        if (!isConfigured()) {
            return CacheLookupResult.disabled();
        }

        if (request.estimatedTokens() < properties.minTokens()) {
            logger.debug("Context estimated tokens ({}) below cache threshold ({}); marking NOT_ELIGIBLE",
                    request.estimatedTokens(), properties.minTokens());
            return CacheLookupResult.notEligible(request.estimatedTokens());
        }

        String key = request.cacheKey();

        // 1. Check local cache and evict if expired
        CacheEntry existingEntry = localEntries.get(key);
        if (existingEntry != null) {
            if (existingEntry.isExpired()) {
                logger.info("Local cache entry for key '{}' expired at {}; evicting", key, existingEntry.expiresAt());
                localEntries.remove(key, existingEntry);
            } else {
                logger.debug("Explicit cache hit for key '{}' -> cacheId={}", key, existingEntry.cacheId());
                return CacheLookupResult.hit(existingEntry.cacheId(), existingEntry.tokenCount(), existingEntry.expiresAt());
            }
        }

        // 2. Single-flight creation for in-flight requests
        CompletableFuture<CacheEntry> promise = new CompletableFuture<>();
        CompletableFuture<CacheEntry> inFlight = inFlightCreations.putIfAbsent(key, promise);
        if (inFlight != null) {
            try {
                CacheEntry sharedEntry = inFlight.join();
                logger.debug("Reused concurrent creation for key '{}' -> cacheId={}", key, sharedEntry.cacheId());
                return CacheLookupResult.hit(sharedEntry.cacheId(), sharedEntry.tokenCount(), sharedEntry.expiresAt());
            } catch (CompletionException ce) {
                Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
                if (cause instanceof PromptCacheUnavailableException pcue) {
                    throw pcue;
                }
                throw new PromptCacheUnavailableException("Failed waiting for concurrent cache creation: " + cause.getMessage(), cause);
            }
        }

        // 3. First thread: perform remote creation
        try {
            CacheEntry createdEntry = doCreate(request);
            localEntries.put(key, createdEntry);
            ownedResourceNames.add(createdEntry.cacheId());
            promise.complete(createdEntry);
            logger.info("Created explicit Google GenAI cache for key '{}' -> cacheId={}, tokens={}, expiresAt={}",
                    key, createdEntry.cacheId(), createdEntry.tokenCount(), createdEntry.expiresAt());
            return CacheLookupResult.miss(createdEntry.cacheId(), createdEntry.tokenCount(), createdEntry.expiresAt());
        } catch (Throwable t) {
            promise.completeExceptionally(t);
            if (t instanceof PromptCacheUnavailableException pcue) {
                throw pcue;
            }
            throw new PromptCacheUnavailableException("Failed to create Google GenAI cached content: " + t.getMessage(), t);
        } finally {
            inFlightCreations.remove(key);
        }
    }

    private CacheEntry doCreate(CacheRequest request) {
        try {
            Content systemInstructionContent = Content.fromParts(Part.fromText(request.systemInstruction()));
            Content contextContent = Content.fromParts(Part.fromText(request.contextContent()));

            String shortKey = request.cacheKey().length() > 8 ? request.cacheKey().substring(0, 8) : request.cacheKey();
            String displayName = properties.displayNamePrefix() + instanceId + "-" + shortKey;

            CachedContentRequest createRequest = CachedContentRequest.builder()
                    .model(request.model())
                    .displayName(displayName)
                    .systemInstruction(systemInstructionContent)
                    .contents(List.of(contextContent))
                    .ttl(properties.ttl())
                    .build();

            GoogleGenAiCachedContent cachedContent = cachedContentService.create(createRequest);
            String cacheId = cachedContent.getName();
            long tokenCount = request.estimatedTokens();
            if (cachedContent.getUsageMetadata() != null
                    && cachedContent.getUsageMetadata().totalTokenCount() != null
                    && cachedContent.getUsageMetadata().totalTokenCount().isPresent()) {
                tokenCount = cachedContent.getUsageMetadata().totalTokenCount().get().longValue();
            }

            Instant expiresAt = cachedContent.getExpireTime() != null
                    ? cachedContent.getExpireTime()
                    : Instant.now().plus(properties.ttl());

            return new CacheEntry(cacheId, tokenCount, expiresAt);
        } catch (Exception e) {
            logger.error("Error creating Google GenAI cached content: {}", e.getMessage(), e);
            throw new PromptCacheUnavailableException("Failed to create Google GenAI cached content: " + e.getMessage(), e);
        }
    }

    @Override
    public void clearOwned() {
        if (!isConfigured()) {
            return;
        }
        logger.info("Clearing {} owned cached content resource(s) for instance '{}'",
                ownedResourceNames.size(), instanceId);

        for (String resourceName : ownedResourceNames) {
            try {
                boolean deleted = cachedContentService.delete(resourceName);
                logger.info("Deleted remote cached content resource '{}': success={}", resourceName, deleted);
            } catch (Exception e) {
                logger.warn("Failed to delete remote cached content resource '{}': {}", resourceName, e.getMessage());
            }
        }
        ownedResourceNames.clear();
        localEntries.clear();
        inFlightCreations.clear();
    }

    @Override
    public void destroy() {
        clearOwned();
    }
}
