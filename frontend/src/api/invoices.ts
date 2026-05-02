import { api } from './client';
import type { ApiEnvelope, Invoice, InvoiceListItem, InvoiceStatus, PageResponse } from '@/types/api';

export interface InvoicePayload {
  customerId: string;
  currency?: string;
  issueDate: string;
  dueDate: string;
  discountAmount?: string;
  notes?: string;
  terms?: string;
  template?: string;
  lineItems: Array<{
    description: string;
    quantity: string;
    unitPrice: string;
    taxRate?: string;
    discountPercent?: string;
  }>;
}

export interface InvoiceListParams {
  status?: InvoiceStatus;
  customerId?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export async function listInvoices(params: InvoiceListParams): Promise<PageResponse<InvoiceListItem>> {
  const res = await api.get<ApiEnvelope<PageResponse<InvoiceListItem>>>('/api/v1/invoices', { params });
  return res.data.data;
}

export async function getInvoice(id: string): Promise<Invoice> {
  const res = await api.get<ApiEnvelope<Invoice>>(`/api/v1/invoices/${id}`);
  return res.data.data;
}

export async function createInvoice(payload: InvoicePayload): Promise<Invoice> {
  const res = await api.post<ApiEnvelope<Invoice>>('/api/v1/invoices', payload);
  return res.data.data;
}

export async function updateInvoice(id: string, payload: InvoicePayload): Promise<Invoice> {
  const res = await api.put<ApiEnvelope<Invoice>>(`/api/v1/invoices/${id}`, payload);
  return res.data.data;
}

export async function deleteInvoice(id: string): Promise<void> {
  await api.delete(`/api/v1/invoices/${id}`);
}

export async function sendInvoice(id: string): Promise<Invoice> {
  const res = await api.post<ApiEnvelope<Invoice>>(`/api/v1/invoices/${id}/send`);
  return res.data.data;
}

export async function markPaid(id: string): Promise<Invoice> {
  const res = await api.post<ApiEnvelope<Invoice>>(`/api/v1/invoices/${id}/mark-paid`);
  return res.data.data;
}

export async function cancelInvoice(id: string): Promise<Invoice> {
  const res = await api.post<ApiEnvelope<Invoice>>(`/api/v1/invoices/${id}/cancel`);
  return res.data.data;
}
