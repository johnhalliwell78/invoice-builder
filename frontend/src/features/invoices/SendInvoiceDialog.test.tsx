import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ReactNode } from 'react';

import '@/i18n';

vi.mock('@/api/invoices', () => ({
  listInvoices: vi.fn(),
  getInvoice: vi.fn(),
  createInvoice: vi.fn(),
  updateInvoice: vi.fn(),
  deleteInvoice: vi.fn(),
  sendInvoice: vi.fn(),
  resendInvoice: vi.fn(),
  markPaid: vi.fn(),
  cancelInvoice: vi.fn(),
  fetchInvoicePdf: vi.fn(),
  getPublicInvoice: vi.fn(),
  getEmailPreview: vi.fn(),
}));

import { getEmailPreview, resendInvoice, sendInvoice } from '@/api/invoices';
import { SendInvoiceDialog } from './SendInvoiceDialog';

const PREVIEW = {
  recipientEmail: 'billing@widget.example',
  subject: 'Invoice INV-1 from Acme',
  body: 'Hi, please find your invoice attached.',
};

function renderDialog(props: Partial<Parameters<typeof SendInvoiceDialog>[0]> = {}) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
  return render(
    <SendInvoiceDialog
      open
      onClose={() => undefined}
      invoiceId="inv-1"
      invoiceNumber="INV-1"
      defaultRecipient={null}
      {...props}
    />,
    { wrapper },
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getEmailPreview).mockResolvedValue(PREVIEW);
  vi.mocked(sendInvoice).mockResolvedValue({} as never);
  vi.mocked(resendInvoice).mockResolvedValue({} as never);
});

describe('SendInvoiceDialog', () => {
  it('prefills recipient, subject, and message from the email preview', async () => {
    renderDialog();
    expect(await screen.findByDisplayValue('Invoice INV-1 from Acme')).toBeInTheDocument();
    expect(screen.getByDisplayValue('billing@widget.example')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Hi, please find your invoice attached.')).toBeInTheDocument();
  });

  it('rejects an invalid CC address and does not submit', async () => {
    const user = userEvent.setup();
    renderDialog();
    await screen.findByDisplayValue('Invoice INV-1 from Acme');

    await user.type(screen.getByLabelText('CC'), 'not-an-email');
    await user.click(screen.getByRole('button', { name: 'Send' }));

    expect(await screen.findByText('One or more email addresses are invalid.')).toBeInTheDocument();
    expect(sendInvoice).not.toHaveBeenCalled();
  });

  it('submits parsed CC/BCC lists with the payload', async () => {
    const user = userEvent.setup();
    renderDialog();
    await screen.findByDisplayValue('Invoice INV-1 from Acme');

    await user.type(screen.getByLabelText('CC'), 'a@x.io, b@x.io');
    await user.click(screen.getByRole('button', { name: 'Send' }));

    await waitFor(() =>
      expect(sendInvoice).toHaveBeenCalledWith(
        'inv-1',
        expect.objectContaining({
          recipientEmail: 'billing@widget.example',
          cc: ['a@x.io', 'b@x.io'],
        }),
      ),
    );
  });

  it('uses the resend mutation and title in resend mode', async () => {
    const user = userEvent.setup();
    renderDialog({ mode: 'resend' });
    await screen.findByDisplayValue('Invoice INV-1 from Acme');

    expect(screen.getByRole('dialog', { name: 'Resend INV-1' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Resend' }));

    await waitFor(() => expect(resendInvoice).toHaveBeenCalled());
    expect(sendInvoice).not.toHaveBeenCalled();
  });
});
