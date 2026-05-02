import { api } from './client';
import type { ApiEnvelope, Customer, PageResponse } from '@/types/api';

export interface CustomerPayload {
  name: string;
  email?: string;
  phone?: string;
  company?: string;
  address?: {
    street?: string;
    city?: string;
    state?: string;
    zip?: string;
    country?: string;
  };
  taxId?: string;
  notes?: string;
}

export interface CustomerListParams {
  q?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export async function listCustomers(params: CustomerListParams): Promise<PageResponse<Customer>> {
  const res = await api.get<ApiEnvelope<PageResponse<Customer>>>('/api/v1/customers', { params });
  return res.data.data;
}

export async function getCustomer(id: string): Promise<Customer> {
  const res = await api.get<ApiEnvelope<Customer>>(`/api/v1/customers/${id}`);
  return res.data.data;
}

export async function createCustomer(payload: CustomerPayload): Promise<Customer> {
  const res = await api.post<ApiEnvelope<Customer>>('/api/v1/customers', payload);
  return res.data.data;
}

export async function updateCustomer(id: string, payload: CustomerPayload): Promise<Customer> {
  const res = await api.put<ApiEnvelope<Customer>>(`/api/v1/customers/${id}`, payload);
  return res.data.data;
}

export async function deleteCustomer(id: string): Promise<void> {
  await api.delete(`/api/v1/customers/${id}`);
}
