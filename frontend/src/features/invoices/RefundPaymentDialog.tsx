import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';

import { useReversePayment } from '@/hooks/useInvoices';
import type { Payment, ReversalReason } from '@/api/invoices';
import { Modal } from '@/components/Modal';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import type { ProblemDetail } from '@/types/api';

const REASONS: ReversalReason[] = ['REFUND', 'DISPUTE', 'ADJUSTMENT'];

interface Props {
  open: boolean;
  onClose: () => void;
  payment: Payment | null;
  /** How much of this payment has not already been given back. */
  reversible: string;
}

export function RefundPaymentDialog({ open, onClose, payment, reversible }: Props) {
  const { t } = useTranslation();
  const [amount, setAmount] = useState(reversible);
  const [reason, setReason] = useState<ReversalReason>('REFUND');
  const [note, setNote] = useState('');
  const reverse = useReversePayment();

  // Re-seed only as the dialog opens, so a background refetch cannot
  // overwrite what the operator is typing.
  const [wasOpen, setWasOpen] = useState(false);
  if (open && !wasOpen) {
    setWasOpen(true);
    setAmount(reversible);
  } else if (!open && wasOpen) {
    setWasOpen(false);
  }

  async function submit() {
    if (!payment) return;
    try {
      await reverse.mutateAsync({
        paymentId: payment.id,
        payload: { amount, reason, note: note.trim() || undefined },
      });
      toast.success(t('payments.reversal.recorded'));
      setNote('');
      onClose();
    } catch (err) {
      const detail = isAxiosError<ProblemDetail>(err) ? err.response?.data?.detail : undefined;
      toast.error(detail ?? t('auth.errors.default'));
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t('payments.reversal.title')}
      description={t('payments.reversal.description', { amount: reversible })}
    >
      <div className="space-y-4">
        <div className="space-y-1.5">
          <Label>{t('payments.fields.amount')}</Label>
          <Input inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} />
        </div>
        <div className="space-y-1.5">
          <Label>{t('payments.reversal.reason')}</Label>
          <select
            className="h-10 w-full rounded-md border bg-background px-3 text-sm"
            value={reason}
            onChange={(e) => setReason(e.target.value as ReversalReason)}
          >
            {REASONS.map((r) => (
              <option key={r} value={r}>
                {t(`payments.reversal.reasons.${r}`)}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-1.5">
          <Label>{t('payments.fields.note')}</Label>
          <Input value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} />
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="outline" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="button" disabled={reverse.isPending} onClick={() => void submit()}>
            {reverse.isPending ? t('common.saving') : t('payments.reversal.confirm')}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
