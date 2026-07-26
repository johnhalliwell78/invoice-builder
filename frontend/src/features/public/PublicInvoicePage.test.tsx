import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import '@/i18n';

vi.mock('@/api/invoices', () => ({
  getPublicInvoice: vi.fn(),
  startPublicCheckout: vi.fn(),
}));

import { getPublicInvoice, startPublicCheckout, type PublicInvoiceView } from '@/api/invoices';
import PublicInvoicePage from './PublicInvoicePage';

const invoice = {
  issuer: { name: 'Acme GmbH', address: null, taxId: null },
  recipient: { name: 'Payer Co', address: null },
  invoiceNumber: 'INV-2026-0007',
  docType: 'INVOICE',
  status: 'SENT',
  currency: 'EUR',
  subtotal: '100.00',
  taxTotal: '19.00',
  discountAmount: '0.00',
  total: '119.00',
  amountPaid: '19.00',
  issueDate: '2026-07-01',
  dueDate: '2026-07-31',
  notes: null,
  terms: null,
  paymentEnabled: true,
  lineItems: [],
} as unknown as PublicInvoiceView;

function renderPage(initialEntry = '/i/tok123') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/i/:token" element={<PublicInvoicePage />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getPublicInvoice).mockResolvedValue(invoice);
  vi.mocked(startPublicCheckout).mockResolvedValue('https://checkout.stripe.test/session');
});

describe('PublicInvoicePage', () => {
  it('offers to pay the outstanding balance, not the full total', async () => {
    renderPage();

    // 119.00 total minus 19.00 already paid.
    expect(await screen.findByRole('button', { name: /100/ })).toBeInTheDocument();
  });

  it('starts a checkout session for the invoice token when clicked', async () => {
    const user = userEvent.setup();
    renderPage();
    const payButton = await screen.findByRole('button', { name: /100/ });

    await user.click(payButton);

    await waitFor(() => expect(startPublicCheckout).toHaveBeenCalledWith('tok123'));
  });

  it('hides the pay button when online payment is unavailable', async () => {
    vi.mocked(getPublicInvoice).mockResolvedValue({ ...invoice, paymentEnabled: false });
    renderPage();

    await screen.findByText(/INV-2026-0007/);
    expect(screen.queryByRole('button', { name: /Pay/i })).not.toBeInTheDocument();
  });

  it('re-reads the invoice after returning from Stripe instead of trusting the redirect', async () => {
    renderPage('/i/tok123?payment=success');

    // Once on mount, once after the success return — the webhook is the source
    // of truth, so the page refetches rather than assuming payment landed.
    await waitFor(() => expect(getPublicInvoice).toHaveBeenCalledTimes(2));
  });
});
