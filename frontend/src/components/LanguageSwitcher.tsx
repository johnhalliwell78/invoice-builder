import { useTranslation } from 'react-i18next';
import { SUPPORTED_LOCALES, type Locale } from '@/i18n';
import { cn } from '@/lib/utils';

const LABELS: Record<Locale, string> = {
  en: 'English',
  de: 'Deutsch',
  fr: 'Français',
};

/** Locale selector used in the app shell and on the auth screens. */
export function LanguageSwitcher({ className }: { className?: string }) {
  const { t, i18n } = useTranslation();
  const current = i18n.language.slice(0, 2);

  return (
    <select
      aria-label={t('common.language')}
      className={cn('h-8 rounded-md border bg-background px-2 text-xs', className)}
      value={SUPPORTED_LOCALES.includes(current as Locale) ? current : 'en'}
      onChange={(e) => void i18n.changeLanguage(e.target.value)}
    >
      {SUPPORTED_LOCALES.map((loc) => (
        <option key={loc} value={loc}>
          {LABELS[loc]}
        </option>
      ))}
    </select>
  );
}
