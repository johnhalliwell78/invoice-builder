import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { Send } from 'lucide-react';

import { useSendInvoice } from '@/hooks/useInvoices';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Modal } from '@/components/Modal';
import type { ProblemDetail } from '@/types/api';

export function SendInvoiceDialog({
  open,
  onClose,
  invoiceId,
  invoiceNumber,
  defaultRecipient,
  onSent,
}: {
  open: boolean;
  onClose: () => void;
  invoiceId: string;
  invoiceNumber: string;
  defaultRecipient: string | null;
  onSent: () => void;
}) {
  const { t } = useTranslation();
  const [recipient, setRecipient] = useState(defaultRecipient ?? '');
  const [subject, setSubject] = useState('');
  const [message, setMessage] = useState('');
  const send = useSendInvoice();

  useEffect(() => {
    if (open) setRecipient(defaultRecipient ?? '');
  }, [open, defaultRecipient]);

  async function handleSend() {
    try {
      await send.mutateAsync({
        id: invoiceId,
        payload: {
          recipientEmail: recipient || undefined,
          subject: subject || undefined,
          message: message || undefined,
        },
      });
      toast.success(t('invoices.sent'));
      onSent();
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
      title={t('invoices.sendTitle', { number: invoiceNumber })}
      description={t('invoices.sendSubtitle')}
      size="lg"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button onClick={() => void handleSend()} disabled={send.isPending || !recipient}>
            <Send className="mr-2 h-4 w-4" />
            {send.isPending ? t('invoices.sending') : t('invoices.actions.send')}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <div className="space-y-1.5">
          <Label htmlFor="send-recipient">{t('invoices.fields.recipient')} *</Label>
          <Input
            id="send-recipient"
            type="email"
            value={recipient}
            onChange={(e) => setRecipient(e.target.value)}
            placeholder="customer@example.com"
          />
          <p className="text-xs text-muted-foreground">{t('invoices.fields.recipientHint')}</p>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="send-subject">{t('invoices.fields.subject')}</Label>
          <Input
            id="send-subject"
            value={subject}
            onChange={(e) => setSubject(e.target.value)}
            placeholder={t('invoices.fields.subjectPlaceholder')}
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="send-message">{t('invoices.fields.message')}</Label>
          <Textarea
            id="send-message"
            rows={5}
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            placeholder={t('invoices.fields.messagePlaceholder')}
          />
        </div>
      </div>
    </Modal>
  );
}
