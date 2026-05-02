import { create } from 'zustand';
import { immer } from 'zustand/middleware/immer';
import type { User } from '@/types/api';

interface AuthState {
  accessToken: string | null;
  user: User | null;
  isInitialized: boolean;
  setAuth: (token: string, user: User) => void;
  setAccessToken: (token: string) => void;
  setUser: (user: User) => void;
  clearAuth: () => void;
  markInitialized: () => void;
}

/**
 * In-memory auth state. The access token is intentionally NOT persisted —
 * page reloads trigger a silent /refresh to recover the session.
 */
export const useAuthStore = create<AuthState>()(
  immer((set) => ({
    accessToken: null,
    user: null,
    isInitialized: false,

    setAuth: (token, user) =>
      set((state) => {
        state.accessToken = token;
        state.user = user;
        state.isInitialized = true;
      }),

    setAccessToken: (token) =>
      set((state) => {
        state.accessToken = token;
      }),

    setUser: (user) =>
      set((state) => {
        state.user = user;
      }),

    clearAuth: () =>
      set((state) => {
        state.accessToken = null;
        state.user = null;
        state.isInitialized = true;
      }),

    markInitialized: () =>
      set((state) => {
        state.isInitialized = true;
      }),
  })),
);

export const selectIsAuthenticated = (s: AuthState) => s.accessToken !== null && s.user !== null;
