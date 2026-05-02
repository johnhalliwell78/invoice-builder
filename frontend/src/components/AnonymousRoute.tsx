import { Navigate, Outlet } from 'react-router-dom';
import { useAuthStore, selectIsAuthenticated } from '@/store/authStore';

/** Redirects authenticated users away from auth pages (login, register). */
export function AnonymousRoute() {
  const isAuthenticated = useAuthStore(selectIsAuthenticated);
  const isInitialized = useAuthStore((s) => s.isInitialized);

  if (isInitialized && isAuthenticated) {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}
