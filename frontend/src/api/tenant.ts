import { api } from './client';
import type { Address, ApiEnvelope } from '@/types/api';

export interface Tenant {
  id: string;
  name: string;
  slug: string;
  defaultCurrency: string;
  defaultLocale: string;
  logoPath: string | null;
  address: Address | null;
  taxId: string | null;
  invoicePrefix: string;
  defaultTemplate: string;
  footerText: string | null;
  paymentInfo: string | null;
  brandingColor: string | null;
  nextInvoiceNumber: number;
  createdAt: string;
}

export interface TenantUpdatePayload {
  name: string;
  defaultCurrency?: string;
  defaultLocale?: string;
  taxId?: string;
  invoicePrefix?: string;
  defaultTemplate?: string;
  address?: Address;
  footerText?: string;
  paymentInfo?: string;
  brandingColor?: string;
}

export async function getTenant(): Promise<Tenant> {
  const res = await api.get<ApiEnvelope<Tenant>>('/api/v1/tenant');
  return res.data.data;
}

export async function updateTenant(payload: TenantUpdatePayload): Promise<Tenant> {
  const res = await api.put<ApiEnvelope<Tenant>>('/api/v1/tenant', payload);
  return res.data.data;
}

export async function uploadTenantLogo(file: File): Promise<Tenant> {
  const form = new FormData();
  form.append('file', file);
  const res = await api.post<ApiEnvelope<Tenant>>('/api/v1/tenant/logo', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return res.data.data;
}

export async function fetchTenantLogo(): Promise<Blob> {
  const res = await api.get('/api/v1/tenant/logo', { responseType: 'blob' });
  return res.data as Blob;
}

export async function deleteTenantLogo(): Promise<Tenant> {
  const res = await api.delete<ApiEnvelope<Tenant>>('/api/v1/tenant/logo');
  return res.data.data;
}
