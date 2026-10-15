import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import type { ApiError, Session, SessionStore } from './index';

interface RetryConfig extends InternalAxiosRequestConfig {
  securebankRetried?: boolean;
}

export function createApiClient(baseURL: string, store: SessionStore) {
  const client = axios.create({ baseURL, timeout: 15_000 });
  const refreshClient = axios.create({ baseURL, timeout: 15_000 });
  let refreshing: Promise<Session> | null = null;

  client.interceptors.request.use((config) => {
    const session = store.read();
    if (session) config.headers.Authorization = `Bearer ${session.accessToken}`;
    return config;
  });
  client.interceptors.response.use(
    (response) => response,
    async (error: AxiosError) => {
      const config = error.config as RetryConfig | undefined;
      const previous = store.read();
      if (
        error.response?.status !== 401 ||
        !config ||
        config.securebankRetried ||
        !previous ||
        config.url?.startsWith('/auth/')
      )
        throw error;
      config.securebankRetried = true;
      if (!refreshing) {
        refreshing = refreshClient
          .post<Session>('/auth/refresh', { refreshToken: previous.refreshToken })
          .then(async ({ data }) => {
            if (store.read()?.refreshToken !== previous.refreshToken)
              throw new Error('The session changed.');
            await store.write(data);
            return data;
          })
          .catch(async (failure: unknown) => {
            if (store.read()?.refreshToken === previous.refreshToken) await store.clear();
            throw failure;
          })
          .finally(() => {
            refreshing = null;
          });
      }
      const session = await refreshing;
      config.headers.Authorization = `Bearer ${session.accessToken}`;
      return client(config);
    },
  );
  return client;
}

export function errorMessage(error: unknown): string {
  if (axios.isAxiosError<ApiError>(error)) {
    if (error.response?.data?.message) return error.response.data.message;
    if (!error.response)
      return 'We could not reach SecureBank. Check your connection and try again.';
  }
  return 'We could not complete that action. Please try again.';
}
