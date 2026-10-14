export const API_PREFIX = '/api/v1';
export const CURRENCY = 'ZMW';

export interface ApiError {
  status: number;
  code: string;
  message: string;
  correlationId: string;
  fieldErrors?: Record<string, string>;
}

export interface PlatformInfo {
  name: string;
  mode: string;
  currency: string;
}

export function formatMoney(amount: string | number): string {
  const match = /^(-?)(\d+)(?:\.(\d{1,2}))?$/.exec(String(amount));
  if (!match) throw new Error('Expected a decimal amount with at most two fractional digits.');
  const integer = new Intl.NumberFormat('en-ZM').format(BigInt(match[2]));
  return `${match[1]}K${integer}.${(match[3] ?? '').padEnd(2, '0')}`;
}

export type Role = 'CUSTOMER' | 'ADMIN' | 'SUPPORT_AGENT' | 'COMPLIANCE_OFFICER';
export interface User {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  status: 'ACTIVE' | 'SUSPENDED';
  emailVerified: boolean;
  mfaEnabled: boolean;
}
export interface Session {
  accessToken: string;
  refreshToken: string;
  user: User;
  mfaRequired: false;
}
export interface MfaChallenge { mfaRequired: true; challenge: string }
export type LoginResult = Session | MfaChallenge;
export interface SessionStore {
  read(): Session | null;
  write(value: Session): void | Promise<void>;
  clear(): void | Promise<void>;
}

export { createApiClient, errorMessage } from './client';
