import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { ArrowLeft, ArrowRightLeft, Ban, Banknote, CheckCircle2, Copy, Eye, Pencil, Repeat, Send, Trash2, XCircle } from 'lucide-react';

import {
  useApproveEstimate,
  useCancelInvoice,
  useConvertEstimate,
  useDeclineEstimate,
  useDeleteInvoice,
  useDuplicateInvoice,
  useInvoice,
  useMarkPaid,
  usePayments,
  useReminders,
} from '@/hooks/useInvoices';
import { useCustomer } from '@/hooks/useCustomers';
import { useEntityAudit } from '@/hooks/useAudit';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader } from '@/components/PageHeader';
import { StatusBadge } from './StatusBadge';
import { InvoicePreviewDialog } from './InvoicePreviewDialog';
import { SendInvoiceDialog } from './SendInvoiceDialog';
import { MakeRecurringDialog } from './MakeRecurringDialog';
import { RecordPaymentDialog } from './RecordPaymentDialog';
import { formatCurrency, formatDate } from '@/lib/format';
import type { ProblemDetail } from '@/types/api';

export default function InvoiceDetailPage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const { data: invoice, isPending, error } = useInvoice(id);
  const customer = useCustomer(invoice?.customerId);
  const reminders = useReminders(id);
  const payments = usePayments(id);
  const activity = useEntityAudit('Invoice', id);

  const markPaid = useMarkPaid();
  const cancel = useCancelInvoice();
  const del = useDeleteInvoice();
  const duplicate = useDuplicateInvoice();
  const approve = useApproveEstimate();
  const decline = useDeclineEstimate();
  const convert = useConvertEstimate();
  const [previewOpen, setPreviewOpen] = useState(false);
  const [sendOpen, setSendOpen] = useState(false);
  const [recurringOpen, setRecurringOpen] = useState(false);
  const [paymentOpen, setPaymentOpen] = useState(false);
  const [sendMode, setSendMode] = useState<'send' | 'resend'>('send');

  if (isPending) {
    return <div className="text-muted-foreground">{t('common.loading')}</div>;
  }
  if (error || !invoice) {
    return <div className="text-destructive">{t('common.loadFailed')}</div>;
  }

  async function action<T>(promise: Promise<T>, successKey: string) {
    try {
      await promise;
      toast.success(t(successKey));
    } catch (err) {
      const detail = isAxiosError<ProblemDetail>(err) ? err.response?.data?.detail : undefined;
      toast.error(detail ?? t('auth.errors.default'));
    }
  }

  const isEstimate = invoice.docType === 'ESTIMATE';
  const base = isEstimate ? '/estimates' : '/invoices';
  const isDraft = invoice.status === 'DRAFT';
  const openStatuses = isEstimate ? ['SENT', 'VIEWED'] : ['SENT', 'VIEWED', 'OVERDUE'];
  const canMarkPaid = !isEstimate && openStatuses.includes(invoice.status);
  const canCancel = openStatuses.includes(invoice.status);
  const canResend = openStatuses.includes(invoice.status);
  const canDecide = isEstimate && openStatuses.includes(invoice.status);
  const canConvert = isEstimate && invoice.status === 'APPROVED' && !invoice.convertedInvoiceId;
  const cur = invoice.currency;
  const balance = (Number(invoice.total) - Number(invoice.amountPaid)).toFixed(2);

  return (
    <div>
      <PageHeader
        title={`${invoice.invoiceNumber}`}
        description={customer.data?.name ?? '—'}
        actions={
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={() => navigate(base)}>
              <ArrowLeft className="mr-2 h-4 w-4" />
              {t('common.back')}
            </Button>
            <Button variant="outline" onClick={() => setPreviewOpen(true)}>
              <Eye className="mr-2 h-4 w-4" />
              {t('invoices.actions.preview')}
            </Button>
            <Button
              variant="outline"
              disabled={duplicate.isPending}
              onClick={() =>
                void action(
                  duplicate
                    .mutateAsync(invoice.id)
                    .then((copy) => navigate(`${base}/${copy.id}/edit`)),
                  'invoices.duplicated',
                )
              }
            >
              <Copy className="mr-2 h-4 w-4" />
              {t('invoices.actions.duplicate')}
            </Button>
            {isDraft && (
              <>
                <Button variant="outline" onClick={() => navigate(`${base}/${invoice.id}/edit`)}>
                  <Pencil className="mr-2 h-4 w-4" />
                  {t('common.edit')}
                </Button>
                <Button
                  variant="outline"
                  onClick={() =>
                    void (window.confirm(t('invoices.deleteConfirm', { number: invoice.invoiceNumber })) &&
                      action(del.mutateAsync(invoice.id).then(() => navigate(base)), 'invoices.deleted'))
                  }
                >
                  <Trash2 className="mr-2 h-4 w-4 text-destructive" />
                  {t('common.delete')}
                </Button>
                <Button onClick={() => { setSendMode('send'); setSendOpen(true); }}>
                  <Send className="mr-2 h-4 w-4" />
                  {t('invoices.actions.send')}
                </Button>
              </>
            )}
            {!isEstimate && (
              <Button variant="outline" onClick={() => setRecurringOpen(true)}>
                <Repeat className="mr-2 h-4 w-4" />
                {t('recurring.actions.makeRecurring')}
              </Button>
            )}
            {canDecide && (
              <>
                <Button
                  disabled={approve.isPending}
                  onClick={() => void action(approve.mutateAsync(invoice.id), 'estimates.approved')}
                >
                  <CheckCircle2 className="mr-2 h-4 w-4" />
                  {t('estimates.actions.approve')}
                </Button>
                <Button
                  variant="outline"
                  disabled={decline.isPending}
                  onClick={() => void action(decline.mutateAsync(invoice.id), 'estimates.declined')}
                >
                  <XCircle className="mr-2 h-4 w-4" />
                  {t('estimates.actions.decline')}
                </Button>
              </>
            )}
            {canConvert && (
              <Button
                disabled={convert.isPending}
                onClick={() =>
                  void action(
                    convert
                      .mutateAsync(invoice.id)
                      .then((created) => navigate(`/invoices/${created.id}`)),
                    'estimates.converted',
                  )
                }
              >
                <ArrowRightLeft className="mr-2 h-4 w-4" />
                {t('estimates.actions.convert')}
              </Button>
            )}
            {canMarkPaid && (
              <Button variant="outline" onClick={() => setPaymentOpen(true)}>
                <Banknote className="mr-2 h-4 w-4" />
                {t('payments.actions.record')}
              </Button>
            )}
            {canMarkPaid && (
              <Button onClick={() => void action(markPaid.mutateAsync(invoice.id), 'invoices.markedPaid')}>
                <CheckCircle2 className="mr-2 h-4 w-4" />
                {t('invoices.actions.markPaid')}
              </Button>
            )}
            {canResend && (
              <Button
                variant="outline"
                onClick={() => { setSendMode('resend'); setSendOpen(true); }}
              >
                <Send className="mr-2 h-4 w-4" />
                {t('invoices.actions.resend')}
              </Button>
            )}
            {canCancel && (
              <Button
                variant="outline"
                onClick={() => void action(cancel.mutateAsync(invoice.id), 'invoices.cancelled')}
              >
                <Ban className="mr-2 h-4 w-4" />
                {t('invoices.actions.cancel')}
              </Button>
            )}
          </div>
        }
      />

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle>{t('invoices.section.lineItems')}</CardTitle>
              <StatusBadge status={invoice.status} />
            </div>
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
                  <tr key={li.id ?? li.description}>
                    <td className="py-2">{li.description}</td>
                    <td className="py-2 text-right tabular-nums">{li.quantity}</td>
                    <td className="py-2 text-right tabular-nums">
                      {formatCurrency(li.unitPrice, cur, i18n.language)}
                    </td>
                    <td className="py-2 text-right tabular-nums">{li.taxRate}%</td>
                    <td className="py-2 text-right tabular-nums">
                      {formatCurrency(li.amount ?? '0', cur, i18n.language)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            <div className="ml-auto mt-6 max-w-xs space-y-1 text-sm">
              <Row label={t('invoices.totals.subtotal')} value={formatCurrency(invoice.subtotal, cur, i18n.language)} />
              <Row label={t('invoices.totals.tax')} value={formatCurrency(invoice.taxTotal, cur, i18n.language)} />
              <Row
                label={t('invoices.totals.discount')}
                value={formatCurrency(invoice.discountAmount, cur, i18n.language)}
              />
              <div className="my-2 border-t" />
              <Row label={t('invoices.totals.total')} value={formatCurrency(invoice.total, cur, i18n.language)} bold />
              {invoice.amountPaid && Number(invoice.amountPaid) > 0 && (
                <Row
                  label={t('invoices.totals.amountPaid')}
                  value={formatCurrency(invoice.amountPaid, cur, i18n.language)}
                />
              )}
            </div>
          </CardContent>
        </Card>

        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>{t('invoices.section.metadata')}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2 text-sm">
              <DetailRow label={t('invoices.fields.issueDate')} value={formatDate(invoice.issueDate, i18n.language)} />
              <DetailRow label={t('invoices.fields.dueDate')} value={formatDate(invoice.dueDate, i18n.language)} />
              {invoice.sentAt && (
                <DetailRow label={t('invoices.timeline.sent')} value={formatDate(invoice.sentAt, i18n.language)} />
              )}
              {invoice.viewedAt && (
                <DetailRow label={t('invoices.timeline.viewed')} value={formatDate(invoice.viewedAt, i18n.language)} />
              )}
              {invoice.paidAt && (
                <DetailRow label={t('invoices.timeline.paid')} value={formatDate(invoice.paidAt, i18n.language)} />
              )}
            </CardContent>
          </Card>

          {(invoice.notes || invoice.terms) && (
            <Card>
              <CardHeader>
                <CardTitle>{t('invoices.section.notesAndTerms')}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3 text-sm">
                {invoice.notes && (
                  <div>
                    <div className="text-xs uppercase text-muted-foreground">{t('invoices.fields.notes')}</div>
                    <p className="whitespace-pre-line">{invoice.notes}</p>
                  </div>
                )}
                {invoice.terms && (
                  <div>
                    <div className="text-xs uppercase text-muted-foreground">{t('invoices.fields.terms')}</div>
                    <p className="whitespace-pre-line">{invoice.terms}</p>
                  </div>
                )}
              </CardContent>
            </Card>
          )}

          {reminders.data && reminders.data.length > 0 && (
            <Card>
              <CardHeader>
                <CardTitle>{t('invoices.reminders.title')}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-2 text-sm">
                {reminders.data.map((reminder) => (
                  <div key={reminder.id} className="flex items-center justify-between gap-2">
                    <span className="text-muted-foreground">
                      {t(`invoices.reminders.${reminder.type}`)}
                    </span>
                    <span>{formatDate(reminder.sentAt, i18n.language)}</span>
                  </div>
                ))}
              </CardContent>
            </Card>
          )}

          {payments.data && payments.data.length > 0 && (
            <Card>
              <CardHeader>
                <CardTitle>{t('payments.title')}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-2 text-sm">
                {payments.data.map((payment) => (
                  <div key={payment.id} className="flex items-center justify-between gap-2">
                    <span className="text-muted-foreground">
                      {t(`payments.method.${payment.method}`)} · {formatDate(payment.paidOn, i18n.language)}
                    </span>
                    <span className="tabular-nums">
                      {formatCurrency(payment.amount, cur, i18n.language)}
                    </span>
                  </div>
                ))}
                {invoice.status !== 'PAID' && (
                  <div className="flex items-center justify-between gap-2 border-t pt-2 font-medium">
                    <span>{t('payments.balance')}</span>
                    <span className="tabular-nums">{formatCurrency(balance, cur, i18n.language)}</span>
                  </div>
                )}
              </CardContent>
            </Card>
          )}

          {activity.data && activity.data.length > 0 && (
            <Card>
              <CardHeader>
                <CardTitle>{t('invoices.activity.title')}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-2 text-sm">
                {activity.data.map((entry) => (
                  <div key={entry.id} className="flex items-center justify-between gap-2">
                    <span className="text-muted-foreground">
                      {t(`audit.actions.${entry.action}`)}
                    </span>
                    <span>{formatDate(entry.createdAt, i18n.language)}</span>
                  </div>
                ))}
              </CardContent>
            </Card>
          )}
        </div>
      </div>

      <InvoicePreviewDialog
        open={previewOpen}
        onClose={() => setPreviewOpen(false)}
        invoiceId={invoice.id}
        invoiceNumber={invoice.invoiceNumber}
        initialTemplate={invoice.template}
      />
      <RecordPaymentDialog
        open={paymentOpen}
        onClose={() => setPaymentOpen(false)}
        invoiceId={invoice.id}
        invoiceNumber={invoice.invoiceNumber}
        balance={balance}
      />
      <MakeRecurringDialog
        open={recurringOpen}
        onClose={() => setRecurringOpen(false)}
        invoiceId={invoice.id}
        invoiceNumber={invoice.invoiceNumber}
      />
      <SendInvoiceDialog
        open={sendOpen}
        onClose={() => setSendOpen(false)}
        invoiceId={invoice.id}
        invoiceNumber={invoice.invoiceNumber}
        defaultRecipient={customer.data?.email ?? null}
        mode={sendMode}
      />
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

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-muted-foreground">{label}</span>
      <span>{value}</span>
    </div>
  );
}
