import { api } from './client';
import type { ApiEnvelope, AuthResponse, User } from '@/types/api';

export interface LoginPayload {
  email: string;
  password: string;
}

export interface RegisterPayload {
  email: string;
  password: string;
  fullName: string;
  tenantName: string;
  defaultCurrency?: string;
  defaultLocale?: string;
}

export async function login(payload: LoginPayload): Promise<AuthResponse> {
  const res = await api.post<ApiEnvelope<AuthResponse>>('/api/v1/auth/login', payload);
  return res.data.data;
}

export async function register(payload: RegisterPayload): Promise<AuthResponse> {
  const res = await api.post<ApiEnvelope<AuthResponse>>('/api/v1/auth/register', payload);
  return res.data.data;
}

export async function logout(): Promise<void> {
  await api.post('/api/v1/auth/logout');
}

export async function getCurrentUser(): Promise<User> {
  const res = await api.get<ApiEnvelope<User>>('/api/v1/auth/me');
  return res.data.data;
}

export function oauth2AuthorizeUrl(provider: 'google' | 'github'): string {
  const base = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
  return `${base}/api/v1/auth/oauth2/authorization/${provider}`;
}
