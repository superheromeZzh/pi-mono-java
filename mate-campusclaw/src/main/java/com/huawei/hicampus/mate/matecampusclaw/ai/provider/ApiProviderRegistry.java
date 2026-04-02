package com.huawei.hicampus.mate.matecampusclaw.ai.provider;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Registry for {@link ApiProvider} instances, indexed by {@link Api} type.
 *
 * <p>Spring-managed providers (annotated with {@code @Component}) are
 * automatically collected via {@code @Autowired}. Additional providers
 * can be registered at runtime with a {@code sourceId} to support
 * dynamic extension and bulk unregistration.
 *
 * <p>Thread-safe: all mutation methods synchronize on the internal lock.
 */
@Service
public class ApiProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(ApiProviderRegistry.class);

    private static final String SPRING_SOURCE_ID = "__spring__";

    private final Object lock = new Object();

    /** Primary index: Api -> provider (last registration wins). */
    private final Map<Api, ApiProvider> providersByApi = new ConcurrentHashMap<>();

    /** Tracks which sourceId registered which providers, for bulk unregister. */
    private final Map<String, List<ApiProvider>> providersBySource = new ConcurrentHashMap<>();

    /**
     * Constructs the registry. Spring-discovered providers are registered
     * automatically under the {@code "__spring__"} source id.
     *
     * @param springProviders providers discovered by Spring DI (may be empty)
     */
    @Autowired
    public ApiProviderRegistry(@Autowired(required = false) List<ApiProvider> springProviders) {
        if (springProviders != null) {
            for (var provider : springProviders) {
                doRegister(provider, SPRING_SOURCE_ID);
            }
            log.info("Registered {} Spring-managed ApiProvider(s): {}",
                springProviders.size(),
                springProviders.stream().map(p -> p.getApi().value()).toList());
        }
    }

    /**
     * Looks up a provider by API type.
     *
     * @param api the API protocol
     * @return the registered provider, or empty if none is registered for that API
     */
    public Optional<ApiProvider> getProvider(Api api) {
        return Optional.ofNullable(providersByApi.get(api));
    }

    /**
     * Returns all currently registered providers (unordered, deduplicated).
     *
     * @return an unmodifiable list of all registered providers
     */
    public List<ApiProvider> getProviders() {
        synchronized (lock) {
            // Deduplicate: a provider may appear in multiple sources but
            // the Api index already deduplicates by Api key.
            return List.copyOf(new LinkedHashSet<>(providersByApi.values()));
        }
    }

    /**
     * Registers a provider at runtime under the given source id.
     * If a provider for the same {@link Api} already exists, it is replaced.
     *
     * @param provider the provider to register
     * @param sourceId identifier for the registration source (used for bulk unregister)
     * @throws NullPointerException if provider or sourceId is null
     */
    public void register(ApiProvider provider, String sourceId) {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        doRegister(provider, sourceId);
        log.debug("Registered provider for api={} from source={}", provider.getApi().value(), sourceId);
    }

    /**
     * Unregisters all providers that were registered under the given source id.
     * Only removes from the Api index if the current mapping still points to
     * a provider from that source.
     *
     * @param sourceId the source identifier
     */
    public void unregister(String sourceId) {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        synchronized (lock) {
            var providers = providersBySource.remove(sourceId);
            if (providers != null) {
                for (var provider : providers) {
                    // Only remove from the Api index if this provider is still the current one
                    providersByApi.remove(provider.getApi(), provider);
                }
                log.debug("Unregistered {} provider(s) from source={}", providers.size(), sourceId);
            }
        }
    }

    /**
     * Removes all registered providers (including Spring-discovered ones).
     */
    public void clear() {
        synchronized (lock) {
            providersByApi.clear();
            providersBySource.clear();
            log.debug("Cleared all providers from registry");
        }
    }

    private void doRegister(ApiProvider provider, String sourceId) {
        synchronized (lock) {
            providersByApi.put(provider.getApi(), provider);
            providersBySource.computeIfAbsent(sourceId, k -> new ArrayList<>()).add(provider);
        }
    }
}
