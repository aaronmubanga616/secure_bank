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
export interface MfaChallenge {
  mfaRequired: true;
  challenge: string;
}
export type LoginResult = Session | MfaChallenge;
export interface SessionStore {
  read(): Session | null;
  write(value: Session): void | Promise<void>;
  clear(): void | Promise<void>;
}

export { createApiClient, errorMessage } from './client';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
}
export interface Account {
  id: string;
  accountNumber: string;
  type: 'SAVINGS' | 'CURRENT';
  status: string;
  currency: string;
  balance: string;
  createdAt: string;
}
export interface Transaction {
  id: string;
  reference: string;
  type: string;
  amount: string;
  currency: string;
  sourceAccount: string | null;
  destinationAccount: string | null;
  sourceAccountNumber: string | null;
  destinationAccountNumber: string | null;
  status: string;
  description: string;
  direction: string;
  riskScore: number;
  failureReason: string | null;
  createdAt: string;
  completedAt: string | null;
}
export interface Beneficiary {
  id: string;
  name: string;
  fullName: string;
  accountNumber: string;
  status: string;
  createdAt: string;
}
export interface Schedule {
  id: string;
  sourceAccount: string;
  beneficiaryId: string;
  beneficiaryName: string;
  accountNumber: string;
  amount: string;
  description: string;
  frequency: string;
  nextRunAt: string;
  status: string;
  lastResult: string | null;
  lastError: string | null;
}
export interface Notification {
  id: string;
  title: string;
  message: string;
  category: string;
  read: boolean;
  createdAt: string;
}
export function sumMoney(amounts: string[]): string {
  const cents = amounts.reduce((sum, amount) => {
    const match = /^(-?)(\d+)\.(\d{2})$/.exec(amount);
    if (!match) throw new Error('Expected two decimal places.');
    return sum + (match[1] ? -1n : 1n) * (BigInt(match[2]) * 100n + BigInt(match[3]));
  }, 0n);
  const absolute = cents < 0n ? -cents : cents;
  return `${cents < 0n ? '-' : ''}${absolute / 100n}.${String(absolute % 100n).padStart(2, '0')}`;
}
