import { useQuery } from '@tanstack/react-query';
import { listEntityAudit } from '@/api/audit';

export function useEntityAudit(type: string, id: string | undefined) {
  return useQuery({
    queryKey: ['audit', type, id],
    queryFn: () => listEntityAudit(type, id as string),
    enabled: !!id,
  });
}
