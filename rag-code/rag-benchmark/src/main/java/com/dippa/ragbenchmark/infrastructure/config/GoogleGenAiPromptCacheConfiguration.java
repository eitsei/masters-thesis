package com.dippa.ragbenchmark.infrastructure.config;

import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ensures a {@link GoogleGenAiCachedContentService} bean is available from {@link GoogleGenAiChatModel}
 * when active, bridging Spring AI's auto-configuration condition gap.
 */
@Configuration
@ConditionalOnClass({GoogleGenAiChatModel.class, GoogleGenAiCachedContentService.class})
public class GoogleGenAiPromptCacheConfiguration {

    @Bean
    @ConditionalOnBean(GoogleGenAiChatModel.class)
    @ConditionalOnMissingBean(GoogleGenAiCachedContentService.class)
    public GoogleGenAiCachedContentService googleGenAiCachedContentService(GoogleGenAiChatModel chatModel) {
        return chatModel.getCachedContentService();
    }
}
