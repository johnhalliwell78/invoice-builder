import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { LanguageSwitcher } from '@/components/LanguageSwitcher';

export function AuthLayout({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  return (
    <div className="grid min-h-screen w-full lg:grid-cols-2">
      <div className="hidden lg:flex flex-col justify-between bg-primary p-10 text-primary-foreground">
        <div className="text-2xl font-semibold tracking-tight">{t('app.name')}</div>
        <p className="text-lg leading-relaxed text-primary-foreground/80">{t('app.tagline')}</p>
      </div>
      <div className="relative flex items-center justify-center p-6 sm:p-10">
        <div className="absolute right-4 top-4">
          <LanguageSwitcher />
        </div>
        {children}
      </div>
    </div>
  );
}
