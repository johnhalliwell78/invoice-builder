import { api } from './client';
import type { ApiEnvelope, PageResponse } from '@/types/api';

export type NotificationType =
  | 'INVOICE_SENT'
  | 'INVOICE_VIEWED'
  | 'INVOICE_PAID'
  | 'INVOICE_OVERDUE'
  | 'CUSTOMER_CREATED';

export interface AppNotification {
  id: string;
  type: NotificationType;
  title: string;
  message: string | null;
  referenceType: string | null;
  referenceId: string | null;
  read: boolean;
  createdAt: string;
}

export async function listNotifications(): Promise<PageResponse<AppNotification>> {
  const res = await api.get<ApiEnvelope<PageResponse<AppNotification>>>('/api/v1/notifications', {
    params: { size: 20, sort: 'createdAt,desc' },
  });
  return res.data.data;
}

export async function getUnreadCount(): Promise<number> {
  const res = await api.get<ApiEnvelope<{ count: number }>>('/api/v1/notifications/unread-count');
  return res.data.data.count;
}

export async function markNotificationRead(id: string): Promise<void> {
  await api.put(`/api/v1/notifications/${id}/read`);
}

export async function markAllNotificationsRead(): Promise<void> {
  await api.put('/api/v1/notifications/read-all');
}
