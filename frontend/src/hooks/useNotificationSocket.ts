import { useEffect } from 'react';
import { Client, type IStompSocket } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';

import { useAuthStore } from '@/store/authStore';
import type { AppNotification } from '@/api/notifications';

const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';

/**
 * Opens a STOMP-over-SockJS connection while the user is authenticated and
 * subscribes to their own notification queue. Each pushed notification
 * refreshes the bell's queries and raises a toast. The connection auto-reconnects
 * and tears down on logout. This supersedes the 30s polling in useUnreadCount
 * (which stays as a fallback for when the socket is down).
 */
export function useNotificationSocket() {
  const token = useAuthStore((s) => s.accessToken);
  const queryClient = useQueryClient();
  const { t } = useTranslation();

  useEffect(() => {
    if (!token) return;

    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_URL}/ws`) as IStompSocket,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe('/user/queue/notifications', (message) => {
          const notification = JSON.parse(message.body) as AppNotification;
          void queryClient.invalidateQueries({ queryKey: ['notifications'] });
          toast(t(`notifications.types.${notification.type}`, {
            subject: notification.message ?? '',
          }));
        });
      },
    });

    client.activate();
    return () => {
      void client.deactivate();
    };
  }, [token, queryClient, t]);
}
