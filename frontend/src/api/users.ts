import axios from 'axios';
import { api } from './client';
import type { ApiEnvelope, Role } from '@/types/api';

export interface Member {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  provider: string;
  active: boolean;
  pendingInvite: boolean;
  lastLoginAt: string | null;
  invitedAt: string | null;
}

export interface InviteInfo {
  email: string;
  tenantName: string;
}

export async function listMembers(): Promise<Member[]> {
  const res = await api.get<ApiEnvelope<Member[]>>('/api/v1/users');
  return res.data.data;
}

export async function inviteMember(payload: { email: string; role: 'ADMIN' | 'MEMBER' }): Promise<Member> {
  const res = await api.post<ApiEnvelope<Member>>('/api/v1/users/invite', payload);
  return res.data.data;
}

export async function changeMemberRole(id: string, role: 'ADMIN' | 'MEMBER'): Promise<Member> {
  const res = await api.put<ApiEnvelope<Member>>(`/api/v1/users/${id}/role`, { role });
  return res.data.data;
}

export async function setMemberActive(id: string, active: boolean): Promise<Member> {
  const res = await api.put<ApiEnvelope<Member>>(`/api/v1/users/${id}/active`, { active });
  return res.data.data;
}

export async function transferOwnership(targetUserId: string): Promise<Member[]> {
  const res = await api.post<ApiEnvelope<Member[]>>('/api/v1/users/transfer-ownership', {
    targetUserId,
  });
  return res.data.data;
}

/** Anonymous — invite recipients are not logged in, so bypass the auth client. */
export async function getInviteInfo(token: string): Promise<InviteInfo> {
  const base = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
  const res = await axios.get<ApiEnvelope<InviteInfo>>(`${base}/api/v1/public/invites/${token}`);
  return res.data.data;
}

export async function acceptInvite(
  token: string,
  payload: { fullName: string; password: string },
): Promise<void> {
  const base = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
  await axios.post(`${base}/api/v1/public/invites/${token}/accept`, payload);
}
