package com.forgeopslabs.fluxgate.spring.openfeature;

import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * Registers a provider with {@link OpenFeatureAPI} at startup
 * ({@code fluxgate.openfeature.auto-register=true}). Registration does not
 * block: the SDK initializes the provider in the background and emits
 * {@code PROVIDER_READY} or {@code PROVIDER_ERROR}.
 */
public class OpenFeatureRegistrar implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(OpenFeatureRegistrar.class);

    private final OpenFeatureAPI api;
    private final FeatureProvider provider;
    private final String domain;

    /**
     * @param api      the OpenFeature API to register with
     * @param provider the provider to register
     * @param domain   the OpenFeature domain, or {@code null}/blank to register
     *                 the default provider
     */
    public OpenFeatureRegistrar(OpenFeatureAPI api, FeatureProvider provider, String domain) {
        this.api = api;
        this.provider = provider;
        this.domain = domain;
    }

    @Override
    public void afterPropertiesSet() {
        if (domain == null || domain.isBlank()) {
            logger.info("Registering {} as the default OpenFeature provider", provider.getMetadata().getName());
            api.setProvider(provider);
        } else {
            logger.info("Registering {} as the OpenFeature provider for domain '{}'",
                    provider.getMetadata().getName(), domain);
            api.setProvider(domain, provider);
        }
    }
}
