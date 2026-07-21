import { api } from './client';
import type { ApiEnvelope, PageResponse } from '@/types/api';

export interface Product {
  id: string;
  name: string;
  description: string | null;
  unitPrice: string;
  taxRate: string;
  category: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ProductPayload {
  name: string;
  description?: string;
  unitPrice: string;
  taxRate?: string;
  category?: string;
  active?: boolean;
}

export interface ProductListParams {
  q?: string;
  activeOnly?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

export async function listProducts(params: ProductListParams): Promise<PageResponse<Product>> {
  const res = await api.get<ApiEnvelope<PageResponse<Product>>>('/api/v1/products', { params });
  return res.data.data;
}

export async function getProduct(id: string): Promise<Product> {
  const res = await api.get<ApiEnvelope<Product>>(`/api/v1/products/${id}`);
  return res.data.data;
}

export async function createProduct(payload: ProductPayload): Promise<Product> {
  const res = await api.post<ApiEnvelope<Product>>('/api/v1/products', payload);
  return res.data.data;
}

export async function updateProduct(id: string, payload: ProductPayload): Promise<Product> {
  const res = await api.put<ApiEnvelope<Product>>(`/api/v1/products/${id}`, payload);
  return res.data.data;
}

export async function deleteProduct(id: string): Promise<void> {
  await api.delete(`/api/v1/products/${id}`);
}
