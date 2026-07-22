import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

import { getPublicInvoice, type PublicInvoiceView } from '@/api/invoices';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { StatusBadge } from '@/features/invoices/StatusBadge';
import { formatCurrency, formatDate } from '@/lib/format';

export default function PublicInvoicePage() {
  const { t, i18n } = useTranslation();
  const { token } = useParams<{ token: string }>();
  const [invoice, setInvoice] = useState<PublicInvoiceView | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!token) return;
    getPublicInvoice(token)
      .then(setInvoice)
      .catch(() => setError(true));
  }, [token]);

  if (error) {
    return (
      <PublicShell>
        <div className="rounded-md border border-destructive/50 bg-destructive/5 p-6 text-destructive">
          {t('invoices.publicNotFound')}
        </div>
      </PublicShell>
    );
  }
  if (!invoice) {
    return (
      <PublicShell>
        <div className="text-muted-foreground">{t('common.loading')}</div>
      </PublicShell>
    );
  }

  const cur = invoice.currency;
  return (
    <PublicShell>
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-3xl font-semibold tracking-tight">
            {t(invoice.docType === 'ESTIMATE' ? 'estimates.publicTitle' : 'invoices.publicTitle')}{' '}
            {invoice.invoiceNumber}
          </h1>
          <p className="text-sm text-muted-foreground">{invoice.issuer.name}</p>
        </div>
        <StatusBadge status={invoice.status} />
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>{t('invoices.section.lineItems')}</CardTitle>
          </CardHeader>
          <CardContent>
            <table className="w-full text-sm">
              <thead className="text-left text-xs uppercase text-muted-foreground">
                <tr>
                  <th className="pb-2 font-medium">{t('invoices.fields.description')}</th>
                  <th className="pb-2 text-right font-medium">{t('invoices.fields.qty')}</th>
                  <th className="pb-2 text-right font-medium">{t('invoices.fields.unitPrice')}</th>
                  <th className="pb-2 text-right font-medium">{t('invoices.fields.taxPct')}</th>
                  <th className="pb-2 text-right font-medium">{t('invoices.totals.amount')}</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {invoice.lineItems.map((li) => (
                  <tr key={li.id}>
                    <td className="py-2">{li.description}</td>
                    <td className="py-2 text-right tabular-nums">{li.quantity}</td>
                    <td className="py-2 text-right tabular-nums">
                      {formatCurrency(li.unitPrice, cur, i18n.language)}
                    </td>
                    <td className="py-2 text-right tabular-nums">{li.taxRate}%</td>
                    <td className="py-2 text-right tabular-nums">
                      {formatCurrency(li.amount, cur, i18n.language)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            <div className="ml-auto mt-6 max-w-xs space-y-1 text-sm">
              <Row label={t('invoices.totals.subtotal')} value={formatCurrency(invoice.subtotal, cur, i18n.language)} />
              <Row label={t('invoices.totals.tax')} value={formatCurrency(invoice.taxTotal, cur, i18n.language)} />
              {Number(invoice.discountAmount) > 0 && (
                <Row
                  label={t('invoices.totals.discount')}
                  value={formatCurrency(invoice.discountAmount, cur, i18n.language)}
                />
              )}
              <div className="my-2 border-t" />
              <Row
                label={t('invoices.totals.total')}
                value={formatCurrency(invoice.total, cur, i18n.language)}
                bold
              />
            </div>
          </CardContent>
        </Card>

        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>{t('invoices.section.metadata')}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2 text-sm">
              <Row label={t('invoices.fields.issueDate')} value={formatDate(invoice.issueDate, i18n.language)} />
              <Row label={t('invoices.fields.dueDate')} value={formatDate(invoice.dueDate, i18n.language)} />
              {invoice.issuer.taxId && (
                <Row label={t('pdf.invoice.taxId')} value={invoice.issuer.taxId} />
              )}
            </CardContent>
          </Card>

          {(invoice.notes || invoice.terms) && (
            <Card>
              <CardHeader>
                <CardTitle>{t('invoices.section.notesAndTerms')}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3 text-sm">
                {invoice.notes && <p className="whitespace-pre-line">{invoice.notes}</p>}
                {invoice.terms && (
                  <p className="whitespace-pre-line text-muted-foreground">{invoice.terms}</p>
                )}
              </CardContent>
            </Card>
          )}
        </div>
      </div>
    </PublicShell>
  );
}

function PublicShell({ children }: { children: React.ReactNode }) {
  const { t } = useTranslation();
  return (
    <div className="min-h-screen bg-muted/30">
      <header className="border-b bg-background">
        <div className="mx-auto max-w-5xl p-4 text-sm font-medium">{t('app.name')}</div>
      </header>
      <main className="mx-auto max-w-5xl p-6">{children}</main>
    </div>
  );
}

function Row({ label, value, bold }: { label: string; value: string; bold?: boolean }) {
  return (
    <div className={`flex justify-between ${bold ? 'font-semibold' : ''}`}>
      <span className="text-muted-foreground">{label}</span>
      <span className="tabular-nums">{value}</span>
    </div>
  );
}
