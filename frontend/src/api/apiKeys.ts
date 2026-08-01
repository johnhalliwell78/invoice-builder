import { api } from './client';
import type { ApiEnvelope, Role } from '@/types/api';

export interface ApiKey {
  id: string;
  name: string;
  keyPrefix: string;
  role: Role;
  lastUsedAt: string | null;
  revokedAt: string | null;
  createdAt: string;
}

export interface ApiKeyPayload {
  name: string;
  role: Role;
}

/** The `secret` is returned once, at creation, and never again. */
export interface ApiKeyCreated {
  key: ApiKey;
  secret: string;
}

export async function listApiKeys(): Promise<ApiKey[]> {
  const res = await api.get<ApiEnvelope<ApiKey[]>>('/api/v1/api-keys');
  return res.data.data;
}

export async function createApiKey(payload: ApiKeyPayload): Promise<ApiKeyCreated> {
  const res = await api.post<ApiEnvelope<ApiKeyCreated>>('/api/v1/api-keys', payload);
  return res.data.data;
}

export async function revokeApiKey(id: string): Promise<void> {
  await api.delete(`/api/v1/api-keys/${id}`);
}
