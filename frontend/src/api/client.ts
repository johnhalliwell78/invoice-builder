import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { useAuthStore } from '@/store/authStore';
import type { ApiEnvelope, AuthResponse, ProblemDetail } from '@/types/api';

const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';

interface RetriableConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

export const api = axios.create({
  baseURL: API_URL,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
});

// ---- Request interceptor: attach Bearer ----
api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// ---- Response interceptor: refresh-on-401 with single-flight queueing ----
let refreshPromise: Promise<string> | null = null;

function refreshAccessToken(): Promise<string> {
  if (!refreshPromise) {
    refreshPromise = axios
      .post<ApiEnvelope<AuthResponse>>(
        '/api/v1/auth/refresh',
        {},
        { baseURL: API_URL, withCredentials: true },
      )
      .then((res) => {
        const { accessToken, user } = res.data.data;
        useAuthStore.getState().setAuth(accessToken, user);
        return accessToken;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ProblemDetail>) => {
    const original = error.config as RetriableConfig | undefined;
    const status = error.response?.status;

    const isAuthEndpoint = original?.url?.includes('/api/v1/auth/');
    if (status === 401 && original && !original._retry && !isAuthEndpoint) {
      original._retry = true;
      try {
        const newToken = await refreshAccessToken();
        if (original.headers) {
          original.headers.Authorization = `Bearer ${newToken}`;
        }
        return api(original);
      } catch {
        useAuthStore.getState().clearAuth();
        return Promise.reject(error);
      }
    }
    return Promise.reject(error);
  },
);

/** Boot-time silent refresh — populates the store from the refresh cookie. */
export async function bootstrapAuth(): Promise<void> {
  try {
    await refreshAccessToken();
  } catch {
    // No valid refresh cookie — user stays anonymous.
  } finally {
    useAuthStore.getState().markInitialized();
  }
}
