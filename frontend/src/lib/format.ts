export function formatCurrency(amount: string | number, currency: string, locale?: string) {
  const num = typeof amount === 'string' ? Number(amount) : amount;
  if (Number.isNaN(num)) return String(amount);
  return new Intl.NumberFormat(locale ?? undefined, {
    style: 'currency',
    currency,
  }).format(num);
}

export function formatDate(iso: string, locale?: string) {
  if (!iso) return '';
  const d = new Date(iso);
  return new Intl.DateTimeFormat(locale ?? undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  }).format(d);
}

export function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

export function addDaysIso(base: string, days: number): string {
  const d = new Date(base);
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}
