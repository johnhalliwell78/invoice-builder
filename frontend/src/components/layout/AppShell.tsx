import { useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { LogOut, LayoutDashboard, FileText, FileClock, Package, Users, Settings } from 'lucide-react';

import { logout as logoutApi } from '@/api/auth';
import { useAuthStore } from '@/store/authStore';
import { useNotificationSocket } from '@/hooks/useNotificationSocket';
import { Button } from '@/components/ui/button';
import { LanguageSwitcher } from '@/components/LanguageSwitcher';
import { NotificationBell } from '@/components/NotificationBell';
import { cn } from '@/lib/utils';

export function AppShell() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const [signingOut, setSigningOut] = useState(false);
  useNotificationSocket();

  async function handleLogout() {
    setSigningOut(true);
    try {
      await logoutApi();
    } finally {
      clearAuth();
      navigate('/login', { replace: true });
    }
  }

  const navItems = [
    { to: '/', label: t('nav.dashboard'), icon: LayoutDashboard, end: true },
    { to: '/invoices', label: t('nav.invoices'), icon: FileText },
    { to: '/estimates', label: t('nav.estimates'), icon: FileClock },
    { to: '/customers', label: t('nav.customers'), icon: Users },
    { to: '/products', label: t('nav.products'), icon: Package },
    { to: '/settings', label: t('nav.settings'), icon: Settings },
  ];

  return (
    <div className="grid min-h-screen grid-cols-[16rem_1fr] bg-muted/30">
      <aside className="flex flex-col gap-6 border-r bg-background p-4">
        <Link to="/" className="px-2 text-lg font-semibold tracking-tight">
          {t('app.name')}
        </Link>
        <nav className="flex flex-col gap-1">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-primary text-primary-foreground'
                    : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground',
                )
              }
            >
              <item.icon className="h-4 w-4" />
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="mt-auto flex flex-col gap-2 px-2">
          <LanguageSwitcher />
          {user && (
            <div className="rounded-md border bg-card p-2 text-xs">
              <div className="font-medium">{user.fullName}</div>
              <div className="truncate text-muted-foreground">{user.email}</div>
            </div>
          )}
          <Button variant="ghost" size="sm" onClick={() => void handleLogout()} disabled={signingOut}>
            <LogOut className="mr-2 h-4 w-4" />
            {t('nav.logout')}
          </Button>
        </div>
      </aside>
      <div className="flex flex-col">
        <header className="flex items-center justify-end border-b bg-background px-8 py-3">
          <NotificationBell />
        </header>
        <main className="p-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
