export type Role = 'OWNER' | 'ADMIN' | 'MEMBER';
export type AuthProvider = 'LOCAL' | 'GOOGLE' | 'GITHUB';

export interface User {
  id: string;
  tenantId: string;
  email: string;
  fullName: string;
  avatarUrl: string | null;
  role: Role;
  provider: AuthProvider;
  locale: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: User;
}

export interface ApiEnvelope<T> {
  data: T;
  meta?: Record<string, unknown>;
}

export interface ProblemDetail {
  type?: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  code?: string;
  details?: string[];
  timestamp?: string;
}
