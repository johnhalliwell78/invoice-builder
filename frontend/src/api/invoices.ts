import axios from 'axios';
import { api } from './client';
import type { ApiEnvelope, Invoice, InvoiceListItem, InvoiceStatus, PageResponse } from '@/types/api';

export interface SendInvoicePayload {
  recipientEmail?: string;
  cc?: string[];
  bcc?: string[];
  subject?: string;
  message?: string;
  skipEmail?: boolean;
}

export interface EmailPreview {
  recipientEmail: string | null;
  subject: string;
  body: string;
}

export interface PublicInvoiceView {
  issuer: { name: string; address: Address | null; taxId: string | null };
  recipient: { name: string; address: Address | null };
  invoiceNumber: string;
  status: InvoiceStatus;
  currency: string;
  subtotal: string;
  taxTotal: string;
  discountAmount: string;
  total: string;
  amountPaid: string;
  issueDate: string;
  dueDate: string;
  notes: string | null;
  terms: string | null;
  lineItems: Array<{
    id: string;
    description: string;
    quantity: string;
    unitPrice: string;
    taxRate: string;
    discountPercent: string;
    amount: string;
    sortOrder: number;
  }>;
}

interface Address {
  street?: string;
  city?: string;
  state?: string;
  zip?: string;
  country?: string;
}

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

export async function sendInvoice(id: string, payload?: SendInvoicePayload): Promise<Invoice> {
  const res = await api.post<ApiEnvelope<Invoice>>(`/api/v1/invoices/${id}/send`, payload ?? {});
  return res.data.data;
}

export async function getEmailPreview(id: string): Promise<EmailPreview> {
  const res = await api.get<ApiEnvelope<EmailPreview>>(`/api/v1/invoices/${id}/email-preview`);
  return res.data.data;
}

export async function resendInvoice(id: string, payload?: SendInvoicePayload): Promise<Invoice> {
  const res = await api.post<ApiEnvelope<Invoice>>(`/api/v1/invoices/${id}/resend`, payload ?? {});
  return res.data.data;
}

/** Fetches the rendered PDF as a Blob via the authenticated axios client. */
export async function fetchInvoicePdf(id: string, mode: 'preview' | 'pdf' = 'preview'): Promise<Blob> {
  const res = await api.get(`/api/v1/invoices/${id}/${mode}`, { responseType: 'blob' });
  return res.data as Blob;
}

/**
 * Anonymous fetch — bypasses the authenticated `api` instance so we don't
 * accidentally attach a stale Bearer token (recipients are not logged in).
 */
export async function getPublicInvoice(token: string): Promise<PublicInvoiceView> {
  const base = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
  const res = await axios.get<ApiEnvelope<PublicInvoiceView>>(
    `${base}/api/v1/public/invoices/${token}`,
  );
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
