import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Download } from 'lucide-react';

import { downloadCsv, getTaxSummary } from '@/api/reports';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader } from '@/components/PageHeader';
import { formatCurrency } from '@/lib/format';

/** First day of the current year through today — a sane default period. */
function defaultRange() {
  const now = new Date();
  const iso = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  return { from: iso(new Date(now.getFullYear(), 0, 1)), to: iso(now) };
}

export default function ReportsPage() {
  const { t, i18n } = useTranslation();
  const initial = defaultRange();
  const [from, setFrom] = useState(initial.from);
  const [to, setTo] = useState(initial.to);
  const [downloading, setDownloading] = useState(false);

  const summary = useQuery({
    queryKey: ['reports', 'tax-summary', from, to],
    queryFn: () => getTaxSummary(from, to),
    enabled: !!from && !!to,
  });

  async function download(path: string, filename: string) {
    setDownloading(true);
    try {
      await downloadCsv(path, { from, to }, filename);
    } catch {
      toast.error(t('common.loadFailed'));
    } finally {
      setDownloading(false);
    }
  }

  return (
    <div>
      <PageHeader title={t('reports.title')} description={t('reports.subtitle')} />

      <div className="mb-6 flex flex-wrap items-end gap-3">
        <div className="space-y-1.5">
          <Label>{t('reports.from')}</Label>
          <Input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <div className="space-y-1.5">
          <Label>{t('reports.to')}</Label>
          <Input type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} />
        </div>
        <Button
          variant="outline"
          disabled={downloading}
          onClick={() => void download('/api/v1/exports/invoices.csv', 'invoices.csv')}
        >
          <Download className="mr-2 h-4 w-4" />
          {t('reports.exportInvoices')}
        </Button>
        <Button
          variant="outline"
          disabled={downloading}
          onClick={() => void download('/api/v1/exports/payments.csv', 'payments.csv')}
        >
          <Download className="mr-2 h-4 w-4" />
          {t('reports.exportPayments')}
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t('reports.taxSummary')}</CardTitle>
        </CardHeader>
        <CardContent>
          <table className="w-full text-sm">
            <thead className="text-left text-xs uppercase text-muted-foreground">
              <tr>
                <th className="pb-2 font-medium">{t('reports.columns.currency')}</th>
                <th className="pb-2 text-right font-medium">{t('reports.columns.rate')}</th>
                <th className="pb-2 text-right font-medium">{t('reports.columns.net')}</th>
                <th className="pb-2 text-right font-medium">{t('reports.columns.tax')}</th>
                <th className="pb-2 text-right font-medium">{t('reports.columns.gross')}</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {summary.isPending ? (
                <tr>
                  <td colSpan={5} className="py-8 text-center text-muted-foreground">
                    {t('common.loading')}
                  </td>
                </tr>
              ) : !summary.data?.length ? (
                <tr>
                  <td colSpan={5} className="py-8 text-center text-muted-foreground">
                    {t('reports.empty')}
                  </td>
                </tr>
              ) : (
                summary.data.map((row) => (
                  <tr key={`${row.currency}-${row.taxRate}`}>
                    <td className="py-2">{row.currency}</td>
                    <td className="py-2 text-right tabular-nums">{Number(row.taxRate).toFixed(2)}%</td>
                    <td className="py-2 text-right tabular-nums">
                      {formatCurrency(row.net, row.currency, i18n.language)}
                    </td>
                    <td className="py-2 text-right tabular-nums">
                      {formatCurrency(row.tax, row.currency, i18n.language)}
                    </td>
                    <td className="py-2 text-right font-medium tabular-nums">
                      {formatCurrency(row.gross, row.currency, i18n.language)}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </CardContent>
      </Card>
    </div>
  );
}
