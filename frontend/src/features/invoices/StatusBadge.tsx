import { useTranslation } from 'react-i18next';
import type { InvoiceStatus } from '@/types/api';
import { cn } from '@/lib/utils';

const STYLE: Record<InvoiceStatus, string> = {
  DRAFT: 'bg-muted text-muted-foreground',
  SENT: 'bg-blue-100 text-blue-900 dark:bg-blue-950 dark:text-blue-200',
  VIEWED: 'bg-indigo-100 text-indigo-900 dark:bg-indigo-950 dark:text-indigo-200',
  PAID: 'bg-emerald-100 text-emerald-900 dark:bg-emerald-950 dark:text-emerald-200',
  OVERDUE: 'bg-amber-100 text-amber-900 dark:bg-amber-950 dark:text-amber-200',
  CANCELLED: 'bg-rose-100 text-rose-900 dark:bg-rose-950 dark:text-rose-200',
  APPROVED: 'bg-emerald-100 text-emerald-900 dark:bg-emerald-950 dark:text-emerald-200',
  DECLINED: 'bg-rose-100 text-rose-900 dark:bg-rose-950 dark:text-rose-200',
};

export function StatusBadge({ status }: { status: InvoiceStatus }) {
  const { t } = useTranslation();
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium',
        STYLE[status],
      )}
    >
      {t(`invoices.status.${status}`)}
    </span>
  );
}
