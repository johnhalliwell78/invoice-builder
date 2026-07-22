import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';

import { useMakeRecurring } from '@/hooks/useRecurring';
import type { Frequency } from '@/api/recurring';
import { Modal } from '@/components/Modal';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import type { ProblemDetail } from '@/types/api';

const FREQUENCIES: Frequency[] = ['DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY'];

interface Props {
  open: boolean;
  onClose: () => void;
  invoiceId: string;
  invoiceNumber: string;
}

export function MakeRecurringDialog({ open, onClose, invoiceId, invoiceNumber }: Props) {
  const { t } = useTranslation();
  const [frequency, setFrequency] = useState<Frequency>('MONTHLY');
  const [autoSend, setAutoSend] = useState(false);
  const makeRecurring = useMakeRecurring(invoiceId);

  async function submit() {
    try {
      await makeRecurring.mutateAsync({ frequency, autoSend });
      toast.success(t('recurring.created'));
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
      title={t('recurring.dialog.title', { number: invoiceNumber })}
      description={t('recurring.dialog.description')}
    >
      <div className="space-y-4">
        <div className="space-y-1.5">
          <Label>{t('recurring.columns.frequency')}</Label>
          <select
            className="h-10 w-full rounded-md border bg-background px-3 text-sm"
            value={frequency}
            onChange={(e) => setFrequency(e.target.value as Frequency)}
          >
            {FREQUENCIES.map((f) => (
              <option key={f} value={f}>
                {t(`recurring.frequency.${f}`)}
              </option>
            ))}
          </select>
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            className="h-4 w-4 rounded border"
            checked={autoSend}
            onChange={(e) => setAutoSend(e.target.checked)}
          />
          {t('recurring.dialog.autoSend')}
        </label>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="outline" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="button" disabled={makeRecurring.isPending} onClick={() => void submit()}>
            {makeRecurring.isPending ? t('common.saving') : t('recurring.dialog.confirm')}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
