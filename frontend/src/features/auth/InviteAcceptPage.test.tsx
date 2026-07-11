import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import '@/i18n';

vi.mock('@/api/users', () => ({
  listMembers: vi.fn(),
  inviteMember: vi.fn(),
  changeMemberRole: vi.fn(),
  setMemberActive: vi.fn(),
  transferOwnership: vi.fn(),
  getInviteInfo: vi.fn(),
  acceptInvite: vi.fn(),
}));

import { acceptInvite, getInviteInfo } from '@/api/users';
import InviteAcceptPage from './InviteAcceptPage';

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/invite/tok-123']}>
      <Routes>
        <Route path="/invite/:token" element={<InviteAcceptPage />} />
        <Route path="/login" element={<div>login page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getInviteInfo).mockResolvedValue({
    email: 'new@acme.example',
    tenantName: 'Acme GmbH',
  });
  vi.mocked(acceptInvite).mockResolvedValue(undefined);
});

describe('InviteAcceptPage', () => {
  it('shows the tenant and invited email from the token lookup', async () => {
    renderPage();
    expect(await screen.findByText(/Acme GmbH/)).toBeInTheDocument();
    expect(screen.getByText(/new@acme\.example/)).toBeInTheDocument();
  });

  it('accepts the invite with name and password, then routes to login', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText(/Acme GmbH/);

    await user.type(screen.getByLabelText(/Full name|Vollständiger Name|Nom complet/), 'New Member');
    await user.type(screen.getByLabelText(/Password|Passwort|Mot de passe/), 'Sup3rSecret');
    await user.click(screen.getByRole('button', { name: /Accept|annehmen|Accepter/ }));

    await waitFor(() =>
      expect(acceptInvite).toHaveBeenCalledWith('tok-123', {
        fullName: 'New Member',
        password: 'Sup3rSecret',
      }),
    );
    expect(await screen.findByText('login page')).toBeInTheDocument();
  });

  it('shows an error state for an invalid token', async () => {
    vi.mocked(getInviteInfo).mockRejectedValue(new Error('404'));
    renderPage();
    expect(
      await screen.findByText(/invalid or has expired|ungültig|invalide/),
    ).toBeInTheDocument();
  });
});
