import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Bell } from 'lucide-react';

import {
  useMarkAllRead,
  useMarkRead,
  useNotificationList,
  useUnreadCount,
} from '@/hooks/useNotifications';
import type { AppNotification } from '@/api/notifications';
import { formatDate } from '@/lib/format';
import { cn } from '@/lib/utils';

export function NotificationBell() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const unread = useUnreadCount(true);
  const list = useNotificationList(open);
  const markRead = useMarkRead();
  const markAllRead = useMarkAllRead();
  const count = unread.data ?? 0;

  useEffect(() => {
    if (!open) return;
    const onClick = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, [open]);

  function onSelect(n: AppNotification) {
    if (!n.read) markRead.mutate(n.id);
    if (n.referenceType === 'Invoice' && n.referenceId) {
      setOpen(false);
      navigate(`/invoices/${n.referenceId}`);
    }
  }

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        aria-label={t('notifications.title')}
        className="relative rounded-md p-2 hover:bg-accent"
        onClick={() => setOpen((v) => !v)}
      >
        <Bell className="h-5 w-5" />
        {count > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[10px] font-medium text-destructive-foreground">
            {count > 9 ? '9+' : count}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-50 mt-2 w-80 overflow-hidden rounded-lg border bg-card shadow-lg">
          <div className="flex items-center justify-between border-b px-3 py-2">
            <span className="text-sm font-medium">{t('notifications.title')}</span>
            {count > 0 && (
              <button
                type="button"
                className="text-xs text-primary hover:underline"
                onClick={() => markAllRead.mutate()}
              >
                {t('notifications.markAllRead')}
              </button>
            )}
          </div>
          <div className="max-h-96 overflow-auto">
            {list.isPending ? (
              <p className="px-3 py-6 text-center text-sm text-muted-foreground">
                {t('common.loading')}
              </p>
            ) : !list.data || list.data.content.length === 0 ? (
              <p className="px-3 py-6 text-center text-sm text-muted-foreground">
                {t('notifications.empty')}
              </p>
            ) : (
              list.data.content.map((n) => (
                <button
                  key={n.id}
                  type="button"
                  onClick={() => onSelect(n)}
                  className={cn(
                    'flex w-full flex-col items-start gap-0.5 border-b px-3 py-2 text-left text-sm last:border-b-0 hover:bg-accent',
                    !n.read && 'bg-primary/5',
                  )}
                >
                  <span className={cn(!n.read && 'font-medium')}>
                    {t(`notifications.types.${n.type}`, { subject: n.message ?? '' })}
                  </span>
                  <span className="text-xs text-muted-foreground">
                    {formatDate(n.createdAt, i18n.language)}
                  </span>
                </button>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}
