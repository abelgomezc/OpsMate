package com.opsmate.backend.agent.tools;

import com.opsmate.backend.config.ConnectorFactory;
import com.opsmate.backend.config.ConnectorType;
import com.opsmate.backend.config.RiskLevel;
import com.opsmate.backend.config.SystemsConfig;
import com.opsmate.backend.config.SystemRegistry;
import com.opsmate.backend.connector.model.ActionResult;
import com.opsmate.backend.context.CaseContext;
import com.opsmate.backend.testutil.FakeSimEnvServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link ActionTools} executes allowed actions but rejects and records any
 * action that is not in the current system's {@code allowedActions} list.
 */
class ActionNotAllowedTest {

    private static FakeSimEnvServer server;
    private static SystemRegistry registry;
    private static ActionTools actionTools;

    @BeforeAll
    static void setUp() throws Exception {
        server = new FakeSimEnvServer();

        SystemsConfig simulated = new SystemsConfig(
                "simulated",
                "Simulated Payments Environment",
                ConnectorType.SIMULATED,
                true,
                server.baseUrl(),
                List.of(new SystemsConfig.ServiceConfig("payments", "ignored", null)),
                Map.of(),
                List.of(
                        new SystemsConfig.AllowedAction("restart", "Restart service",
                                RiskLevel.MEDIUM, false),
                        new SystemsConfig.AllowedAction("scale-up", "Scale up",
                                RiskLevel.HIGH, true)));

        registry = new SystemRegistry(List.of(simulated), new ConnectorFactory());
        actionTools = new ActionTools(registry, false);
    }

    @AfterAll
    static void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void allowedAction_isExecuted() {
        int denialsBefore = actionTools.deniedActions().size();
        ActionResult result = CaseContext.runIn(new CaseContext("case-1", "simulated"),
                () -> actionTools.executeAllowedAction("restart", Map.of("service", "payments")));

        assertNotNull(result);
        assertEquals("restart", result.action());
        assertTrue(result.resolved());
        assertEquals(denialsBefore, actionTools.deniedActions().size(),
                "an allowed action must not record a denial");
    }

    @Test
    void disallowedAction_isRejectedAndRecorded() {
        ActionNotAllowedException ex = assertThrows(ActionNotAllowedException.class,
                () -> CaseContext.runIn(new CaseContext("case-2", "simulated"),
                        () -> actionTools.executeAllowedAction("drop-database",
                                Map.of("service", "payments"))));

        assertEquals("drop-database", ex.action());
        assertEquals("simulated", ex.systemId());
        assertEquals(1, actionTools.deniedActions().size());
        assertEquals("drop-database", actionTools.deniedActions().get(0).action());
    }
}