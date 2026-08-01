import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { Trash2, Webhook } from 'lucide-react';

import {
  createWebhook,
  deleteWebhook,
  listWebhooks,
  type WebhookEndpoint,
  type WebhookEventType,
} from '@/api/webhooks';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import type { ProblemDetail } from '@/types/api';

const KEY = ['webhooks'] as const;
const EVENTS: WebhookEventType[] = [
  'INVOICE_SENT',
  'INVOICE_VIEWED',
  'INVOICE_PAID',
  'INVOICE_OVERDUE',
  'CUSTOMER_CREATED',
];

export function WebhooksCard() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [url, setUrl] = useState('');
  const [selected, setSelected] = useState<WebhookEventType[]>(['INVOICE_PAID']);
  const [freshSecret, setFreshSecret] = useState<string | null>(null);

  const endpoints = useQuery({ queryKey: KEY, queryFn: listWebhooks });

  const create = useMutation({
    mutationFn: () => createWebhook({ url, eventTypes: selected }),
    onSuccess: (created) => {
      setFreshSecret(created.secret);
      setUrl('');
      void qc.invalidateQueries({ queryKey: KEY });
    },
    onError: errorToast,
  });

  const remove = useMutation({
    mutationFn: (id: string) => deleteWebhook(id),
    onSuccess: () => {
      toast.success(t('webhooks.deleted'));
      void qc.invalidateQueries({ queryKey: KEY });
    },
    onError: errorToast,
  });

  function errorToast(err: unknown) {
    const detail = isAxiosError<ProblemDetail>(err) ? err.response?.data?.detail : undefined;
    toast.error(detail ?? t('auth.errors.default'));
  }

  function toggleEvent(event: WebhookEventType) {
    setSelected((current) =>
      current.includes(event) ? current.filter((e) => e !== event) : [...current, event],
    );
  }

  function handleDelete(endpoint: WebhookEndpoint) {
    if (!window.confirm(t('webhooks.deleteConfirm', { url: endpoint.url }))) return;
    remove.mutate(endpoint.id);
  }

  return (
    <Card className="mt-6 max-w-4xl">
      <CardHeader>
        <CardTitle>{t('webhooks.title')}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-muted-foreground">{t('webhooks.description')}</p>

        {freshSecret && (
          <div className="rounded-md border border-amber-500/50 bg-amber-50 p-3 dark:bg-amber-950/30">
            <p className="mb-2 text-sm font-medium">{t('webhooks.secretHint')}</p>
            <code className="block break-all rounded bg-background px-2 py-1 text-xs">
              {freshSecret}
            </code>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="mt-2"
              onClick={() => setFreshSecret(null)}
            >
              {t('common.close')}
            </Button>
          </div>
        )}

        <div className="space-y-2">
          <div className="space-y-1.5">
            <Label>{t('webhooks.fields.url')}</Label>
            <Input
              value={url}
              placeholder="https://example.com/hooks/invoice-builder"
              onChange={(e) => setUrl(e.target.value)}
            />
          </div>
          <div className="space-y-1.5">
            <Label>{t('webhooks.fields.events')}</Label>
            <div className="flex flex-wrap gap-3">
              {EVENTS.map((event) => (
                <label key={event} className="flex items-center gap-1.5 text-sm">
                  <input
                    type="checkbox"
                    className="h-4 w-4 rounded border"
                    checked={selected.includes(event)}
                    onChange={() => toggleEvent(event)}
                  />
                  {t(`notifications.types.${event}`)}
                </label>
              ))}
            </div>
          </div>
          <Button
            type="button"
            disabled={!url.trim() || selected.length === 0 || create.isPending}
            onClick={() => create.mutate()}
          >
            <Webhook className="mr-2 h-4 w-4" />
            {t('webhooks.create')}
          </Button>
        </div>

        <table className="w-full text-sm">
          <thead className="text-left text-xs uppercase text-muted-foreground">
            <tr>
              <th className="pb-2 font-medium">{t('webhooks.columns.url')}</th>
              <th className="pb-2 font-medium">{t('webhooks.columns.events')}</th>
              <th className="pb-2 text-right font-medium">{t('common.actions')}</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {!endpoints.data?.length ? (
              <tr>
                <td colSpan={3} className="py-6 text-center text-muted-foreground">
                  {t('webhooks.empty')}
                </td>
              </tr>
            ) : (
              endpoints.data.map((endpoint) => (
                <tr key={endpoint.id}>
                  <td className="max-w-xs truncate py-2 font-mono text-xs">{endpoint.url}</td>
                  <td className="py-2 text-muted-foreground">{endpoint.eventTypes.length}</td>
                  <td className="py-2 text-right">
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label={t('common.delete')}
                      onClick={() => handleDelete(endpoint)}
                    >
                      <Trash2 className="h-4 w-4 text-destructive" />
                    </Button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </CardContent>
    </Card>
  );
}
