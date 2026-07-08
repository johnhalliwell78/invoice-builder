import { useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { LogOut, LayoutDashboard, FileText, Users, Settings } from 'lucide-react';

import { logout as logoutApi } from '@/api/auth';
import { useAuthStore } from '@/store/authStore';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

export function AppShell() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const [signingOut, setSigningOut] = useState(false);

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
    { to: '/customers', label: t('nav.customers'), icon: Users },
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
          <select
            className="h-8 rounded-md border bg-background px-2 text-xs"
            value={i18n.language.slice(0, 2)}
            onChange={(e) => void i18n.changeLanguage(e.target.value)}
          >
            <option value="en">English</option>
            <option value="de">Deutsch</option>
            <option value="fr">Français</option>
          </select>
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
      <main className="p-8">
        <Outlet />
      </main>
    </div>
  );
}
