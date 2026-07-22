import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { Pause, Play, Trash2 } from 'lucide-react';

import { useDeleteRecurring, useRecurringList, useToggleRecurring } from '@/hooks/useRecurring';
import type { RecurringInvoice } from '@/api/recurring';
import { Button } from '@/components/ui/button';
import { PageHeader } from '@/components/PageHeader';
import { formatDate } from '@/lib/format';
import type { ProblemDetail } from '@/types/api';

export default function RecurringPage() {
  const { t, i18n } = useTranslation();
  const [page, setPage] = useState(0);

  const { data, isPending, error } = useRecurringList({ page, size: 20 });
  const toggle = useToggleRecurring();
  const del = useDeleteRecurring();

  async function run<T>(promise: Promise<T>, successKey: string) {
    try {
      await promise;
      toast.success(t(successKey));
    } catch (err) {
      const detail = isAxiosError<ProblemDetail>(err) ? err.response?.data?.detail : undefined;
      toast.error(detail ?? t('auth.errors.default'));
    }
  }

  function handleDelete(r: RecurringInvoice) {
    if (!window.confirm(t('recurring.deleteConfirm', { name: r.customerName ?? r.currency }))) return;
    void run(del.mutateAsync(r.id), 'recurring.deleted');
  }

  return (
    <div>
      <PageHeader title={t('recurring.title')} description={t('recurring.subtitle')} />

      {error ? (
        <div className="rounded-md border border-destructive/50 bg-destructive/5 p-4 text-sm text-destructive">
          {t('common.loadFailed')}
        </div>
      ) : (
        <div className="overflow-hidden rounded-lg border bg-card">
          <table className="w-full text-sm">
            <thead className="bg-muted/50 text-left text-xs uppercase tracking-wider text-muted-foreground">
              <tr>
                <th className="px-4 py-3 font-medium">{t('recurring.columns.customer')}</th>
                <th className="px-4 py-3 font-medium">{t('recurring.columns.frequency')}</th>
                <th className="px-4 py-3 font-medium">{t('recurring.columns.nextRun')}</th>
                <th className="px-4 py-3 font-medium">{t('recurring.columns.autoSend')}</th>
                <th className="px-4 py-3 font-medium">{t('recurring.columns.status')}</th>
                <th className="px-4 py-3 text-right font-medium">{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {isPending ? (
                <tr>
                  <td colSpan={6} className="px-4 py-12 text-center text-muted-foreground">
                    {t('common.loading')}
                  </td>
                </tr>
              ) : data && data.totalElements === 0 ? (
                <tr>
                  <td colSpan={6} className="px-4 py-12 text-center text-muted-foreground">
                    {t('recurring.empty')}
                  </td>
                </tr>
              ) : (
                data?.content.map((r) => (
                  <tr key={r.id} className="hover:bg-muted/30">
                    <td className="px-4 py-3 font-medium">{r.customerName ?? '—'}</td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {t(`recurring.frequency.${r.frequency}`)}
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {formatDate(r.nextRun, i18n.language)}
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {r.autoSend ? t('recurring.on') : t('recurring.off')}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={
                          r.active
                            ? 'rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-700'
                            : 'rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground'
                        }
                      >
                        {r.active ? t('recurring.status.active') : t('recurring.status.paused')}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="icon"
                          aria-label={r.active ? t('recurring.actions.pause') : t('recurring.actions.resume')}
                          disabled={toggle.isPending}
                          onClick={() =>
                            void run(
                              toggle.mutateAsync(r.id),
                              r.active ? 'recurring.paused' : 'recurring.resumed',
                            )
                          }
                        >
                          {r.active ? <Pause className="h-4 w-4" /> : <Play className="h-4 w-4" />}
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          aria-label={t('common.delete')}
                          onClick={() => handleDelete(r)}
                        >
                          <Trash2 className="h-4 w-4 text-destructive" />
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {data && data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm">
          <div className="text-muted-foreground">
            {t('common.pageOf', { current: data.page + 1, total: data.totalPages })}
          </div>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={data.first}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              {t('common.previous')}
            </Button>
            <Button variant="outline" size="sm" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
              {t('common.next')}
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
