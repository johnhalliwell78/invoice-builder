import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ReactNode } from 'react';
import type { UseFormRegisterReturn } from 'react-hook-form';

import '@/i18n';

vi.mock('@/api/products', () => ({
  listProducts: vi.fn(),
}));

import { listProducts, type Product } from '@/api/products';
import { ProductAutocompleteInput } from './ProductAutocompleteInput';

const consulting: Product = {
  id: 'p1',
  name: 'Consulting hour',
  description: null,
  unitPrice: '120.00',
  taxRate: '19.00',
  category: 'Services',
  active: true,
  createdAt: '2026-07-01T00:00:00Z',
  updatedAt: '2026-07-01T00:00:00Z',
};

function fakeRegistration(): UseFormRegisterReturn {
  return {
    name: 'lineItems.0.description',
    onChange: vi.fn(() => Promise.resolve()),
    onBlur: vi.fn(() => Promise.resolve()),
    ref: vi.fn(),
  };
}

function renderInput(onPick: (p: Product) => void) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
  return render(
    <ProductAutocompleteInput registration={fakeRegistration()} onPick={onPick} placeholder="What was delivered" />,
    { wrapper },
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(listProducts).mockResolvedValue({
    content: [consulting],
    page: 0,
    size: 8,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
  });
});

describe('ProductAutocompleteInput', () => {
  it('suggests matching products with their price while typing', async () => {
    const user = userEvent.setup();
    renderInput(vi.fn());

    await user.type(screen.getByPlaceholderText('What was delivered'), 'cons');

    expect(await screen.findByRole('option')).toHaveTextContent('Consulting hour');
    expect(screen.getByRole('option')).toHaveTextContent('120.00');
    await waitFor(() =>
      expect(listProducts).toHaveBeenCalledWith(
        expect.objectContaining({ q: 'cons', activeOnly: true }),
      ),
    );
  });

  it('hands the picked product to the parent and closes the list', async () => {
    const user = userEvent.setup();
    const onPick = vi.fn();
    renderInput(onPick);

    await user.type(screen.getByPlaceholderText('What was delivered'), 'cons');
    await user.click(await screen.findByRole('option'));

    expect(onPick).toHaveBeenCalledWith(consulting);
    expect(screen.queryByRole('option')).not.toBeInTheDocument();
  });
});
