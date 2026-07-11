import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  changeMemberRole,
  inviteMember,
  listMembers,
  setMemberActive,
  transferOwnership,
} from '@/api/users';

const KEY = ['members'] as const;

export function useMembers(enabled: boolean) {
  return useQuery({ queryKey: KEY, queryFn: listMembers, enabled });
}

export function useInviteMember() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: { email: string; role: 'ADMIN' | 'MEMBER' }) => inviteMember(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useChangeMemberRole() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { id: string; role: 'ADMIN' | 'MEMBER' }) =>
      changeMemberRole(args.id, args.role),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useSetMemberActive() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { id: string; active: boolean }) => setMemberActive(args.id, args.active),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useTransferOwnership() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (targetUserId: string) => transferOwnership(targetUserId),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}
