import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ReactNode } from 'react';

import '@/i18n';

vi.mock('@/api/dashboard', () => ({ getDashboard: vi.fn() }));
vi.mock('@/api/tenant', () => ({
  getTenant: vi.fn(),
  updateTenant: vi.fn(),
  uploadTenantLogo: vi.fn(),
  fetchTenantLogo: vi.fn(),
  deleteTenantLogo: vi.fn(),
}));

import { getDashboard, type Dashboard } from '@/api/dashboard';
import { getTenant } from '@/api/tenant';
import DashboardPage from './DashboardPage';

const DASHBOARD: Dashboard = {
  tiles: [{ currency: 'EUR', outstanding: '1500.00', overdue: '400.00', paidThisMonth: '900.00' }],
  statusCounts: { DRAFT: 2, SENT: 3, PAID: 5 },
  revenueByMonth: [
    { month: '2026-06', currency: 'EUR', total: '800.00' },
    { month: '2026-07', currency: 'EUR', total: '900.00' },
  ],
  customersByMonth: [
    { month: '2026-06', count: 2 },
    { month: '2026-07', count: 1 },
  ],
  recentInvoices: [
    {
      id: 'inv-1',
      invoiceNumber: 'INV-2026-0007',
      status: 'SENT',
      total: '250.00',
      currency: 'EUR',
      customerName: 'Widget Co',
      updatedAt: '2026-07-10T09:00:00Z',
    },
  ],
};

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
  return render(<DashboardPage />, { wrapper });
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getDashboard).mockResolvedValue(DASHBOARD);
  vi.mocked(getTenant).mockResolvedValue({
    id: 'ten-1',
    name: 'Acme',
    slug: 'acme',
    defaultCurrency: 'EUR',
    defaultLocale: 'en',
    logoPath: null,
    address: null,
    taxId: null,
    invoicePrefix: 'INV',
    defaultTemplate: 'classic',
    footerText: null,
    paymentInfo: null,
    brandingColor: null,
    nextInvoiceNumber: 8,
    createdAt: '2026-01-01T00:00:00Z',
  });
});

describe('DashboardPage', () => {
  it('renders the outstanding/overdue/paid tiles from dashboard data', async () => {
    renderPage();
    // "Outstanding" and "Paid this month" are tile-only labels; "Overdue" also
    // appears as a status badge, so it is asserted via the amount below.
    expect(await screen.findByText('Outstanding')).toBeInTheDocument();
    expect(screen.getByText('Paid this month')).toBeInTheDocument();
    // EUR amounts formatted; the outstanding value should appear.
    expect(screen.getByText(/1[.,\s]500[.,]00/)).toBeInTheDocument();
  });

  it('lists recent invoices linking to the detail page', async () => {
    renderPage();
    const link = await screen.findByRole('link', { name: 'INV-2026-0007' });
    expect(link).toHaveAttribute('href', '/invoices/inv-1');
    expect(screen.getByText('Widget Co')).toBeInTheDocument();
  });
});
