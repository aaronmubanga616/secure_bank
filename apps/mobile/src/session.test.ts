import { beforeEach, expect, test, vi } from 'vitest';
import type { Session } from '@securebank/contracts';
import * as SecureStore from 'expo-secure-store';
const mocks = vi.hoisted(() => ({ storage: new Map<string, string>(), writes: vi.fn() }));
vi.mock('expo-secure-store', () => ({
  WHEN_UNLOCKED_THIS_DEVICE_ONLY: 1,
  canUseBiometricAuthentication: () => true,
  getItemAsync: vi.fn(async (key: string) => mocks.storage.get(key) ?? null),
  setItemAsync: vi.fn(async (key: string, value: string) => {
    mocks.writes(key, value);
    mocks.storage.set(key, value);
  }),
  deleteItemAsync: vi.fn(async (key: string) => {
    mocks.storage.delete(key);
  }),
}));
vi.mock('expo-local-authentication', () => ({
  hasHardwareAsync: async () => true,
  isEnrolledAsync: async () => true,
}));
vi.mock('expo-crypto', () => ({ randomUUID: () => 'test-device-1234567890' }));
import {
  api,
  sessionStore,
  enableBiometrics,
  lockSession,
  signOut,
  unlockSession,
} from './session';
const session: Session = {
  accessToken: 'memory-access-token',
  refreshToken: 'secure-refresh-token',
  mfaRequired: false,
  user: {
    id: 'test-user',
    fullName: 'Maya Banda',
    email: 'maya@example.test',
    role: 'CUSTOMER',
    status: 'ACTIVE',
    emailVerified: true,
    mfaEnabled: false,
  },
};
beforeEach(async () => {
  vi.spyOn(api, 'post').mockResolvedValue({ data: session });
  await signOut();
  mocks.writes.mockClear();
});
test('only the refresh token is persisted and a locked session cannot authorize requests', async () => {
  await sessionStore.write(session);
  expect(mocks.writes).toHaveBeenCalledWith('securebank-refresh', 'secure-refresh-token');
  expect(JSON.stringify([...mocks.storage])).not.toContain('memory-access-token');
  await enableBiometrics();
  expect(mocks.storage.has('securebank-refresh')).toBe(false);
  lockSession();
  expect(sessionStore.read()).toBeNull();
  await unlockSession();
  expect(sessionStore.read()?.accessToken).toBe('memory-access-token');
});
test('logging out while device unlock is refreshing cannot restore the session', async () => {
  await sessionStore.write(session);
  await enableBiometrics();
  lockSession();
  let resolve!: (value: { data: Session }) => void;
  vi.mocked(api.post).mockImplementationOnce(
    () =>
      new Promise((done) => {
        resolve = done;
      }),
  );
  const unlocking = unlockSession();
  await vi.waitFor(() => expect(resolve).toBeTypeOf('function'));
  await signOut();
  resolve({ data: session });
  await expect(unlocking).rejects.toThrow('session changed');
  expect(sessionStore.read()).toBeNull();
  expect(mocks.storage.size).toBe(0);
});
test('logging out during biometric enrollment removes the pending protected credential', async () => {
  await sessionStore.write(session);
  let resume!: (value: string | null) => void;
  vi.mocked(SecureStore.getItemAsync).mockImplementationOnce(
    () =>
      new Promise((done) => {
        resume = done;
      }),
  );
  const enrolling = enableBiometrics();
  await vi.waitFor(() => expect(resume).toBeTypeOf('function'));
  const loggingOut = signOut();
  resume(session.refreshToken);
  await expect(enrolling).rejects.toThrow('session changed');
  await loggingOut;
  expect(sessionStore.read()).toBeNull();
  expect(mocks.storage.size).toBe(0);
});
