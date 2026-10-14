import { useSyncExternalStore } from 'react';
import * as SecureStore from 'expo-secure-store';
import * as LocalAuthentication from 'expo-local-authentication';
import * as Crypto from 'expo-crypto';
import { QueryClient } from '@tanstack/react-query';
import { createApiClient, type Session } from '@securebank/contracts';

interface State {
  session: Session | null;
  ready: boolean;
  locked: boolean;
  biometrics: boolean;
  hasSavedSession: boolean;
}
let state: State = {
  session: null,
  ready: false,
  locked: false,
  biometrics: false,
  hasSavedSession: false,
};
const listeners = new Set<() => void>();
let generation = 0;
let storageQueue: Promise<unknown> = Promise.resolve();
function serialize<T>(action: () => Promise<T>): Promise<T> {
  const next = storageQueue.catch(() => {}).then(action);
  storageQueue = next;
  return next;
}
const normalKey = 'securebank-refresh';
const biometricKey = 'securebank-refresh-protected';
const secureOptions = { keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY };
const protectedOptions = {
  ...secureOptions,
  requireAuthentication: true,
  authenticationPrompt: 'Unlock SecureBank',
};
const emit = (next: Partial<State>) => {
  state = { ...state, ...next };
  listeners.forEach((listener) => listener());
};
export const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 15_000 } },
});
export const sessionStore = {
  read: () => (state.locked ? null : state.session),
  write: async (session: Session) => {
    const expected = generation;
    if (session.user.role !== 'CUSTOMER')
      throw new Error('Use the staff web portal for this account.');
    await serialize(async () => {
      if (expected !== generation) throw new Error('The session changed.');
      await SecureStore.setItemAsync(
        state.biometrics ? biometricKey : normalKey,
        session.refreshToken,
        state.biometrics ? protectedOptions : secureOptions,
      );
    });
    if (expected !== generation) throw new Error('The session changed.');
    emit({ session, ready: true, hasSavedSession: true });
  },
  clear: async () => {
    generation++;
    emit({ session: null, locked: false, hasSavedSession: false, ready: true });
    queryClient.clear();
    await serialize(() =>
      Promise.all([
        SecureStore.deleteItemAsync(normalKey),
        SecureStore.deleteItemAsync(biometricKey),
      ]),
    );
  },
};
export const api = createApiClient(
  process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8180/api/v1',
  sessionStore,
);
export function useSession() {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
    () => state,
  );
}
let initialization: Promise<void> | null = null;
export function initializeSession() {
  initialization ??= (async () => {
    try {
      let device = await SecureStore.getItemAsync('securebank-device');
      if (!device) {
        device = Crypto.randomUUID();
        await SecureStore.setItemAsync('securebank-device', device, secureOptions);
      }
      api.defaults.headers.common['X-Device-ID'] = device;
      const biometrics = (await SecureStore.getItemAsync('securebank-biometric')) === 'enabled';
      emit({ biometrics });
      if (biometrics) {
        emit({ ready: true, locked: true, hasSavedSession: true });
        return;
      }
      const refresh = await SecureStore.getItemAsync(normalKey);
      if (refresh)
        await sessionStore.write(
          (await api.post<Session>('/auth/refresh', { refreshToken: refresh })).data,
        );
      else emit({ ready: true });
    } catch {
      await sessionStore.clear();
    }
  })();
  return initialization;
}
export function lockSession() {
  if (state.biometrics && state.hasSavedSession) {
    emit({ locked: true });
    queryClient.clear();
  }
}
export async function unlockSession() {
  const expected = generation;
  const token = await SecureStore.getItemAsync(biometricKey, protectedOptions);
  if (!token) throw new Error('Device security changed or this session expired. Sign in again.');
  const result = (await api.post<Session>('/auth/refresh', { refreshToken: token })).data;
  if (expected !== generation) throw new Error('The session changed.');
  await sessionStore.write(result);
  emit({ locked: false });
}
export async function enableBiometrics() {
  if (!state.session) throw new Error('Sign in first.');
  if (
    !(await LocalAuthentication.hasHardwareAsync()) ||
    !(await LocalAuthentication.isEnrolledAsync()) ||
    !SecureStore.canUseBiometricAuthentication()
  )
    throw new Error('Set up supported biometrics in your device settings first.');
  await SecureStore.setItemAsync(biometricKey, state.session.refreshToken, protectedOptions);
  const token = await SecureStore.getItemAsync(biometricKey, protectedOptions);
  if (!token) throw new Error('Device verification was cancelled.');
  await SecureStore.setItemAsync('securebank-biometric', 'enabled', secureOptions);
  await SecureStore.deleteItemAsync(normalKey);
  emit({ biometrics: true });
}
export async function disableBiometrics() {
  const token = await SecureStore.getItemAsync(biometricKey, protectedOptions);
  if (!token) throw new Error('Unlock your device session first.');
  await SecureStore.setItemAsync(normalKey, token, secureOptions);
  await SecureStore.deleteItemAsync(biometricKey);
  await SecureStore.deleteItemAsync('securebank-biometric');
  emit({ biometrics: false });
}
export async function signOut() {
  const token = state.session?.refreshToken;
  await sessionStore.clear();
  await SecureStore.deleteItemAsync('securebank-biometric');
  emit({ biometrics: false });
  if (token) await api.post('/auth/logout', { refreshToken: token }).catch(() => {});
}
