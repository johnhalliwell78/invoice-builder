import { api } from './client';
import type { ApiEnvelope } from '@/types/api';

export type WebhookEventType =
  | 'INVOICE_SENT'
  | 'INVOICE_VIEWED'
  | 'INVOICE_PAID'
  | 'INVOICE_OVERDUE'
  | 'CUSTOMER_CREATED';

export interface WebhookEndpoint {
  id: string;
  url: string;
  eventTypes: WebhookEventType[];
  active: boolean;
  createdAt: string;
}

export interface WebhookEndpointPayload {
  url: string;
  eventTypes: WebhookEventType[];
}

export interface WebhookEndpointCreated {
  endpoint: WebhookEndpoint;
  secret: string;
}

export async function listWebhooks(): Promise<WebhookEndpoint[]> {
  const res = await api.get<ApiEnvelope<WebhookEndpoint[]>>('/api/v1/webhooks');
  return res.data.data;
}

export async function createWebhook(payload: WebhookEndpointPayload): Promise<WebhookEndpointCreated> {
  const res = await api.post<ApiEnvelope<WebhookEndpointCreated>>('/api/v1/webhooks', payload);
  return res.data.data;
}

export async function deleteWebhook(id: string): Promise<void> {
  await api.delete(`/api/v1/webhooks/${id}`);
}
