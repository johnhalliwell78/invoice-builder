import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  deleteTenantLogo,
  fetchTenantLogo,
  getTenant,
  updateTenant,
  uploadTenantLogo,
  type TenantUpdatePayload,
} from '@/api/tenant';

const KEY = ['tenant'] as const;

export function useTenant() {
  return useQuery({ queryKey: KEY, queryFn: getTenant });
}

export function useUpdateTenant() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: TenantUpdatePayload) => updateTenant(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useTenantLogo(enabled: boolean) {
  return useQuery({
    queryKey: [...KEY, 'logo'],
    queryFn: fetchTenantLogo,
    enabled,
    staleTime: 0,
  });
}

export function useUploadLogo() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => uploadTenantLogo(file),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useDeleteLogo() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: deleteTenantLogo,
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}
