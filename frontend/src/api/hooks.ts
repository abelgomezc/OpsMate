import { useState, useEffect, useCallback } from 'react';
import {
  casesApi,
  approvalsApi,
  systemsApi,
  monitorApi,
  reportsApi,
  simApi,
} from './client';
import type { Case, Approval, SystemSummary, SystemHealth, MonitorStatus, ScenarioInfo } from '../types';

export function useCases(filters?: { status?: string[]; systemId?: string; severity?: string }) {
  const [data, setData] = useState<Case[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await casesApi.list(filters);
      setData(res.data ?? []);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e : new Error(String(e)));
    } finally {
      setLoading(false);
    }
  }, [filters]);

  useEffect(() => {
    load();
  }, [load]);

  const refetch = useCallback(() => {
    load();
  }, [load]);

  return { data, loading, error, refetch };
}

export function useCase(id: string | null) {
  const [data, setData] = useState<Case | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    if (!id) {
      setData(null);
      setLoading(false);
      return;
    }
    let cancelled = false;
    casesApi.get(id)
      .then((res) => { if (!cancelled) setData(res.data); })
      .catch((e) => { if (!cancelled) setError(e instanceof Error ? e : new Error(String(e))); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [id]);

  return { data, loading, error };
}

export function usePendingApprovals() {
  const [data, setData] = useState<Approval[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    let cancelled = false;
    approvalsApi.pending()
      .then((res) => { if (!cancelled) setData(res.data ?? []); })
      .catch((e) => { if (!cancelled) setError(e instanceof Error ? e : new Error(String(e))); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const refetch = useCallback(() => {
    setLoading(true);
    approvalsApi.pending()
      .then((res) => { setData(res.data ?? []); setError(null); })
      .catch((e) => { setError(e instanceof Error ? e : new Error(String(e))); })
      .finally(() => { setLoading(false); });
  }, []);

  return { data, loading, error, refetch };
}

export function useSystems() {
  const [data, setData] = useState<SystemSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    let cancelled = false;
    systemsApi.list()
      .then((res) => { if (!cancelled) setData(res.data ?? []); })
      .catch((e) => { if (!cancelled) setError(e instanceof Error ? e : new Error(String(e))); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const refetch = useCallback(() => {
    setLoading(true);
    systemsApi.list()
      .then((res) => { setData(res.data ?? []); setError(null); })
      .catch((e) => { setError(e instanceof Error ? e : new Error(String(e))); })
      .finally(() => { setLoading(false); });
  }, []);

  return { data, loading, error, refetch };
}

export function useSystemHealth(id: string | null) {
  const [data, setData] = useState<SystemHealth | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    if (!id) { setData(null); setLoading(false); return; }
    let cancelled = false;
    systemsApi.health(id)
      .then((res) => { if (!cancelled) setData(res.data); })
      .catch((e) => { if (!cancelled) setError(e instanceof Error ? e : new Error(String(e))); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [id]);

  return { data, loading, error };
}

export function useMonitor() {
  const [data, setData] = useState<MonitorStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    let cancelled = false;
    monitorApi.status()
      .then((res) => { if (!cancelled) setData(res.data); })
      .catch((e) => { if (!cancelled) setError(e instanceof Error ? e : new Error(String(e))); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const start = useCallback(async () => {
    const res = await monitorApi.start();
    setData(res.data);
    return res.data;
  }, []);
  const stop = useCallback(async () => {
    const res = await monitorApi.stop();
    setData(res.data);
    return res.data;
  }, []);
  const setInterval = useCallback(async (seconds: number) => {
    const res = await monitorApi.interval(seconds);
    setData(res.data);
    return res.data;
  }, []);

  return { data, loading, error, start, stop, setInterval };
}

export function useScenarios() {
  const [data, setData] = useState<ScenarioInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    let cancelled = false;
    simApi.scenarios()
      .then((res) => { if (!cancelled) setData(res.data ?? []); })
      .catch((e) => { if (!cancelled) setError(e instanceof Error ? e : new Error(String(e))); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const inject = useCallback(async (scenarioId: string, body?: Parameters<typeof simApi.inject>[1]) => {
    const res = await simApi.inject(scenarioId, body);
    return res.data;
  }, []);
  const clear = useCallback(async () => {
    const res = await simApi.clear();
    return res.data;
  }, []);

  return { data, loading, error, inject, clear };
}

export function useExportCaseReport(caseId: string) {
  const [markdown, setMarkdown] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const exportReport = useCallback(async () => {
    setLoading(true);
    try {
      const res = await reportsApi.exportCase(caseId);
      setMarkdown(res.data);
      return res.data;
    } catch (e) {
      setError(e instanceof Error ? e : new Error(String(e)));
      return null;
    } finally {
      setLoading(false);
    }
  }, [caseId]);

  return { markdown, loading, error, exportReport };
}
