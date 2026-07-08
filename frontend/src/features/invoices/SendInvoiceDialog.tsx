import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { Send } from 'lucide-react';

import { useEmailPreview, useResendInvoice, useSendInvoice } from '@/hooks/useInvoices';
import type { SendInvoicePayload } from '@/api/invoices';
import { findInvalidEmail, parseEmailList } from '@/lib/email';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Modal } from '@/components/Modal';
import type { ProblemDetail } from '@/types/api';

const schema = z
  .object({
    recipient: z.string().min(1).email(),
    cc: z.string().optional(),
    bcc: z.string().optional(),
    subject: z.string().max(255).optional(),
    message: z.string().max(4000).optional(),
  })
  .superRefine((values, ctx) => {
    (['cc', 'bcc'] as const).forEach((field) => {
      if (findInvalidEmail(parseEmailList(values[field] ?? ''))) {
        ctx.addIssue({ code: z.ZodIssueCode.custom, path: [field], message: 'invalid' });
      }
    });
  });
type FormValues = z.infer<typeof schema>;

export function SendInvoiceDialog({
  open,
  onClose,
  invoiceId,
  invoiceNumber,
  defaultRecipient,
  mode = 'send',
  onSent,
}: {
  open: boolean;
  onClose: () => void;
  invoiceId: string;
  invoiceNumber: string;
  defaultRecipient: string | null;
  mode?: 'send' | 'resend';
  onSent?: () => void;
}) {
  const { t } = useTranslation();
  const send = useSendInvoice();
  const resend = useResendInvoice();
  const preview = useEmailPreview(invoiceId, open);
  const isResend = mode === 'resend';

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { recipient: defaultRecipient ?? '', cc: '', bcc: '', subject: '', message: '' },
  });

  // Prefill with the exact email the backend would send, once the preview loads.
  useEffect(() => {
    if (!open) return;
    reset({
      recipient: defaultRecipient ?? preview.data?.recipientEmail ?? '',
      cc: '',
      bcc: '',
      subject: preview.data?.subject ?? '',
      message: preview.data?.body ?? '',
    });
  }, [open, defaultRecipient, preview.data, reset]);

  async function onSubmit(values: FormValues) {
    const cc = parseEmailList(values.cc ?? '');
    const bcc = parseEmailList(values.bcc ?? '');
    const payload: SendInvoicePayload = {
      recipientEmail: values.recipient,
      cc: cc.length ? cc : undefined,
      bcc: bcc.length ? bcc : undefined,
      subject: values.subject || undefined,
      message: values.message || undefined,
    };
    try {
      if (isResend) {
        await resend.mutateAsync({ id: invoiceId, payload });
        toast.success(t('invoices.resent'));
      } else {
        await send.mutateAsync({ id: invoiceId, payload });
        toast.success(t('invoices.sent'));
      }
      onSent?.();
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
      title={t(isResend ? 'invoices.resendTitle' : 'invoices.sendTitle', { number: invoiceNumber })}
      description={t(isResend ? 'invoices.resendSubtitle' : 'invoices.sendSubtitle')}
      size="lg"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="send-invoice-form" disabled={isSubmitting}>
            <Send className="mr-2 h-4 w-4" />
            {isSubmitting
              ? t('invoices.sending')
              : t(isResend ? 'invoices.actions.resend' : 'invoices.actions.send')}
          </Button>
        </>
      }
    >
      <form
        id="send-invoice-form"
        className="space-y-4"
        onSubmit={(e) => void handleSubmit(onSubmit)(e)}
        noValidate
      >
        <div className="space-y-1.5">
          <Label htmlFor="send-recipient">{t('invoices.fields.recipient')} *</Label>
          <Input
            id="send-recipient"
            type="email"
            aria-invalid={!!errors.recipient}
            placeholder="customer@example.com"
            {...register('recipient')}
          />
          {errors.recipient ? (
            <p className="text-xs text-destructive">{t('invoices.fields.invalidRecipient')}</p>
          ) : (
            <p className="text-xs text-muted-foreground">{t('invoices.fields.recipientHint')}</p>
          )}
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label htmlFor="send-cc">{t('invoices.fields.cc')}</Label>
            <Input id="send-cc" aria-invalid={!!errors.cc} {...register('cc')} />
            {errors.cc && (
              <p className="text-xs text-destructive">{t('invoices.fields.invalidEmailList')}</p>
            )}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="send-bcc">{t('invoices.fields.bcc')}</Label>
            <Input id="send-bcc" aria-invalid={!!errors.bcc} {...register('bcc')} />
            {errors.bcc && (
              <p className="text-xs text-destructive">{t('invoices.fields.invalidEmailList')}</p>
            )}
          </div>
          <p className="-mt-2 text-xs text-muted-foreground sm:col-span-2">
            {t('invoices.fields.ccBccHint')}
          </p>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="send-subject">{t('invoices.fields.subject')}</Label>
          <Input
            id="send-subject"
            placeholder={t('invoices.fields.subjectPlaceholder')}
            {...register('subject')}
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="send-message">{t('invoices.fields.message')}</Label>
          <Textarea
            id="send-message"
            rows={5}
            placeholder={t('invoices.fields.messagePlaceholder')}
            {...register('message')}
          />
        </div>
      </form>
    </Modal>
  );
}
