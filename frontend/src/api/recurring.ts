import { api } from './client';
import type { ApiEnvelope, PageResponse } from '@/types/api';

export type Frequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';

export interface RecurringInvoice {
  id: string;
  customerId: string;
  customerName: string | null;
  frequency: Frequency;
  nextRun: string;
  active: boolean;
  autoSend: boolean;
  currency: string;
  template: string;
  dueDays: number;
  discountAmount: string;
  lineItems: Array<{
    description: string;
    quantity: string;
    unitPrice: string;
    taxRate: string;
    discountPercent: string;
  }>;
  createdAt: string;
}

export interface MakeRecurringPayload {
  frequency: Frequency;
  autoSend: boolean;
  firstRun?: string;
}

export async function listRecurring(params: { page?: number; size?: number }): Promise<PageResponse<RecurringInvoice>> {
  const res = await api.get<ApiEnvelope<PageResponse<RecurringInvoice>>>('/api/v1/recurring', { params });
  return res.data.data;
}

export async function toggleRecurring(id: string): Promise<RecurringInvoice> {
  const res = await api.post<ApiEnvelope<RecurringInvoice>>(`/api/v1/recurring/${id}/toggle`);
  return res.data.data;
}

export async function deleteRecurring(id: string): Promise<void> {
  await api.delete(`/api/v1/recurring/${id}`);
}

export async function makeRecurring(invoiceId: string, payload: MakeRecurringPayload): Promise<RecurringInvoice> {
  const res = await api.post<ApiEnvelope<RecurringInvoice>>(
    `/api/v1/invoices/${invoiceId}/make-recurring`,
    payload,
  );
  return res.data.data;
}
