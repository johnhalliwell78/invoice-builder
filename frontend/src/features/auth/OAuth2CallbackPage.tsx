import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';

import { getCurrentUser } from '@/api/auth';
import { useAuthStore } from '@/store/authStore';

/**
 * Lands here after the backend's OAuth2 success handler redirects with
 * #access_token=...&token_type=Bearer&expires_in=...
 * Reads the token, fetches the user profile, then navigates to /.
 */
export default function OAuth2CallbackPage() {
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const { t } = useTranslation();

  useEffect(() => {
    const fragment = window.location.hash.replace(/^#/, '');
    const params = new URLSearchParams(fragment);
    const accessToken = params.get('access_token');

    if (!accessToken) {
      toast.error(t('auth.errors.default'));
      navigate('/login', { replace: true });
      return;
    }

    // Stash the token so the request interceptor includes it on /me.
    useAuthStore.getState().setAccessToken(accessToken);
    history.replaceState(null, '', window.location.pathname);

    getCurrentUser()
      .then((user) => {
        setAuth(accessToken, user);
        navigate('/', { replace: true });
      })
      .catch(() => {
        clearAuth();
        toast.error(t('auth.errors.default'));
        navigate('/login', { replace: true });
      });
  }, [navigate, setAuth, clearAuth, t]);

  return (
    <div className="flex min-h-screen items-center justify-center text-muted-foreground">
      {t('common.loading')}
    </div>
  );
}
