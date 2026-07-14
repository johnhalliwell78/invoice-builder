import i18n from 'i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import { initReactI18next } from 'react-i18next';

import en from './locales/en.json';
import de from './locales/de.json';
import fr from './locales/fr.json';

export const SUPPORTED_LOCALES = ['en', 'de', 'fr'] as const;
export type Locale = (typeof SUPPORTED_LOCALES)[number];

void i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      en: { translation: en },
      de: { translation: de },
      fr: { translation: fr },
    },
    fallbackLng: 'en',
    supportedLngs: [...SUPPORTED_LOCALES],
    interpolation: { escapeValue: false },
    detection: {
      // English is the default — only a locale the user explicitly picked (and
      // that we persisted) overrides it. Browser language is intentionally not
      // consulted, so first-time visitors always start in English.
      order: ['localStorage'],
      caches: ['localStorage'],
      lookupLocalStorage: 'ib_locale',
    },
  });

export default i18n;
