import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';

import { acceptInvite, getInviteInfo, type InviteInfo } from '@/api/users';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { AuthLayout } from './AuthLayout';
import type { ProblemDetail } from '@/types/api';

const schema = z.object({
  fullName: z.string().min(1).max(255),
  password: z
    .string()
    .min(8)
    .max(128)
    .regex(/^(?=.*[A-Z])(?=.*\d).+$/, 'weak'),
});
type FormValues = z.infer<typeof schema>;

export default function InviteAcceptPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { token } = useParams<{ token: string }>();
  const [info, setInfo] = useState<InviteInfo | null>(null);
  const [invalid, setInvalid] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { fullName: '', password: '' },
  });

  useEffect(() => {
    if (!token) return;
    getInviteInfo(token)
      .then(setInfo)
      .catch(() => setInvalid(true));
  }, [token]);

  async function onSubmit(values: FormValues) {
    if (!token) return;
    try {
      await acceptInvite(token, values);
      toast.success(t('invite.accepted'));
      navigate('/login', { replace: true });
    } catch (err) {
      const detail = isAxiosError<ProblemDetail>(err) ? err.response?.data?.detail : undefined;
      toast.error(detail ?? t('invite.invalid'));
    }
  }

  return (
    <AuthLayout>
      <Card className="w-full max-w-md border-0 shadow-none sm:border sm:shadow-sm">
        {invalid ? (
          <CardContent className="p-6 text-destructive">{t('invite.invalid')}</CardContent>
        ) : !info ? (
          <CardContent className="p-6 text-muted-foreground">{t('common.loading')}</CardContent>
        ) : (
          <>
            <CardHeader>
              <CardTitle>{t('invite.title', { tenant: info.tenantName })}</CardTitle>
              <CardDescription>{t('invite.subtitle', { email: info.email })}</CardDescription>
            </CardHeader>
            <CardContent>
              <form className="space-y-4" onSubmit={(e) => void handleSubmit(onSubmit)(e)} noValidate>
                <div className="space-y-1.5">
                  <Label htmlFor="invite-name">{t('invite.fields.fullName')}</Label>
                  <Input id="invite-name" aria-invalid={!!errors.fullName} {...register('fullName')} />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="invite-password">{t('invite.fields.password')}</Label>
                  <Input
                    id="invite-password"
                    type="password"
                    autoComplete="new-password"
                    aria-invalid={!!errors.password}
                    {...register('password')}
                  />
                  <p className={errors.password ? 'text-xs text-destructive' : 'text-xs text-muted-foreground'}>
                    {t('invite.fields.passwordHint')}
                  </p>
                </div>
                <Button type="submit" className="w-full" disabled={isSubmitting}>
                  {isSubmitting ? t('invite.accepting') : t('invite.submit')}
                </Button>
              </form>
            </CardContent>
          </>
        )}
      </Card>
    </AuthLayout>
  );
}
