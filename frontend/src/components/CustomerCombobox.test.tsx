import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ReactNode } from 'react';

import '@/i18n';

vi.mock('@/api/customers', () => ({
  listCustomers: vi.fn(),
}));

import { listCustomers } from '@/api/customers';
import { CustomerCombobox } from './CustomerCombobox';
import type { Customer } from '@/types/api';

const acme = {
  id: 'c1',
  name: 'Acme GmbH',
  email: null,
  phone: null,
  company: 'Acme',
  address: null,
  taxId: null,
  notes: null,
  createdAt: '2026-07-01T00:00:00Z',
  updatedAt: '2026-07-01T00:00:00Z',
} as unknown as Customer;

const globex = { ...acme, id: 'c2', name: 'Globex Corp', company: null } as unknown as Customer;

function pageOf(content: Customer[], page: number, last: boolean) {
  return { content, page, size: 20, totalElements: 40, totalPages: 2, first: page === 0, last };
}

function renderBox(onSelect: (c: Customer | null) => void, selected?: { id: string; name: string }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
  return render(
    <CustomerCombobox
      selectedId={selected?.id ?? ''}
      selectedName={selected?.name}
      onSelect={onSelect}
      clearable
      placeholder="Select a customer"
    />,
    { wrapper },
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(listCustomers).mockResolvedValue(pageOf([acme, globex], 0, false));
});

describe('CustomerCombobox', () => {
  it('opens on focus and lists customers from the server', async () => {
    const user = userEvent.setup();
    renderBox(vi.fn());

    await user.click(screen.getByRole('combobox'));

    expect(await screen.findByRole('option', { name: /Acme GmbH/ })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: /Globex Corp/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Load more' })).toBeInTheDocument();
  });

  it('searches server-side and selects via click', async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    renderBox(onSelect);

    await user.type(screen.getByRole('combobox'), 'acm');
    await user.click(await screen.findByRole('option', { name: /Acme GmbH/ }));

    expect(onSelect).toHaveBeenCalledWith(acme);
  });

  it('selects the highlighted option with the keyboard', async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    renderBox(onSelect);

    await user.click(screen.getByRole('combobox'));
    await screen.findByRole('option', { name: /Acme GmbH/ });
    await user.keyboard('{ArrowDown}{Enter}');

    expect(onSelect).toHaveBeenCalledWith(globex);
  });

  it('clears the selection', async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    renderBox(onSelect, { id: 'c1', name: 'Acme GmbH' });

    expect(screen.getByRole('combobox')).toHaveValue('Acme GmbH');
    await user.click(screen.getByRole('button', { name: 'Clear' }));

    expect(onSelect).toHaveBeenCalledWith(null);
  });
});
