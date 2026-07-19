import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getUnreadCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from '@/api/notifications';

const KEY = ['notifications'] as const;

/** Polls the unread count. Real-time push (WebSocket) supersedes this in G1. */
export function useUnreadCount(enabled: boolean) {
  return useQuery({
    queryKey: [...KEY, 'unread-count'],
    queryFn: getUnreadCount,
    enabled,
    refetchInterval: 30_000,
  });
}

export function useNotificationList(enabled: boolean) {
  return useQuery({
    queryKey: [...KEY, 'list'],
    queryFn: listNotifications,
    enabled,
  });
}

export function useMarkRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => markNotificationRead(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useMarkAllRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => markAllNotificationsRead(),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}
