import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import '@/i18n';

vi.mock('@/api/invoices', () => ({
  fetchInvoicePdf: vi.fn(),
}));

import { fetchInvoicePdf } from '@/api/invoices';
import { InvoicePreviewDialog } from './InvoicePreviewDialog';

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(fetchInvoicePdf).mockResolvedValue(new Blob(['%PDF'], { type: 'application/pdf' }));
  vi.stubGlobal('URL', {
    ...URL,
    createObjectURL: vi.fn(() => 'blob:mock'),
    revokeObjectURL: vi.fn(),
  });
});

describe('InvoicePreviewDialog', () => {
  it('fetches the preview with the stored template on open', async () => {
    render(
      <InvoicePreviewDialog
        open
        onClose={() => undefined}
        invoiceId="inv-1"
        invoiceNumber="INV-1"
        initialTemplate="modern"
      />,
    );
    await waitFor(() =>
      expect(fetchInvoicePdf).toHaveBeenCalledWith('inv-1', 'preview', 'modern'),
    );
  });

  it('re-fetches with the toggled template', async () => {
    const user = userEvent.setup();
    render(
      <InvoicePreviewDialog
        open
        onClose={() => undefined}
        invoiceId="inv-1"
        invoiceNumber="INV-1"
        initialTemplate="classic"
      />,
    );
    await waitFor(() =>
      expect(fetchInvoicePdf).toHaveBeenCalledWith('inv-1', 'preview', 'classic'),
    );

    await user.click(screen.getByRole('button', { name: 'Modern' }));

    await waitFor(() =>
      expect(fetchInvoicePdf).toHaveBeenCalledWith('inv-1', 'preview', 'modern'),
    );
  });
});
