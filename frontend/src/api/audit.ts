import { api } from './client';
import type { ApiEnvelope } from '@/types/api';

export type AuditAction = 'CREATE' | 'UPDATE' | 'DELETE' | 'STATUS_CHANGE' | 'SEND';

export interface AuditEntry {
  id: number;
  userId: string | null;
  entityType: string;
  entityId: string;
  action: AuditAction;
  changes: Record<string, unknown> | null;
  createdAt: string;
}

export async function listEntityAudit(type: string, id: string): Promise<AuditEntry[]> {
  const res = await api.get<ApiEnvelope<AuditEntry[]>>(`/api/v1/audit-logs/entity/${type}/${id}`);
  return res.data.data;
}
