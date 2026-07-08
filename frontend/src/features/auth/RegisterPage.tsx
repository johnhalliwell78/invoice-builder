import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';

import { register as registerApi } from '@/api/auth';
import { useAuthStore } from '@/store/authStore';
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
import { useAuthErrorMessage } from './useAuthError';

const registerSchema = z.object({
  fullName: z.string().min(1).max(255),
  email: z.string().min(1).email().max(255),
  password: z
    .string()
    .min(8)
    .max(128)
    .regex(/[A-Z]/, 'Must include an uppercase letter')
    .regex(/\d/, 'Must include a digit'),
  tenantName: z.string().min(1).max(255),
});
type RegisterForm = z.infer<typeof registerSchema>;

export default function RegisterPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);
  const errorMessage = useAuthErrorMessage();
  const [submitting, setSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterForm>({ resolver: zodResolver(registerSchema) });

  async function onSubmit(values: RegisterForm) {
    setSubmitting(true);
    try {
      const { accessToken, user } = await registerApi(values);
      setAuth(accessToken, user);
      navigate('/', { replace: true });
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout>
      <Card className="w-full max-w-md border-0 shadow-none sm:border sm:shadow-sm">
        <CardHeader>
          <CardTitle>{t('auth.register.title')}</CardTitle>
          <CardDescription>{t('auth.register.subtitle')}</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={(e) => void handleSubmit(onSubmit)(e)} noValidate>
            <div className="space-y-1.5">
              <Label htmlFor="fullName">{t('auth.register.fullName')}</Label>
              <Input
                id="fullName"
                autoComplete="name"
                aria-invalid={!!errors.fullName}
                {...register('fullName')}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="email">{t('auth.register.email')}</Label>
              <Input
                id="email"
                type="email"
                autoComplete="email"
                aria-invalid={!!errors.email}
                {...register('email')}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="password">{t('auth.register.password')}</Label>
              <Input
                id="password"
                type="password"
                autoComplete="new-password"
                aria-invalid={!!errors.password}
                {...register('password')}
              />
              <p className="text-xs text-muted-foreground">{t('auth.register.passwordHint')}</p>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="tenantName">{t('auth.register.tenantName')}</Label>
              <Input
                id="tenantName"
                autoComplete="organization"
                aria-invalid={!!errors.tenantName}
                {...register('tenantName')}
              />
            </div>
            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting ? t('auth.register.submitting') : t('auth.register.submit')}
            </Button>
          </form>

          <div className="mt-6 text-center text-sm">
            {t('auth.register.haveAccount')}{' '}
            <Link to="/login" className="font-medium text-primary underline-offset-4 hover:underline">
              {t('auth.register.login')}
            </Link>
          </div>
        </CardContent>
      </Card>
    </AuthLayout>
  );
}
