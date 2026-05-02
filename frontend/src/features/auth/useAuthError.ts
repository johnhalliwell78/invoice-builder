import { isAxiosError } from 'axios';
import { useTranslation } from 'react-i18next';
import type { ProblemDetail } from '@/types/api';

export function useAuthErrorMessage() {
  const { t } = useTranslation();
  return (error: unknown): string => {
    if (isAxiosError<ProblemDetail>(error)) {
      const code = error.response?.data?.code;
      if (code) {
        const translated = t(`auth.errors.${code}`, { defaultValue: '' });
        if (translated) return translated;
      }
      if (error.response?.data?.detail) return error.response.data.detail;
    }
    return t('auth.errors.default');
  };
}
