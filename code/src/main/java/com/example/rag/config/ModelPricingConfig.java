package com.example.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "rag.pricing")
public class ModelPricingConfig {

    private Map<String, ModelPrice> models = new HashMap<>();

    public Map<String, ModelPrice> getModels() {
        return models;
    }

    public void setModels(Map<String, ModelPrice> models) {
        this.models = models;
    }

    public static class ModelPrice {
        private double inputCostPerMillion;
        private double outputCostPerMillion;
        private double cachedInputCostPerMillion;

        public double getInputCostPerMillion() {
            return inputCostPerMillion;
        }

        public void setInputCostPerMillion(double inputCostPerMillion) {
            this.inputCostPerMillion = inputCostPerMillion;
        }

        public double getOutputCostPerMillion() {
            return outputCostPerMillion;
        }

        public void setOutputCostPerMillion(double outputCostPerMillion) {
            this.outputCostPerMillion = outputCostPerMillion;
        }

        public double getCachedInputCostPerMillion() {
            return cachedInputCostPerMillion;
        }

        public void setCachedInputCostPerMillion(double cachedInputCostPerMillion) {
            this.cachedInputCostPerMillion = cachedInputCostPerMillion;
        }
    }
}
