import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

import { useDashboard } from '@/hooks/useDashboard';
import { useTenant } from '@/hooks/useTenant';
import type { MonthAmount } from '@/api/dashboard';
import type { InvoiceStatus } from '@/types/api';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader } from '@/components/PageHeader';
import { StatusBadge } from '@/features/invoices/StatusBadge';
import { BarChart, type BarDatum } from './BarChart';
import { formatCurrency, formatDate } from '@/lib/format';

const STATUS_ORDER: InvoiceStatus[] = ['DRAFT', 'SENT', 'VIEWED', 'PAID', 'OVERDUE', 'CANCELLED'];

export default function DashboardPage() {
  const { t, i18n } = useTranslation();
  const { data, isPending, error } = useDashboard();
  const tenant = useTenant();
  const locale = i18n.language;

  if (isPending) {
    return <div className="text-muted-foreground">{t('common.loading')}</div>;
  }
  if (error || !data) {
    return <div className="text-destructive">{t('common.loadFailed')}</div>;
  }

  const defaultCurrency = tenant.data?.defaultCurrency ?? data.tiles[0]?.currency ?? 'USD';
  const primaryTile =
    data.tiles.find((tile) => tile.currency === defaultCurrency) ?? data.tiles[0];
  const otherTiles = data.tiles.filter((tile) => tile.currency !== primaryTile?.currency);

  // Revenue chart uses the default (or first) currency series.
  const revenueSeries = seriesForCurrency(data.revenueByMonth, primaryTile?.currency ?? defaultCurrency);
  const revenueBars: BarDatum[] = revenueSeries.map((entry) => ({
    label: shortMonth(entry.month, locale),
    value: Number(entry.total),
    display: formatCurrency(entry.total, entry.currency, locale),
  }));
  const customerBars: BarDatum[] = data.customersByMonth.map((entry) => ({
    label: shortMonth(entry.month, locale),
    value: entry.count,
    display: String(entry.count),
  }));

  return (
    <div className="space-y-6">
      <PageHeader title={t('dashboard.title')} description={t('dashboard.subtitle')} />

      <div className="grid gap-4 sm:grid-cols-3">
        <StatTile
          label={t('dashboard.tiles.outstanding')}
          value={formatCurrency(primaryTile?.outstanding ?? '0', primaryTile?.currency ?? defaultCurrency, locale)}
        />
        <StatTile
          label={t('dashboard.tiles.overdue')}
          value={formatCurrency(primaryTile?.overdue ?? '0', primaryTile?.currency ?? defaultCurrency, locale)}
          tone="warning"
        />
        <StatTile
          label={t('dashboard.tiles.paidThisMonth')}
          value={formatCurrency(primaryTile?.paidThisMonth ?? '0', primaryTile?.currency ?? defaultCurrency, locale)}
          tone="positive"
        />
      </div>

      {otherTiles.length > 0 && (
        <div className="flex flex-wrap gap-x-6 gap-y-1 text-xs text-muted-foreground">
          {otherTiles.map((tile) => (
            <span key={tile.currency}>
              {tile.currency}: {t('dashboard.tiles.outstanding').toLowerCase()}{' '}
              {formatCurrency(tile.outstanding, tile.currency, locale)}
            </span>
          ))}
        </div>
      )}

      <div className="flex flex-wrap gap-2">
        {STATUS_ORDER.map((status) => (
          <div
            key={status}
            className="flex items-center gap-1.5 rounded-md border bg-card px-3 py-1.5 text-sm"
          >
            <StatusBadge status={status} />
            <span className="font-medium tabular-nums">{data.statusCounts[status] ?? 0}</span>
          </div>
        ))}
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>{t('dashboard.revenue.title')}</CardTitle>
          </CardHeader>
          <CardContent>
            <BarChart data={revenueBars} />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{t('dashboard.customers.title')}</CardTitle>
          </CardHeader>
          <CardContent>
            <BarChart data={customerBars} />
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t('dashboard.recent.title')}</CardTitle>
        </CardHeader>
        <CardContent>
          {data.recentInvoices.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t('dashboard.recent.empty')}</p>
          ) : (
            <ul className="divide-y">
              {data.recentInvoices.map((inv) => (
                <li key={inv.id} className="flex items-center justify-between gap-3 py-2 text-sm">
                  <div className="min-w-0">
                    <Link to={`/invoices/${inv.id}`} className="font-medium hover:underline">
                      {inv.invoiceNumber}
                    </Link>
                    <span className="ml-2 text-muted-foreground">{inv.customerName}</span>
                  </div>
                  <div className="flex shrink-0 items-center gap-3">
                    <StatusBadge status={inv.status} />
                    <span className="tabular-nums">
                      {formatCurrency(inv.total, inv.currency, locale)}
                    </span>
                    <span className="hidden text-muted-foreground sm:inline">
                      {formatDate(inv.updatedAt, locale)}
                    </span>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function StatTile({
  label,
  value,
  tone = 'neutral',
}: {
  label: string;
  value: string;
  tone?: 'neutral' | 'positive' | 'warning';
}) {
  const toneClass =
    tone === 'positive'
      ? 'text-emerald-600 dark:text-emerald-400'
      : tone === 'warning'
        ? 'text-amber-600 dark:text-amber-400'
        : 'text-foreground';
  return (
    <Card>
      <CardContent className="pt-6">
        <div className="text-sm text-muted-foreground">{label}</div>
        <div className={`mt-1 text-2xl font-semibold tabular-nums ${toneClass}`}>{value}</div>
      </CardContent>
    </Card>
  );
}

function seriesForCurrency(series: MonthAmount[], currency: string): MonthAmount[] {
  const filtered = series.filter((entry) => entry.currency === currency);
  return filtered.length > 0 ? filtered : series;
}

function shortMonth(month: string, locale: string): string {
  // month is "YYYY-MM"
  const [year, m] = month.split('-');
  const date = new Date(Number(year), Number(m) - 1, 1);
  return new Intl.DateTimeFormat(locale, { month: 'short' }).format(date);
}
