import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ReactNode } from 'react';

import '@/i18n';

vi.mock('@/api/notifications', () => ({
  listNotifications: vi.fn(),
  getUnreadCount: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}));

import {
  getUnreadCount,
  listNotifications,
  markNotificationRead,
} from '@/api/notifications';
import { NotificationBell } from './NotificationBell';

function renderBell() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
  return render(<NotificationBell />, { wrapper });
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getUnreadCount).mockResolvedValue(2);
  vi.mocked(markNotificationRead).mockResolvedValue(undefined);
  vi.mocked(listNotifications).mockResolvedValue({
    content: [
      {
        id: 'n1',
        type: 'INVOICE_PAID',
        title: 'Invoice paid',
        message: 'INV-2026-0007',
        referenceType: 'Invoice',
        referenceId: 'inv-7',
        read: false,
        createdAt: '2026-07-12T09:00:00Z',
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
  });
});

describe('NotificationBell', () => {
  it('shows the unread badge count', async () => {
    renderBell();
    expect(await screen.findByText('2')).toBeInTheDocument();
  });

  it('opens the dropdown and lists localized notifications', async () => {
    const user = userEvent.setup();
    renderBell();
    await screen.findByText('2');

    await user.click(screen.getByRole('button', { name: 'Notifications' }));

    expect(await screen.findByText('Invoice INV-2026-0007 was paid')).toBeInTheDocument();
  });

  it('marks a notification read when clicked', async () => {
    const user = userEvent.setup();
    renderBell();
    await screen.findByText('2');
    await user.click(screen.getByRole('button', { name: 'Notifications' }));
    const item = await screen.findByText('Invoice INV-2026-0007 was paid');

    await user.click(item);

    await waitFor(() => expect(markNotificationRead).toHaveBeenCalledWith('n1'));
  });
});
