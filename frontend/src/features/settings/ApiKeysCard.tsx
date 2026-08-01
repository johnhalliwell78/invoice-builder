import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { Copy, KeyRound, Trash2 } from 'lucide-react';

import { createApiKey, listApiKeys, revokeApiKey, type ApiKey } from '@/api/apiKeys';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { formatDate } from '@/lib/format';
import type { ProblemDetail, Role } from '@/types/api';

const KEY = ['api-keys'] as const;
const ROLES: Role[] = ['ADMIN', 'MEMBER'];

export function ApiKeysCard() {
  const { t, i18n } = useTranslation();
  const qc = useQueryClient();
  const [name, setName] = useState('');
  const [role, setRole] = useState<Role>('MEMBER');
  /** Held only in memory: the server cannot show this secret again. */
  const [freshSecret, setFreshSecret] = useState<string | null>(null);

  const keys = useQuery({ queryKey: KEY, queryFn: listApiKeys });

  const create = useMutation({
    mutationFn: () => createApiKey({ name, role }),
    onSuccess: (created) => {
      setFreshSecret(created.secret);
      setName('');
      void qc.invalidateQueries({ queryKey: KEY });
    },
    onError: errorToast,
  });

  const revoke = useMutation({
    mutationFn: (id: string) => revokeApiKey(id),
    onSuccess: () => {
      toast.success(t('apiKeys.revoked'));
      void qc.invalidateQueries({ queryKey: KEY });
    },
    onError: errorToast,
  });

  function errorToast(err: unknown) {
    const detail = isAxiosError<ProblemDetail>(err) ? err.response?.data?.detail : undefined;
    toast.error(detail ?? t('auth.errors.default'));
  }

  function handleRevoke(key: ApiKey) {
    if (!window.confirm(t('apiKeys.revokeConfirm', { name: key.name }))) return;
    revoke.mutate(key.id);
  }

  async function copySecret() {
    if (!freshSecret) return;
    await navigator.clipboard.writeText(freshSecret);
    toast.success(t('apiKeys.copied'));
  }

  return (
    <Card className="mt-6 max-w-4xl">
      <CardHeader>
        <CardTitle>{t('apiKeys.title')}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-muted-foreground">{t('apiKeys.description')}</p>

        {freshSecret && (
          <div className="rounded-md border border-amber-500/50 bg-amber-50 p-3 dark:bg-amber-950/30">
            <p className="mb-2 text-sm font-medium">{t('apiKeys.shownOnce')}</p>
            <div className="flex items-center gap-2">
              <code className="flex-1 break-all rounded bg-background px-2 py-1 text-xs">
                {freshSecret}
              </code>
              <Button type="button" variant="outline" size="sm" onClick={() => void copySecret()}>
                <Copy className="mr-1 h-3 w-3" />
                {t('apiKeys.copy')}
              </Button>
              <Button type="button" variant="ghost" size="sm" onClick={() => setFreshSecret(null)}>
                {t('common.close')}
              </Button>
            </div>
          </div>
        )}

        <div className="flex flex-wrap items-end gap-2">
          <div className="space-y-1.5">
            <Label>{t('apiKeys.fields.name')}</Label>
            <Input
              value={name}
              maxLength={100}
              placeholder={t('apiKeys.fields.namePlaceholder')}
              onChange={(e) => setName(e.target.value)}
            />
          </div>
          <div className="space-y-1.5">
            <Label>{t('apiKeys.fields.role')}</Label>
            <select
              className="h-10 rounded-md border bg-background px-3 text-sm"
              value={role}
              onChange={(e) => setRole(e.target.value as Role)}
            >
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {t(`team.roles.${r}`)}
                </option>
              ))}
            </select>
          </div>
          <Button
            type="button"
            disabled={!name.trim() || create.isPending}
            onClick={() => create.mutate()}
          >
            <KeyRound className="mr-2 h-4 w-4" />
            {t('apiKeys.create')}
          </Button>
        </div>

        <table className="w-full text-sm">
          <thead className="text-left text-xs uppercase text-muted-foreground">
            <tr>
              <th className="pb-2 font-medium">{t('apiKeys.columns.name')}</th>
              <th className="pb-2 font-medium">{t('apiKeys.columns.key')}</th>
              <th className="pb-2 font-medium">{t('apiKeys.columns.lastUsed')}</th>
              <th className="pb-2 text-right font-medium">{t('common.actions')}</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {!keys.data?.length ? (
              <tr>
                <td colSpan={4} className="py-6 text-center text-muted-foreground">
                  {t('apiKeys.empty')}
                </td>
              </tr>
            ) : (
              keys.data.map((key) => (
                <tr key={key.id}>
                  <td className="py-2">
                    {key.name}
                    {key.revokedAt && (
                      <span className="ml-2 rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                        {t('apiKeys.revokedLabel')}
                      </span>
                    )}
                  </td>
                  <td className="py-2 font-mono text-xs text-muted-foreground">{key.keyPrefix}…</td>
                  <td className="py-2 text-muted-foreground">
                    {key.lastUsedAt ? formatDate(key.lastUsedAt, i18n.language) : t('apiKeys.never')}
                  </td>
                  <td className="py-2 text-right">
                    {!key.revokedAt && (
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label={t('apiKeys.revoke')}
                        onClick={() => handleRevoke(key)}
                      >
                        <Trash2 className="h-4 w-4 text-destructive" />
                      </Button>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </CardContent>
    </Card>
  );
}
