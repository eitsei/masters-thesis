package com.dippa.ragbenchmark.infrastructure.config;

import com.dippa.ragbenchmark.domain.ModelRouter;
import com.dippa.ragbenchmark.infrastructure.GoogleGenAiModelRouter;
import com.dippa.ragbenchmark.infrastructure.NoOpModelRouter;
import com.dippa.ragbenchmark.infrastructure.OracleModelRouter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration registering the active {@link ModelRouter} bean according to
 * {@code rag.router.provider} (classifier, oracle, or noop).
 */
@Configuration
public class ModelRouterConfiguration {

    @Bean
    @ConditionalOnProperty(name = "rag.router.provider", havingValue = "classifier")
    public ModelRouter googleGenAiModelRouter(
            ObjectProvider<GoogleGenAiChatModel> googleGenAiChatModelProvider,
            ObjectProvider<ChatModel> genericChatModelProvider,
            ModelCatalogProperties catalogProperties,
            RouterProperties routerProperties,
            MeterRegistry meterRegistry) {
        ChatModel model = googleGenAiChatModelProvider.getIfAvailable();
        if (model == null) {
            model = genericChatModelProvider.getIfAvailable();
        }
        return new GoogleGenAiModelRouter(
                model,
                catalogProperties,
                routerProperties,
                meterRegistry);
    }

    @Bean
    @ConditionalOnProperty(name = "rag.router.provider", havingValue = "oracle")
    public ModelRouter oracleModelRouter(
            ModelCatalogProperties catalogProperties,
            RouterProperties routerProperties) {
        return new OracleModelRouter(catalogProperties, routerProperties);
    }

    @Bean
    @ConditionalOnMissingBean(ModelRouter.class)
    public ModelRouter noOpModelRouter(RouterProperties routerProperties) {
        return new NoOpModelRouter(routerProperties);
    }
}
