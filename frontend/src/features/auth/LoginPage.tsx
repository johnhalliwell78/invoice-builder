import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';

import { login as loginApi, oauth2AuthorizeUrl } from '@/api/auth';
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

const loginSchema = z.object({
  email: z.string().min(1).email(),
  password: z.string().min(1),
});
type LoginForm = z.infer<typeof loginSchema>;

export default function LoginPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const setAuth = useAuthStore((s) => s.setAuth);
  const errorMessage = useAuthErrorMessage();
  const [submitting, setSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginForm>({ resolver: zodResolver(loginSchema) });

  async function onSubmit(values: LoginForm) {
    setSubmitting(true);
    try {
      const { accessToken, user } = await loginApi(values);
      setAuth(accessToken, user);
      const dest = (location.state as { from?: { pathname: string } } | null)?.from?.pathname ?? '/';
      navigate(dest, { replace: true });
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
          <CardTitle>{t('auth.login.title')}</CardTitle>
          <CardDescription>{t('auth.login.subtitle')}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <form className="space-y-4" onSubmit={handleSubmit(onSubmit)} noValidate>
            <div className="space-y-1.5">
              <Label htmlFor="email">{t('auth.login.email')}</Label>
              <Input
                id="email"
                type="email"
                autoComplete="email"
                aria-invalid={!!errors.email}
                {...register('email')}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="password">{t('auth.login.password')}</Label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                aria-invalid={!!errors.password}
                {...register('password')}
              />
            </div>
            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting ? t('auth.login.submitting') : t('auth.login.submit')}
            </Button>
          </form>

          <div className="relative">
            <div className="absolute inset-0 flex items-center">
              <span className="w-full border-t" />
            </div>
            <div className="relative flex justify-center text-xs uppercase">
              <span className="bg-card px-2 text-muted-foreground">{t('auth.login.or')}</span>
            </div>
          </div>

          <div className="grid gap-2">
            <Button variant="outline" onClick={() => (window.location.href = oauth2AuthorizeUrl('google'))}>
              {t('auth.login.googleButton')}
            </Button>
            <Button variant="outline" onClick={() => (window.location.href = oauth2AuthorizeUrl('github'))}>
              {t('auth.login.githubButton')}
            </Button>
          </div>

          <div className="text-center text-sm">
            {t('auth.login.noAccount')}{' '}
            <Link to="/register" className="font-medium text-primary underline-offset-4 hover:underline">
              {t('auth.login.register')}
            </Link>
          </div>
        </CardContent>
      </Card>
    </AuthLayout>
  );
}
