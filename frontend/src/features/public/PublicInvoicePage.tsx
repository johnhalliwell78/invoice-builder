import { useCallback, useEffect, useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { CreditCard } from 'lucide-react';

import { isAxiosError } from 'axios';

import { getPublicInvoice, startPublicCheckout, type PublicInvoiceView } from '@/api/invoices';
import type { ProblemDetail } from '@/types/api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { StatusBadge } from '@/features/invoices/StatusBadge';
import { formatCurrency, formatDate } from '@/lib/format';

/** sessionStorage marker: "a Stripe payment for this invoice is in flight". */
function pendingKey(token: string) {
  return `ib_pay_pending_${token}`;
}

export default function PublicInvoicePage() {
  const { t, i18n } = useTranslation();
  const { token } = useParams<{ token: string }>();
  const [invoice, setInvoice] = useState<PublicInvoiceView | null>(null);
  const [error, setError] = useState(false);
  const [redirecting, setRedirecting] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [searchParams, setSearchParams] = useSearchParams();

  const load = useCallback(
    () =>
      token
        ? getPublicInvoice(token)
            .then((fresh) => {
              setInvoice(fresh);
              return fresh;
            })
            .catch(() => {
              setError(true);
              return null;
            })
        : Promise.resolve(null),
    [token],
  );

  useEffect(() => {
    void load();
  }, [load]);

  // Returning from Stripe. The redirect only tells us the shopper came back —
  // the webhook is what actually books the payment, and it usually lands a
  // second or two later. Until the ledger confirms, the invoice still looks
  // unpaid, so we must NOT re-offer the pay button: that is how a shopper
  // ends up charged twice.
  const paymentResult = searchParams.get('payment');
  useEffect(() => {
    if (!paymentResult) return;
    if (paymentResult === 'success') {
      toast.success(t('payments.public.thanks'));
    } else if (paymentResult === 'cancelled') {
      toast.info(t('payments.public.cancelled'));
      if (token) window.sessionStorage.removeItem(pendingKey(token));
    }
    searchParams.delete('payment');
    setSearchParams(searchParams, { replace: true });
    // Runs once per return from Stripe.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [paymentResult]);

  // Poll until the ledger shows MORE paid than before we left. Driven by
  // sessionStorage, so a reload mid-confirmation resumes instead of
  // re-offering the Pay button and inviting a second charge.
  useEffect(() => {
    if (!token) return;
    const raw = window.sessionStorage.getItem(pendingKey(token));
    if (!raw) return;
    const before = Number((JSON.parse(raw) as { amountPaidBefore: string }).amountPaidBefore);

    let cancelled = false;
    let timer = 0;
    setConfirming(true);

    const tick = async (attempt: number) => {
      const fresh = await load();
      if (cancelled) return;
      if (fresh != null && Number(fresh.amountPaid) > before) {
        window.sessionStorage.removeItem(pendingKey(token));
        setConfirming(false);
        return;
      }
      if (attempt >= 8) {
        // Webhook still not visible. Leave Pay suppressed and the marker in
        // place: a stuck webhook must never become a second charge.
        return;
      }
      timer = window.setTimeout(() => void tick(attempt + 1), 2000);
    };
    timer = window.setTimeout(() => void tick(1), 1200);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [token, load, paymentResult]);

  async function pay() {
    if (!token || !invoice) return;
    setRedirecting(true);
    try {
      const url = await startPublicCheckout(token);
      // Remember what the ledger said BEFORE leaving, so on return we can tell
      // "this payment landed" from "some earlier partial payment exists".
      // sessionStorage (not component state) so a reload cannot re-arm Pay.
      window.sessionStorage.setItem(
        pendingKey(token),
        JSON.stringify({ amountPaidBefore: invoice.amountPaid }),
      );
      window.location.href = url;
    } catch (err) {
      setRedirecting(false);
      const detail = isAxiosError<ProblemDetail>(err) ? err.response?.data?.detail : undefined;
      toast.error(detail ?? t('payments.public.failed'));
    }
  }

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
        {confirming && (
          <span className="text-sm text-muted-foreground">{t('payments.public.confirming')}</span>
        )}
        {invoice.paymentEnabled && !confirming && (
          <Button size="lg" disabled={redirecting} onClick={() => void pay()}>
            <CreditCard className="mr-2 h-4 w-4" />
            {redirecting
              ? t('payments.public.redirecting')
              : t('payments.public.pay', {
                  amount: formatCurrency(
                    (Number(invoice.total) - Number(invoice.amountPaid)).toFixed(2),
                    invoice.currency,
                    i18n.language,
                  ),
                })}
          </Button>
        )}
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
