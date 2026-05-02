import { useTranslation } from 'react-i18next';
import { useAuthStore } from '@/store/authStore';

export default function DashboardPage() {
  const { t } = useTranslation();
  const user = useAuthStore((s) => s.user);
  if (!user) return null;
  return (
    <div className="space-y-2">
      <h1 className="text-3xl font-semibold tracking-tight">
        {t('dashboard.welcome', { name: user.fullName })}
      </h1>
      <p className="text-muted-foreground">
        {t('dashboard.greeting', { role: user.role, tenant: user.tenantId })}
      </p>
    </div>
  );
}
