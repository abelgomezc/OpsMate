package com.opsmate.backend.config;

import com.opsmate.backend.connector.SystemConnector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds every registered system and resolves the active one plus its connector.
 *
 * <p>The registry is the single source of truth for "which system is active" and for
 * connector instances. Connectors are created lazily via {@link ConnectorFactory} and
 * cached by system id.</p>
 */
public class SystemRegistry {

    private final Map<String, SystemsConfig> systemsById;
    private final String activeSystemId;
    private final ConnectorFactory connectorFactory;
    private final Map<String, SystemConnector> connectorCache = new ConcurrentHashMap<>();

    public SystemRegistry(List<SystemsConfig> systems) {
        this(systems, new ConnectorFactory());
    }

    public SystemRegistry(List<SystemsConfig> systems, ConnectorFactory connectorFactory) {
        Map<String, SystemsConfig> byId = new LinkedHashMap<>();
        String active = null;
        for (SystemsConfig system : systems) {
            if (byId.put(system.id(), system) != null) {
                throw new IllegalArgumentException("Duplicate system id: " + system.id());
            }
            if (system.active() && active == null) {
                active = system.id();
            }
        }
        this.systemsById = Map.copyOf(byId);
        this.activeSystemId = active;
        this.connectorFactory = connectorFactory;
    }

    /**
     * @return the system marked {@code active: true} in YAML
     * @throws IllegalStateException if no active system is registered
     */
    public SystemsConfig getActiveSystem() {
        if (activeSystemId == null) {
            throw new IllegalStateException("No active system is registered");
        }
        return systemsById.get(activeSystemId);
    }

    /**
     * @return id of the active system, or empty if none is active
     */
    public Optional<String> activeSystemId() {
        return Optional.ofNullable(activeSystemId);
    }

    /**
     * @param id system identifier
     * @return the registered system
     * @throws IllegalArgumentException if no system with {@code id} exists
     */
    public SystemsConfig getSystem(String id) {
        SystemsConfig system = systemsById.get(id);
        if (system == null) {
            throw new IllegalArgumentException("Unknown system id: " + id
                    + "; registered: " + systemsById.keySet());
        }
        return system;
    }

    /**
     * @return every registered system in load order
     */
    public List<SystemsConfig> listSystems() {
        return new ArrayList<>(systemsById.values());
    }

    /**
     * Resolves (and caches) the connector for a system id.
     *
     * @param id system identifier
     * @return the connector for that system
     * @throws IllegalArgumentException if the system is not registered
     */
    public SystemConnector getConnector(String id) {
        getSystem(id); // validates existence
        return connectorCache.computeIfAbsent(id, key -> connectorFactory.create(getSystem(key)));
    }
}