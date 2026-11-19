/**
 * Agent tools exposed to the LLM via Spring AI.
 *
 * <p>Tool groups:</p>
 * <ul>
 *   <li>{@link com.opsmate.backend.agent.tools.TriageTools} — read-only triage over the active system.</li>
 *   <li>{@link com.opsmate.backend.agent.tools.KnowledgeTools} — pgvector runbook / past-case search.</li>
 *   <li>{@link com.opsmate.backend.agent.tools.DiagnosticTools} — read-only diagnostics bound to the current case's system.</li>
 *   <li>{@link com.opsmate.backend.agent.tools.ActionTools} — gated remediation actions.</li>
 * </ul>
 *
 * <p>Compliance has intentionally NO tools here: it is a deterministic rule engine plus an
 * LLM explanation step only, and must never call systems or mutate state.</p>
 */
package com.opsmate.backend.agent.tools;