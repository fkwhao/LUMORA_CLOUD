import { apiFetch } from "./auth";

export interface ProviderCredentialStatus {
  storageType: "ENCRYPTED_DATABASE" | "ENVIRONMENT_REFERENCE";
  managed: boolean;
  maskedValue: string;
  fingerprint?: string;
  revision: number;
  rotatedAt?: string;
}

export interface ModelProvider {
  id: number;
  code: string;
  name: string;
  protocolType: string;
  baseUrl: string;
  credential: ProviderCredentialStatus;
  status: "ACTIVE" | "DISABLED";
  revision: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProviderInput {
  code: string;
  name: string;
  protocolType: string;
  baseUrl: string;
  apiKey: string;
}

export interface ModelCapabilities {
  contextWindow: number;
  maxOutputTokens: number;
  reasoning: boolean;
  tools: boolean;
  vision: boolean;
  json: boolean;
}

export interface ModelRates {
  inputPerMillion: number;
  outputPerMillion: number;
  reasoningPerMillion: number;
  cacheReadPerMillion: number;
  cacheWritePerMillion: number;
}

export interface ModelQuotaRates extends ModelRates {
  minimumRequestQuota: number;
}

export interface ModelVersion {
  id: string;
  pricingVersion: string;
  versionNo: number;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  revision: number;
  providerId: number;
  displayName: string;
  description?: string;
  upstreamModel: string;
  protocolType: string;
  baseUrl: string;
  credentialReference: string;
  capabilities: ModelCapabilities;
  costCurrency: string;
  costRates: ModelRates;
  quotaRates: ModelQuotaRates;
  publishedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface AdminModel {
  modelId: number;
  code: string;
  status: "ACTIVE" | "DISABLED";
  revision: number;
  draft?: ModelVersion;
  published?: ModelVersion;
  createdAt: string;
  updatedAt: string;
}

export interface CatalogStatistics {
  totalProviders: number;
  activeProviders: number;
  totalModels: number;
  publicModels: number;
  draftModels: number;
  totalVersions: number;
  generatedAt: string;
}

export interface ModelVersionInput {
  displayName: string;
  description: string;
  upstreamModel: string;
  contextWindow: number;
  maxOutputTokens: number;
  supportsReasoning: boolean;
  supportsTools: boolean;
  supportsVision: boolean;
  supportsJson: boolean;
  costCurrency: string;
  inputCostPerMillion: number;
  outputCostPerMillion: number;
  reasoningCostPerMillion: number;
  cacheReadCostPerMillion: number;
  cacheWriteCostPerMillion: number;
  inputQuotaPerMillion: number;
  outputQuotaPerMillion: number;
  reasoningQuotaPerMillion: number;
  cacheReadQuotaPerMillion: number;
  cacheWriteQuotaPerMillion: number;
  minimumRequestQuota: number;
}

export interface CreateModelInput {
  code: string;
  providerId: number;
  version: ModelVersionInput;
}

export function listProviders(): Promise<ModelProvider[]> {
  return apiFetch<ModelProvider[]>("/api/admin/catalog/providers");
}

export function getCatalogStatistics(): Promise<CatalogStatistics> {
  return apiFetch<CatalogStatistics>("/api/admin/catalog/statistics");
}

export function createProvider(input: CreateProviderInput): Promise<ModelProvider> {
  return apiFetch<ModelProvider>("/api/admin/catalog/providers", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
}

export function rotateProviderCredential(provider: ModelProvider, apiKey: string): Promise<ModelProvider> {
  return apiFetch<ModelProvider>(`/api/admin/catalog/providers/${provider.id}/credential`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ expectedRevision: provider.revision, apiKey }),
  });
}

export function listModels(): Promise<AdminModel[]> {
  return apiFetch<AdminModel[]>("/api/admin/catalog/models");
}

export function listModelVersions(modelId: number): Promise<ModelVersion[]> {
  return apiFetch<ModelVersion[]>(`/api/admin/catalog/models/${modelId}/versions`);
}

export function createModel(input: CreateModelInput): Promise<AdminModel> {
  return apiFetch<AdminModel>("/api/admin/catalog/models", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
}

export function createModelDraft(modelId: number): Promise<AdminModel> {
  return apiFetch<AdminModel>(`/api/admin/catalog/models/${modelId}/drafts`, {
    method: "POST",
  });
}

export function updateModelDraft(
  model: AdminModel,
  providerId: number,
  version: ModelVersionInput,
): Promise<AdminModel> {
  if (!model.draft) throw new Error("模型草稿不存在");
  return apiFetch<AdminModel>(`/api/admin/catalog/models/${model.modelId}/draft`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ expectedRevision: model.draft.revision, providerId, version }),
  });
}

export function publishModelDraft(model: AdminModel): Promise<AdminModel> {
  if (!model.draft) throw new Error("模型草稿不存在");
  return apiFetch<AdminModel>(`/api/admin/catalog/models/${model.modelId}/draft/publish`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ expectedRevision: model.draft.revision }),
  });
}

export function updateModelStatus(
  model: AdminModel,
  status: AdminModel["status"],
): Promise<AdminModel> {
  return apiFetch<AdminModel>(`/api/admin/catalog/models/${model.modelId}/status`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ expectedRevision: model.revision, status }),
  });
}
