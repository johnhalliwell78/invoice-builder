import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';

import { useRecordPayment } from '@/hooks/useInvoices';
import type { PaymentMethod } from '@/api/invoices';
import { Modal } from '@/components/Modal';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { todayIso } from '@/lib/format';
import type { ProblemDetail } from '@/types/api';

const METHODS: PaymentMethod[] = ['BANK_TRANSFER', 'CARD', 'CASH', 'PAYPAL', 'OTHER'];

interface Props {
  open: boolean;
  onClose: () => void;
  invoiceId: string;
  invoiceNumber: string;
  /** Remaining balance, used as the default amount. */
  balance: string;
}

export function RecordPaymentDialog({ open, onClose, invoiceId, invoiceNumber, balance }: Props) {
  const { t } = useTranslation();
  const [amount, setAmount] = useState(balance);
  const [method, setMethod] = useState<PaymentMethod>('BANK_TRANSFER');
  const [paidOn, setPaidOn] = useState(todayIso());
  const [note, setNote] = useState('');
  const record = useRecordPayment(invoiceId);

  // Re-seed the default amount each time the dialog opens (balance changes as payments land).
  useEffect(() => {
    if (open) setAmount(balance);
  }, [open, balance]);

  async function submit() {
    try {
      await record.mutateAsync({ amount, method, paidOn, note: note.trim() || undefined });
      toast.success(t('payments.recorded'));
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
      title={t('payments.dialog.title', { number: invoiceNumber })}
      description={t('payments.dialog.description', { balance })}
    >
      <div className="space-y-4">
        <div className="space-y-1.5">
          <Label>{t('payments.fields.amount')}</Label>
          <Input inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} />
        </div>
        <div className="space-y-1.5">
          <Label>{t('payments.fields.method')}</Label>
          <select
            className="h-10 w-full rounded-md border bg-background px-3 text-sm"
            value={method}
            onChange={(e) => setMethod(e.target.value as PaymentMethod)}
          >
            {METHODS.map((m) => (
              <option key={m} value={m}>
                {t(`payments.method.${m}`)}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-1.5">
          <Label>{t('payments.fields.paidOn')}</Label>
          <Input type="date" value={paidOn} onChange={(e) => setPaidOn(e.target.value)} />
        </div>
        <div className="space-y-1.5">
          <Label>{t('payments.fields.note')}</Label>
          <Input value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} />
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="outline" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="button" disabled={record.isPending} onClick={() => void submit()}>
            {record.isPending ? t('common.saving') : t('payments.dialog.confirm')}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
