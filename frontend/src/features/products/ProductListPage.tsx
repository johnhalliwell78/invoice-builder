import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { Pencil, Plus, Search, Trash2 } from 'lucide-react';

import { useDeleteProduct, useProductList } from '@/hooks/useProducts';
import type { Product } from '@/api/products';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { PageHeader } from '@/components/PageHeader';
import type { ProblemDetail } from '@/types/api';

const PAGE_SIZE = 20;

export default function ProductListPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [page, setPage] = useState(0);

  useDebouncedEffect(() => setDebouncedSearch(search), 300, [search]);

  const { data, isPending, isFetching, error } = useProductList({
    q: debouncedSearch || undefined,
    page,
    size: PAGE_SIZE,
    sort: 'name,asc',
  });

  const deleteProduct = useDeleteProduct();

  async function handleDeactivate(p: Product) {
    if (!window.confirm(t('products.deactivateConfirm', { name: p.name }))) return;
    try {
      await deleteProduct.mutateAsync(p.id);
      toast.success(t('products.deactivated'));
    } catch (err) {
      const detail = isAxiosError<ProblemDetail>(err) ? err.response?.data?.detail : undefined;
      toast.error(detail ?? t('auth.errors.default'));
    }
  }

  return (
    <div>
      <PageHeader
        title={t('products.title')}
        description={t('products.subtitle')}
        actions={
          <Button onClick={() => navigate('/products/new')}>
            <Plus className="mr-2 h-4 w-4" />
            {t('products.create')}
          </Button>
        }
      />

      <div className="mb-4 flex max-w-md items-center gap-2">
        <Search className="h-4 w-4 text-muted-foreground" />
        <Input
          placeholder={t('products.searchPlaceholder')}
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
                <th className="px-4 py-3 font-medium">{t('products.columns.name')}</th>
                <th className="px-4 py-3 font-medium">{t('products.columns.category')}</th>
                <th className="px-4 py-3 text-right font-medium">{t('products.columns.unitPrice')}</th>
                <th className="px-4 py-3 text-right font-medium">{t('products.columns.taxRate')}</th>
                <th className="px-4 py-3 font-medium">{t('products.columns.status')}</th>
                <th className="px-4 py-3 text-right font-medium">{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {isPending ? (
                <tr>
                  <td colSpan={6} className="px-4 py-12 text-center text-muted-foreground">
                    {t('common.loading')}
                  </td>
                </tr>
              ) : data && data.totalElements === 0 ? (
                <tr>
                  <td colSpan={6} className="px-4 py-12 text-center text-muted-foreground">
                    {debouncedSearch ? t('products.noMatches') : t('products.empty')}
                  </td>
                </tr>
              ) : (
                data?.content.map((p) => (
                  <tr key={p.id} className="hover:bg-muted/30">
                    <td className="px-4 py-3 font-medium">
                      <Link to={`/products/${p.id}`} className="hover:underline">
                        {p.name}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">{p.category ?? '—'}</td>
                    <td className="px-4 py-3 text-right tabular-nums">{Number(p.unitPrice).toFixed(2)}</td>
                    <td className="px-4 py-3 text-right tabular-nums">{Number(p.taxRate).toFixed(2)}%</td>
                    <td className="px-4 py-3">
                      <span
                        className={
                          p.active
                            ? 'rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-700'
                            : 'rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground'
                        }
                      >
                        {p.active ? t('products.status.active') : t('products.status.inactive')}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => navigate(`/products/${p.id}`)}
                          aria-label={t('common.edit')}
                        >
                          <Pencil className="h-4 w-4" />
                        </Button>
                        {p.active && (
                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={() => void handleDeactivate(p)}
                            aria-label={t('common.delete')}
                          >
                            <Trash2 className="h-4 w-4 text-destructive" />
                          </Button>
                        )}
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

function useDebouncedEffect(effect: () => void, delay: number, deps: unknown[]) {
  useEffect(() => {
    const handle = setTimeout(effect, delay);
    return () => clearTimeout(handle);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
}
