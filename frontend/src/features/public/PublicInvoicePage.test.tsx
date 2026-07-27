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
  window.sessionStorage.clear();
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

  it('suppresses the pay button after returning from Stripe so a second click cannot double-charge', async () => {
    // A payment is in flight for this invoice (recorded when Pay was clicked).
    window.sessionStorage.setItem(
      'ib_pay_pending_tok123',
      JSON.stringify({ amountPaidBefore: '19.00' }),
    );
    renderPage('/i/tok123?payment=success');

    // The webhook lands a second or two after the redirect, so the invoice
    // still reads as unpaid. Re-offering "Pay" here is exactly how a shopper
    // gets charged twice — the button must stay hidden while we confirm.
    await screen.findByText(/Confirming your payment/i);
    expect(screen.queryByRole('button', { name: /Pay/i })).not.toBeInTheDocument();
  });

  it('keeps waiting when the invoice already had an earlier partial payment', async () => {
    // The invoice fixture already carries amountPaid 19.00. A guard that only
    // asked "is amountPaid > 0" would call this settled on the first poll and
    // re-arm the button ~1.5s after the shopper got back — a double charge.
    vi.useFakeTimers();
    try {
      window.sessionStorage.setItem(
        'ib_pay_pending_tok123',
        JSON.stringify({ amountPaidBefore: '19.00' }),
      );
      renderPage('/i/tok123?payment=success');
      await vi.advanceTimersByTimeAsync(6000);

      expect(screen.queryByRole('button', { name: /Pay/i })).not.toBeInTheDocument();
      expect(window.sessionStorage.getItem('ib_pay_pending_tok123')).not.toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });

  it('resumes the confirming state after a page reload instead of re-offering Pay', async () => {
    // No ?payment= param: this is a fresh load, as after a refresh. The
    // in-flight marker must still suppress the button.
    window.sessionStorage.setItem(
      'ib_pay_pending_tok123',
      JSON.stringify({ amountPaidBefore: '19.00' }),
    );
    renderPage('/i/tok123');

    await screen.findByText(/Confirming your payment/i);
    expect(screen.queryByRole('button', { name: /Pay/i })).not.toBeInTheDocument();
  });

  it('clears the in-flight marker and shows Pay again once the ledger confirms', async () => {
    vi.useFakeTimers();
    try {
      window.sessionStorage.setItem(
        'ib_pay_pending_tok123',
        JSON.stringify({ amountPaidBefore: '19.00' }),
      );
      // The webhook has landed: more is paid than before the redirect.
      vi.mocked(getPublicInvoice).mockResolvedValue({ ...invoice, amountPaid: '119.00', total: '119.00' });
      renderPage('/i/tok123');
      await vi.advanceTimersByTimeAsync(4000);

      expect(window.sessionStorage.getItem('ib_pay_pending_tok123')).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });
});
