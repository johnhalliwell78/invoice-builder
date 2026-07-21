import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createProduct,
  deleteProduct,
  getProduct,
  listProducts,
  updateProduct,
  type ProductListParams,
  type ProductPayload,
} from '@/api/products';

const KEY = ['products'] as const;

export function useProductList(params: ProductListParams) {
  return useQuery({
    queryKey: [...KEY, 'list', params],
    queryFn: () => listProducts(params),
    placeholderData: keepPreviousData,
  });
}

/** Active-only lookup for the invoice line-item autocomplete. */
export function useProductSearch(query: string) {
  return useQuery({
    queryKey: [...KEY, 'search', query],
    queryFn: () => listProducts({ q: query, activeOnly: true, size: 8, sort: 'name,asc' }),
    enabled: query.trim().length > 0,
    placeholderData: keepPreviousData,
  });
}

export function useProduct(id: string | undefined) {
  return useQuery({
    queryKey: [...KEY, 'detail', id],
    queryFn: () => getProduct(id as string),
    enabled: !!id,
  });
}

export function useCreateProduct() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: ProductPayload) => createProduct(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useUpdateProduct(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: ProductPayload) => updateProduct(id, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useDeleteProduct() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteProduct(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}
