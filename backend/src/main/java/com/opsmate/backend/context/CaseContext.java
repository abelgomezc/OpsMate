package com.opsmate.backend.context;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Per-case execution context propagated with a {@link ScopedValue}.
 *
 * <p>Holds the {@code caseId} and the {@code systemId} of the case currently being handled.
 * {@code DiagnosticTools} and {@code ActionTools} read {@link #current()} to resolve the
 * connector of the right system instead of relying on a global/ambient "active" system.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * CaseContext.runIn(new CaseContext(caseId, systemId), () -> tools.doWork());
 * }</pre>
 */
public final class CaseContext {

    private static final ScopedValue<CaseContext> CURRENT = ScopedValue.newInstance();

    private final String caseId;
    private final String systemId;

    public CaseContext(String caseId, String systemId) {
        this.caseId = Objects.requireNonNull(caseId, "caseId");
        this.systemId = Objects.requireNonNull(systemId, "systemId");
    }

    public String caseId() {
        return caseId;
    }

    public String systemId() {
        return systemId;
    }

    /**
     * @return the context bound to the current thread, or {@code null} if none
     */
    public static CaseContext current() {
        return CURRENT.get();
    }

    /**
     * Binds {@code ctx} for the duration of {@code supplier} and returns its result.
     *
     * @param ctx      context to bind
     * @param supplier work to run within the scope
     * @return the supplier's result
     */
    public static <T> T runIn(CaseContext ctx, Supplier<T> supplier) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(supplier, "supplier");
        final Object[] holder = new Object[1];
        ScopedValue.where(CURRENT, ctx).run(() -> holder[0] = supplier.get());
        @SuppressWarnings("unchecked")
        T result = (T) holder[0];
        return result;
    }

    /**
     * Runs {@code supplier} within the already-bound context. Throws if no context is bound.
     *
     * @param supplier work to run within the current scope
     * @return the supplier's result
     */
    public static <T> T inScope(Supplier<T> supplier) {
        CaseContext ctx = CURRENT.get();
        if (ctx == null) {
            throw new IllegalStateException(
                    "No CaseContext is bound to the current thread; wrap the call in CaseContext.runIn(...)");
        }
        return runIn(ctx, supplier);
    }

    @Override
    public String toString() {
        return "CaseContext{caseId=" + caseId + ", systemId=" + systemId + "}";
    }
}