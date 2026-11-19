package com.opsmate.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads system definitions from YAML files into {@link SystemsConfig} records.
 *
 * <p>Each {@code *.yml} file in the configured directory describes a single system. The
 * loader is intentionally forgiving: unknown keys are ignored and a missing directory
 * yields an empty list (logged as a warning) so the application can still boot.</p>
 */
public final class SystemConfig {

    private static final Logger log = LoggerFactory.getLogger(SystemConfig.class);

    private SystemConfig() {
    }

    /**
     * Loads every {@code *.yml} file in {@code directory}.
     *
     * @param directory directory containing system YAML files
     * @return immutable list of parsed systems, sorted by id
     * @throws IOException if a file cannot be read
     */
    public static List<SystemsConfig> load(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            log.warn("Systems directory '{}' does not exist; no systems registered", directory);
            return List.of();
        }
        List<SystemsConfig> systems = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> yamlFiles = files
                    .filter(p -> p.getFileName().toString().endsWith(".yml")
                            || p.getFileName().toString().endsWith(".yaml"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (Path file : yamlFiles) {
                SystemsConfig config = loadOne(file);
                systems.add(config);
                log.info("Loaded system '{}' ({}) from {}", config.id(), config.connectorType(), file.getFileName());
            }
        }
        return List.copyOf(systems);
    }

    /**
     * Loads a single system YAML file.
     *
     * @param file path to the YAML file
     * @return the parsed system
     * @throws IOException if the file cannot be read
     */
    @SuppressWarnings("unchecked")
    public static SystemsConfig loadOne(Path file) throws IOException {
        String content = Files.readString(file);
        Object root = new Yaml().load(content);
        if (!(root instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("System file must contain a YAML mapping: " + file);
        }
        Map<String, Object> m = (Map<String, Object>) map;

        String id = requireString(m, "id", file);
        String name = stringOr(m, "name", id);
        ConnectorType connectorType = ConnectorType.valueOf(
                requireString(m, "connectorType", file).trim().toUpperCase());
        boolean active = boolOr(m, "active", false);
        String baseUrl = stringOrNull(m, "baseUrl");

        List<SystemsConfig.ServiceConfig> services = new ArrayList<>();
        for (Map<String, Object> s : listOfMaps(m.get("services"))) {
            String svcName = stringOr(s, "name", null);
            if (svcName == null) {
                throw new IllegalArgumentException("Service entry missing 'name' in " + file);
            }
            services.add(new SystemsConfig.ServiceConfig(
                    svcName,
                    stringOrNull(s, "healthUrl"),
                    stringOrNull(s, "containerName")));
        }

        Map<String, SystemsConfig.NamedQuery> namedQueries = new LinkedHashMap<>();
        Object nqRaw = m.get("namedQueries");
        if (nqRaw instanceof Map<?, ?> nqMap) {
            for (Map.Entry<?, ?> e : nqMap.entrySet()) {
                String qName = String.valueOf(e.getKey());
                if (e.getValue() instanceof Map<?, ?> qm) {
                    Map<String, Object> q = (Map<String, Object>) qm;
                    namedQueries.put(qName, new SystemsConfig.NamedQuery(
                            stringOr(q, "description", ""),
                            requireString(q, "sql", file)));
                }
            }
        }

        List<SystemsConfig.AllowedAction> allowedActions = new ArrayList<>();
        for (Map<String, Object> a : listOfMaps(m.get("allowedActions"))) {
            String actionName = stringOr(a, "name", null);
            if (actionName == null) {
                throw new IllegalArgumentException("Action entry missing 'name' in " + file);
            }
            String rawRisk = stringOr(a, "riskLevel", "LOW");
            RiskLevel riskLevel;
            try {
                riskLevel = RiskLevel.valueOf(rawRisk.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid riskLevel '" + rawRisk + "' for action '"
                        + actionName + "' in " + file, ex);
            }
            allowedActions.add(new SystemsConfig.AllowedAction(
                    actionName,
                    stringOr(a, "description", ""),
                    riskLevel,
                    boolOr(a, "requiresApproval", false)));
        }

        return new SystemsConfig(id, name, connectorType, active, baseUrl,
                List.copyOf(services), Map.copyOf(namedQueries), List.copyOf(allowedActions));
    }

    // ------------------------------------------------------------------
    // Small parsing helpers
    // ------------------------------------------------------------------

    private static List<Map<String, Object>> listOfMaps(Object raw) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> mm) {
                    result.add((Map<String, Object>) mm);
                }
            }
        }
        return result;
    }

    private static String requireString(Map<String, Object> map, String key, Path file) {
        Object v = map.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            throw new IllegalArgumentException("Missing required field '" + key + "' in " + file);
        }
        return String.valueOf(v);
    }

    private static String stringOr(Map<String, Object> map, String key, String fallback) {
        Object v = map.get(key);
        return v == null ? fallback : String.valueOf(v);
    }

    private static String stringOrNull(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static boolean boolOr(Map<String, Object> map, String key, boolean fallback) {
        Object v = map.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v != null) {
            return Boolean.parseBoolean(String.valueOf(v));
        }
        return fallback;
    }
}