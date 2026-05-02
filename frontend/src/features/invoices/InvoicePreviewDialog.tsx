import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Download } from 'lucide-react';

import { fetchInvoicePdf } from '@/api/invoices';
import { Button } from '@/components/ui/button';
import { Modal } from '@/components/Modal';

export function InvoicePreviewDialog({
  open,
  onClose,
  invoiceId,
  invoiceNumber,
}: {
  open: boolean;
  onClose: () => void;
  invoiceId: string;
  invoiceNumber: string;
}) {
  const { t } = useTranslation();
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    let revoke: string | null = null;
    setError(null);
    setBlobUrl(null);
    fetchInvoicePdf(invoiceId, 'preview')
      .then((blob) => {
        const url = URL.createObjectURL(blob);
        revoke = url;
        setBlobUrl(url);
      })
      .catch(() => setError(t('invoices.previewFailed')));
    return () => {
      if (revoke) URL.revokeObjectURL(revoke);
    };
  }, [open, invoiceId, t]);

  async function handleDownload() {
    const blob = await fetchInvoicePdf(invoiceId, 'pdf');
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `invoice-${invoiceNumber}.pdf`;
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t('invoices.previewTitle', { number: invoiceNumber })}
      size="xl"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            {t('common.close')}
          </Button>
          <Button onClick={() => void handleDownload()} disabled={!blobUrl}>
            <Download className="mr-2 h-4 w-4" />
            {t('invoices.download')}
          </Button>
        </>
      }
    >
      <div className="h-[70vh] overflow-hidden rounded-md border bg-muted/30">
        {error ? (
          <div className="flex h-full items-center justify-center text-destructive">{error}</div>
        ) : blobUrl ? (
          <iframe src={blobUrl} title="Invoice preview" className="h-full w-full" />
        ) : (
          <div className="flex h-full items-center justify-center text-muted-foreground">
            {t('common.loading')}
          </div>
        )}
      </div>
    </Modal>
  );
}
