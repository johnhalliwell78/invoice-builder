import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { Pencil, Plus, Search, Trash2 } from 'lucide-react';

import { useCustomerList, useDeleteCustomer } from '@/hooks/useCustomers';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { PageHeader } from '@/components/PageHeader';
import type { Customer, ProblemDetail } from '@/types/api';

const PAGE_SIZE = 20;

export default function CustomerListPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [page, setPage] = useState(0);

  // simple debounce: defer query update by 300ms after typing stops
  useDebouncedEffect(() => setDebouncedSearch(search), 300, [search]);

  const { data, isPending, isFetching, error } = useCustomerList({
    q: debouncedSearch || undefined,
    page,
    size: PAGE_SIZE,
    sort: 'name,asc',
  });

  const deleteCustomer = useDeleteCustomer();

  async function handleDelete(c: Customer) {
    if (!window.confirm(t('customers.deleteConfirm', { name: c.name }))) return;
    try {
      await deleteCustomer.mutateAsync(c.id);
      toast.success(t('customers.deleted'));
    } catch (err) {
      const detail = isAxiosError<ProblemDetail>(err) ? err.response?.data?.detail : undefined;
      toast.error(detail ?? t('auth.errors.default'));
    }
  }

  return (
    <div>
      <PageHeader
        title={t('customers.title')}
        description={t('customers.subtitle')}
        actions={
          <Button onClick={() => navigate('/customers/new')}>
            <Plus className="mr-2 h-4 w-4" />
            {t('customers.create')}
          </Button>
        }
      />

      <div className="mb-4 flex max-w-md items-center gap-2">
        <Search className="h-4 w-4 text-muted-foreground" />
        <Input
          placeholder={t('customers.searchPlaceholder')}
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
        />
      </div>

      {error ? (
        <div className="rounded-md border border-destructive/50 bg-destructive/5 p-4 text-sm text-destructive">
          {t('common.loadFailed')}
        </div>
      ) : (
        <div className="overflow-hidden rounded-lg border bg-card">
          <table className="w-full text-sm">
            <thead className="bg-muted/50 text-left text-xs uppercase tracking-wider text-muted-foreground">
              <tr>
                <th className="px-4 py-3 font-medium">{t('customers.columns.name')}</th>
                <th className="px-4 py-3 font-medium">{t('customers.columns.company')}</th>
                <th className="px-4 py-3 font-medium">{t('customers.columns.email')}</th>
                <th className="px-4 py-3 font-medium">{t('customers.columns.phone')}</th>
                <th className="px-4 py-3 text-right font-medium">{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {isPending ? (
                <tr>
                  <td colSpan={5} className="px-4 py-12 text-center text-muted-foreground">
                    {t('common.loading')}
                  </td>
                </tr>
              ) : data && data.totalElements === 0 ? (
                <tr>
                  <td colSpan={5} className="px-4 py-12 text-center text-muted-foreground">
                    {debouncedSearch ? t('customers.noMatches') : t('customers.empty')}
                  </td>
                </tr>
              ) : (
                data?.content.map((c) => (
                  <tr key={c.id} className="hover:bg-muted/30">
                    <td className="px-4 py-3 font-medium">
                      <Link to={`/customers/${c.id}`} className="hover:underline">
                        {c.name}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">{c.company ?? '—'}</td>
                    <td className="px-4 py-3 text-muted-foreground">{c.email ?? '—'}</td>
                    <td className="px-4 py-3 text-muted-foreground">{c.phone ?? '—'}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => navigate(`/customers/${c.id}`)}
                          aria-label={t('common.edit')}
                        >
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => void handleDelete(c)}
                          aria-label={t('common.delete')}
                        >
                          <Trash2 className="h-4 w-4 text-destructive" />
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {data && data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm">
          <div className="text-muted-foreground">
            {t('common.pageOf', { current: data.page + 1, total: data.totalPages })}
            {isFetching && ' · ' + t('common.loading')}
          </div>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={data.first}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              {t('common.previous')}
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={data.last}
              onClick={() => setPage((p) => p + 1)}
            >
              {t('common.next')}
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}

import { useEffect } from 'react';

function useDebouncedEffect(effect: () => void, delay: number, deps: unknown[]) {
  useEffect(() => {
    const handle = setTimeout(effect, delay);
    return () => clearTimeout(handle);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
}
