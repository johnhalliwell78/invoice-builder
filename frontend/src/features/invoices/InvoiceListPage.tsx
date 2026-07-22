import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Plus } from 'lucide-react';

import { useInvoiceList } from '@/hooks/useInvoices';
import { CustomerCombobox } from '@/components/CustomerCombobox';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { PageHeader } from '@/components/PageHeader';
import { StatusBadge } from './StatusBadge';
import { formatCurrency, formatDate } from '@/lib/format';
import type { DocType, InvoiceStatus } from '@/types/api';

const INVOICE_STATUSES: InvoiceStatus[] = ['DRAFT', 'SENT', 'VIEWED', 'PAID', 'OVERDUE', 'CANCELLED'];
const ESTIMATE_STATUSES: InvoiceStatus[] = ['DRAFT', 'SENT', 'VIEWED', 'APPROVED', 'DECLINED', 'CANCELLED'];

export default function InvoiceListPage({ docType = 'INVOICE' }: { docType?: DocType }) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const isEstimate = docType === 'ESTIMATE';
  const base = isEstimate ? '/estimates' : '/invoices';
  const statusOptions = isEstimate ? ESTIMATE_STATUSES : INVOICE_STATUSES;

  const [status, setStatus] = useState<InvoiceStatus | ''>('');
  const [customerFilter, setCustomerFilter] = useState<{ id: string; name: string } | null>(null);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);

  const { data, isPending, error } = useInvoiceList({
    docType,
    status: status || undefined,
    customerId: customerFilter?.id ?? undefined,
    from: from || undefined,
    to: to || undefined,
    page,
    size: 20,
    sort: 'createdAt,desc',
  });

  return (
    <div>
      <PageHeader
        title={isEstimate ? t('estimates.title') : t('invoices.title')}
        description={isEstimate ? t('estimates.subtitle') : t('invoices.subtitle')}
        actions={
          <Button onClick={() => navigate(`${base}/new`)}>
            <Plus className="mr-2 h-4 w-4" />
            {isEstimate ? t('estimates.create') : t('invoices.create')}
          </Button>
        }
      />

      <div className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <div className="space-y-1.5">
          <Label>{t('invoices.filters.status')}</Label>
          <select
            className="h-10 w-full rounded-md border bg-background px-3 text-sm"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value as InvoiceStatus | '');
              setPage(0);
            }}
          >
            <option value="">{t('invoices.filters.allStatuses')}</option>
            {statusOptions.map((s) => (
              <option key={s} value={s}>
                {t(`invoices.status.${s}`)}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-1.5">
          <Label>{t('invoices.filters.customer')}</Label>
          <CustomerCombobox
            selectedId={customerFilter?.id ?? ''}
            selectedName={customerFilter?.name}
            clearable
            placeholder={t('invoices.filters.allCustomers')}
            onSelect={(c) => {
              setCustomerFilter(c ? { id: c.id, name: c.name } : null);
              setPage(0);
            }}
          />
        </div>
        <div className="space-y-1.5">
          <Label>{t('invoices.filters.from')}</Label>
          <Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <div className="space-y-1.5">
          <Label>{t('invoices.filters.to')}</Label>
          <Input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </div>
      </div>

      {error ? (
        <div className="rounded-md border border-destructive/50 bg-destructive/5 p-4 text-sm text-destructive">
          {t('common.loadFailed')}
        </div>
      ) : (
        <div className="overflow-hidden rounded-lg border bg-card">
          <table className="w-full text-sm">
            <thead className="bg-muted/50 text-left text-xs uppercase tracking-wider text-muted-foreground">
              <tr>
                <th className="px-4 py-3 font-medium">{t('invoices.columns.number')}</th>
                <th className="px-4 py-3 font-medium">{t('invoices.columns.customer')}</th>
                <th className="px-4 py-3 font-medium">{t('invoices.columns.status')}</th>
                <th className="px-4 py-3 font-medium">{t('invoices.columns.issued')}</th>
                <th className="px-4 py-3 font-medium">{t('invoices.columns.due')}</th>
                <th className="px-4 py-3 text-right font-medium">{t('invoices.columns.total')}</th>
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
                    {isEstimate ? t('estimates.empty') : t('invoices.empty')}
                  </td>
                </tr>
              ) : (
                data?.content.map((inv) => {
                  return (
                    <tr key={inv.id} className="hover:bg-muted/30">
                      <td className="px-4 py-3 font-medium">
                        <Link to={`${base}/${inv.id}`} className="hover:underline">
                          {inv.invoiceNumber}
                        </Link>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">{inv.customerName ?? '—'}</td>
                      <td className="px-4 py-3">
                        <StatusBadge status={inv.status} />
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {formatDate(inv.issueDate, i18n.language)}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {formatDate(inv.dueDate, i18n.language)}
                      </td>
                      <td className="px-4 py-3 text-right font-medium tabular-nums">
                        {formatCurrency(inv.total, inv.currency, i18n.language)}
                        {Number(inv.amountPaid) > 0 && inv.status !== 'PAID' && (
                          <div className="text-xs font-normal text-muted-foreground">
                            {t('payments.balanceShort', {
                              amount: formatCurrency(
                                (Number(inv.total) - Number(inv.amountPaid)).toFixed(2),
                                inv.currency,
                                i18n.language,
                              ),
                            })}
                          </div>
                        )}
                      </td>
                    </tr>
                  );
                })
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
            <Button
              variant="outline"
              size="sm"
              disabled={data.last}
              onClick={() => setPage((p) => p + 1)}
            >
              {t('common.next')}
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
