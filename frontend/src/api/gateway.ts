import { apiFetch } from "./auth";

export interface GatewayDiagnosticRecord {
  traceId: string;
  clientRequestId: string;
  userId: number;
  modelCode: string;
  providerCode: string;
  routeId?: string;
  routeName?: string;
  protocol: string;
  stream: boolean;
  status: "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELED";
  upstreamStatus?: number;
  errorCode?: string;
  durationMillis: number;
  startedAt: string;
  completedAt?: string;
}

export interface GatewayDiagnosticsSummary {
  total: number;
  succeeded: number;
  failed: number;
  running: number;
  averageDurationMillis: number;
  p95DurationMillis: number;
  window: string;
  generatedAt: string;
}

export interface GatewayDiagnostics {
  summary: GatewayDiagnosticsSummary;
  records: GatewayDiagnosticRecord[];
}

export function getGatewayDiagnostics(limit = 50): Promise<GatewayDiagnostics> {
  return apiFetch<GatewayDiagnostics>(`/api/admin/model-gateway/diagnostics?limit=${limit}`);
}
