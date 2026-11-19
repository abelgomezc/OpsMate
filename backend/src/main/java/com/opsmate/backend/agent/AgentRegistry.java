package com.opsmate.backend.agent;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of agent beans discovered from the Spring context.
 *
 * <p>Agents are registered by their {@link Agent#name()} return value.</p>
 */
@Component
public class AgentRegistry {

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();

    public AgentRegistry(ApplicationContext context) {
        Map<String, Agent> beans = context.getBeansOfType(Agent.class);
        for (Agent agent : beans.values()) {
            String name = agent.name();
            if (agents.putIfAbsent(name, agent) != null) {
                throw new IllegalStateException("Duplicate agent name: " + name);
            }
        }
    }

    /**
     * @return agent by name, or empty if not found
     */
    public Optional<Agent> get(String name) {
        return Optional.ofNullable(agents.get(name));
    }

    /**
     * @return agent by name, throws if not found
     */
    public Agent getRequired(String name) {
        return get(name).orElseThrow(() -> new IllegalArgumentException("Agent not found: " + name));
    }

    /**
     * @return all registered agents
     */
    public Map<String, Agent> getAll() {
        return Map.copyOf(agents);
    }
}