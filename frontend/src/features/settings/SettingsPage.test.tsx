import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ReactNode } from 'react';

import '@/i18n';

vi.mock('@/api/tenant', () => ({
  getTenant: vi.fn(),
  updateTenant: vi.fn(),
  uploadTenantLogo: vi.fn(),
  fetchTenantLogo: vi.fn(),
  deleteTenantLogo: vi.fn(),
}));

import { getTenant, updateTenant, type Tenant } from '@/api/tenant';
import SettingsPage from './SettingsPage';

const TENANT: Tenant = {
  id: 'ten-1',
  name: 'Acme GmbH',
  slug: 'acme-gmbh',
  defaultCurrency: 'EUR',
  defaultLocale: 'de',
  logoPath: null,
  address: { street: 'Hauptstr. 1', city: 'Berlin', zip: '10115', country: 'Germany' },
  taxId: 'DE123456789',
  invoicePrefix: 'ACME',
  defaultTemplate: 'modern',
  footerText: null,
  paymentInfo: 'IBAN DE89 3704 0044 0532 0130 00',
  brandingColor: '#FF5733',
  nextInvoiceNumber: 7,
  createdAt: '2026-01-01T00:00:00Z',
};

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
  return render(<SettingsPage />, { wrapper });
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getTenant).mockResolvedValue(TENANT);
  vi.mocked(updateTenant).mockResolvedValue(TENANT);
});

describe('SettingsPage', () => {
  it('prefills the form from the tenant', async () => {
    renderPage();
    expect(await screen.findByDisplayValue('Acme GmbH')).toBeInTheDocument();
    expect(screen.getByDisplayValue('ACME')).toBeInTheDocument();
    expect(screen.getByDisplayValue('IBAN DE89 3704 0044 0532 0130 00')).toBeInTheDocument();
    expect(screen.getByLabelText(/Standard-PDF-Vorlage|Default PDF template|Modèle PDF/)).toHaveValue('modern');
  });

  it('submits the updated payload', async () => {
    const user = userEvent.setup();
    renderPage();
    const nameInput = await screen.findByDisplayValue('Acme GmbH');

    await user.clear(nameInput);
    await user.type(nameInput, 'Acme Global GmbH');
    await user.click(screen.getByRole('button', { name: /Save|Speichern|Enregistrer/ }));

    await waitFor(() =>
      expect(updateTenant).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'Acme Global GmbH',
          defaultCurrency: 'EUR',
          defaultTemplate: 'modern',
          brandingColor: '#FF5733',
        }),
      ),
    );
  });
});
