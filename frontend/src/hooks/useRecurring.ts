import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  deleteRecurring,
  listRecurring,
  makeRecurring,
  toggleRecurring,
  type MakeRecurringPayload,
} from '@/api/recurring';

const KEY = ['recurring'] as const;

export function useRecurringList(params: { page?: number; size?: number }) {
  return useQuery({
    queryKey: [...KEY, 'list', params],
    queryFn: () => listRecurring(params),
    placeholderData: keepPreviousData,
  });
}

export function useToggleRecurring() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => toggleRecurring(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useDeleteRecurring() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteRecurring(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useMakeRecurring(invoiceId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: MakeRecurringPayload) => makeRecurring(invoiceId, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}
