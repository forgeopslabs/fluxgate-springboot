package com.fluxgate.starter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * Fans callbacks out to listeners and isolates the client from their failures.
 */
final class ClientListeners {

    private static final Logger logger = LoggerFactory.getLogger(ClientListeners.class);

    private final List<FluxGateClientListener> listeners;

    ClientListeners(List<FluxGateClientListener> listeners) {
        this.listeners = List.copyOf(listeners);
    }

    boolean isEmpty() {
        return listeners.isEmpty();
    }

    void publish(Consumer<FluxGateClientListener> callback) {
        for (FluxGateClientListener listener : listeners) {
            try {
                callback.accept(listener);
            } catch (RuntimeException e) {
                logger.warn("FluxGate client listener {} failed", listener.getClass().getName(), e);
            }
        }
    }
}
