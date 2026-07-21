import {
  keepPreviousData,
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import {
  createCustomer,
  deleteCustomer,
  getCustomer,
  listCustomers,
  updateCustomer,
  type CustomerListParams,
  type CustomerPayload,
} from '@/api/customers';

const KEY = ['customers'] as const;

export function useCustomerList(params: CustomerListParams) {
  return useQuery({
    queryKey: [...KEY, 'list', params],
    queryFn: () => listCustomers(params),
    placeholderData: keepPreviousData,
  });
}

/** Paged customer search for the combobox — first page on open, more via "load more". */
export function useCustomerSearchInfinite(query: string, enabled: boolean) {
  return useInfiniteQuery({
    queryKey: [...KEY, 'combobox', query],
    queryFn: ({ pageParam }) =>
      listCustomers({ q: query || undefined, page: pageParam, size: 20, sort: 'name,asc' }),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.last ? undefined : last.page + 1),
    enabled,
    placeholderData: keepPreviousData,
  });
}

export function useCustomer(id: string | undefined) {
  return useQuery({
    queryKey: [...KEY, 'detail', id],
    queryFn: () => getCustomer(id as string),
    enabled: !!id,
  });
}

export function useCreateCustomer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CustomerPayload) => createCustomer(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useUpdateCustomer(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CustomerPayload) => updateCustomer(id, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useDeleteCustomer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteCustomer(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}
