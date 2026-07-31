import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ReactNode } from 'react';

import '@/i18n';

vi.mock('@/api/reports', () => ({
  getTaxSummary: vi.fn(),
  downloadCsv: vi.fn(),
}));

import { downloadCsv, getTaxSummary } from '@/api/reports';
import ReportsPage from './ReportsPage';

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
  return render(<ReportsPage />, { wrapper });
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getTaxSummary).mockResolvedValue([
    { currency: 'EUR', taxRate: '19.00', net: '1000.00', tax: '190.00', gross: '1190.00' },
    { currency: 'EUR', taxRate: '7.00', net: '100.00', tax: '7.00', gross: '107.00' },
  ]);
  vi.mocked(downloadCsv).mockResolvedValue(undefined);
});

describe('ReportsPage', () => {
  it('breaks the period down by tax rate', async () => {
    renderPage();

    expect(await screen.findByText('19.00%')).toBeInTheDocument();
    expect(screen.getByText('7.00%')).toBeInTheDocument();
  });

  it('downloads the invoice export for the selected period', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('19.00%');

    await user.click(screen.getByRole('button', { name: /Export invoices/i }));

    await waitFor(() =>
      expect(downloadCsv).toHaveBeenCalledWith(
        '/api/v1/exports/invoices.csv',
        expect.objectContaining({ from: expect.any(String) as string, to: expect.any(String) as string }),
        'invoices.csv',
      ),
    );
  });

  it('says so plainly when nothing was issued in the period', async () => {
    vi.mocked(getTaxSummary).mockResolvedValue([]);
    renderPage();

    expect(await screen.findByText(/Nothing issued in this period/i)).toBeInTheDocument();
  });
});
