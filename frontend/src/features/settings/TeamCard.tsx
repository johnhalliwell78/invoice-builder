import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { Crown, UserPlus } from 'lucide-react';

import {
  useChangeMemberRole,
  useInviteMember,
  useMembers,
  useSetMemberActive,
  useTransferOwnership,
} from '@/hooks/useUsers';
import type { Member } from '@/api/users';
import { useAuthStore } from '@/store/authStore';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Modal } from '@/components/Modal';
import { cn } from '@/lib/utils';
import type { ProblemDetail } from '@/types/api';

const inviteSchema = z.object({
  email: z.string().min(1).email(),
  role: z.enum(['ADMIN', 'MEMBER']),
});
type InviteForm = z.infer<typeof inviteSchema>;

export function TeamCard() {
  const { t } = useTranslation();
  const currentUser = useAuthStore((s) => s.user);
  const isOwner = currentUser?.role === 'OWNER';

  const members = useMembers(true);
  const invite = useInviteMember();
  const changeRole = useChangeMemberRole();
  const setActive = useSetMemberActive();
  const transfer = useTransferOwnership();
  const [inviteOpen, setInviteOpen] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<InviteForm>({
    resolver: zodResolver(inviteSchema),
    defaultValues: { email: '', role: 'MEMBER' },
  });

  function errorToast(err: unknown) {
    const detail = isAxiosError<ProblemDetail>(err) ? err.response?.data?.detail : undefined;
    toast.error(detail ?? t('auth.errors.default'));
  }

  async function onInvite(values: InviteForm) {
    try {
      await invite.mutateAsync(values);
      toast.success(t('team.invited'));
      reset();
      setInviteOpen(false);
    } catch (err) {
      errorToast(err);
    }
  }

  async function onRoleChange(member: Member, role: 'ADMIN' | 'MEMBER') {
    try {
      await changeRole.mutateAsync({ id: member.id, role });
      toast.success(t('team.roleChanged'));
    } catch (err) {
      errorToast(err);
    }
  }

  async function onToggleActive(member: Member) {
    try {
      await setActive.mutateAsync({ id: member.id, active: !member.active });
      toast.success(t('team.statusChanged'));
    } catch (err) {
      errorToast(err);
    }
  }

  async function onTransfer(member: Member) {
    if (!window.confirm(t('team.transferConfirm', { name: member.fullName }))) return;
    try {
      await transfer.mutateAsync(member.id);
      toast.success(t('team.ownershipTransferred'));
    } catch (err) {
      errorToast(err);
    }
  }

  return (
    <Card className="mt-6 max-w-4xl">
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <CardTitle>{t('team.title')}</CardTitle>
        <Button size="sm" onClick={() => setInviteOpen(true)}>
          <UserPlus className="mr-2 h-4 w-4" />
          {t('team.invite')}
        </Button>
      </CardHeader>
      <CardContent>
        <table className="w-full text-sm">
          <thead className="text-left text-xs uppercase text-muted-foreground">
            <tr>
              <th className="pb-2 font-medium">{t('team.columns.member')}</th>
              <th className="pb-2 font-medium">{t('team.columns.role')}</th>
              <th className="pb-2 font-medium">{t('team.columns.status')}</th>
              <th className="pb-2 text-right font-medium">{t('common.actions')}</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {members.data?.map((member) => {
              const isSelf = member.id === currentUser?.id;
              const isMemberOwner = member.role === 'OWNER';
              return (
                <tr key={member.id}>
                  <td className="py-2">
                    <div className="font-medium">
                      {member.fullName}
                      {isSelf && <span className="text-muted-foreground"> — {t('team.you')}</span>}
                    </div>
                    <div className="text-xs text-muted-foreground">{member.email}</div>
                  </td>
                  <td className="py-2">
                    {isMemberOwner ? (
                      <span className="inline-flex items-center gap-1 font-medium">
                        <Crown className="h-3.5 w-3.5 text-amber-500" />
                        {t('team.roles.OWNER')}
                      </span>
                    ) : (
                      <select
                        className="h-8 rounded-md border bg-background px-2 text-sm"
                        value={member.role}
                        disabled={isSelf}
                        aria-label={t('team.columns.role')}
                        onChange={(e) =>
                          void onRoleChange(member, e.target.value as 'ADMIN' | 'MEMBER')
                        }
                      >
                        <option value="ADMIN">{t('team.roles.ADMIN')}</option>
                        <option value="MEMBER">{t('team.roles.MEMBER')}</option>
                      </select>
                    )}
                  </td>
                  <td className="py-2">
                    <span
                      className={cn(
                        'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium',
                        member.pendingInvite
                          ? 'bg-amber-100 text-amber-900 dark:bg-amber-950 dark:text-amber-200'
                          : member.active
                            ? 'bg-emerald-100 text-emerald-900 dark:bg-emerald-950 dark:text-emerald-200'
                            : 'bg-muted text-muted-foreground',
                      )}
                    >
                      {member.pendingInvite
                        ? t('team.pending')
                        : member.active
                          ? t('team.active')
                          : t('team.inactive')}
                    </span>
                  </td>
                  <td className="py-2">
                    <div className="flex items-center justify-end gap-1">
                      {isOwner && !isMemberOwner && member.active && !member.pendingInvite && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => void onTransfer(member)}
                        >
                          <Crown className="mr-1 h-3.5 w-3.5" />
                          {t('team.makeOwner')}
                        </Button>
                      )}
                      {!isSelf && !isMemberOwner && !member.pendingInvite && (
                        <Button variant="ghost" size="sm" onClick={() => void onToggleActive(member)}>
                          {member.active ? t('team.deactivate') : t('team.activate')}
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </CardContent>

      <Modal
        open={inviteOpen}
        onClose={() => setInviteOpen(false)}
        title={t('team.inviteTitle')}
        description={t('team.inviteSubtitle')}
        footer={
          <>
            <Button variant="outline" onClick={() => setInviteOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" form="invite-form" disabled={isSubmitting}>
              <UserPlus className="mr-2 h-4 w-4" />
              {t('team.invite')}
            </Button>
          </>
        }
      >
        <form
          id="invite-form"
          className="space-y-4"
          onSubmit={(e) => void handleSubmit(onInvite)(e)}
          noValidate
        >
          <div className="space-y-1.5">
            <Label htmlFor="invite-email">{t('team.fields.email')} *</Label>
            <Input
              id="invite-email"
              type="email"
              aria-invalid={!!errors.email}
              placeholder="colleague@example.com"
              {...register('email')}
            />
            {errors.email && (
              <p className="text-xs text-destructive">{t('invoices.fields.invalidRecipient')}</p>
            )}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="invite-role">{t('team.fields.role')}</Label>
            <select
              id="invite-role"
              className="h-10 w-full rounded-md border bg-background px-3 text-sm"
              {...register('role')}
            >
              <option value="MEMBER">{t('team.roles.MEMBER')}</option>
              <option value="ADMIN">{t('team.roles.ADMIN')}</option>
            </select>
            <p className="text-xs text-muted-foreground">{t('team.fields.roleHint')}</p>
          </div>
        </form>
      </Modal>
    </Card>
  );
}
