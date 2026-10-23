package com.forgeopslabs.fluxgate.spring.openfeature;

import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenFeatureRegistrarTest {

    private final OpenFeatureAPI api = mock(OpenFeatureAPI.class);
    private final FeatureProvider provider = mock(FeatureProvider.class);

    {
        when(provider.getMetadata()).thenReturn(() -> "FluxGate");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "  ")
    void withoutProviderNameRegistersTheDefaultProvider(String name) {
        new OpenFeatureRegistrar(api, provider, name).afterPropertiesSet();

        verify(api).setProvider(provider);
    }

    @Test
    void providerNameIsTheOpenFeatureDomain() {
        new OpenFeatureRegistrar(api, provider, "payments").afterPropertiesSet();

        verify(api).setProvider("payments", provider);
        verify(api, never()).setProvider(provider);
    }
}
