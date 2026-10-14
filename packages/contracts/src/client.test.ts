import { afterEach, describe, expect, it } from 'vitest';
import { createServer, type Server } from 'node:http';
import { createApiClient, type Session, type SessionStore } from './index';

const session: Session = {
  accessToken: 'initial-access', refreshToken: 'initial-refresh', mfaRequired: false,
  user: { id: 'test-user', email: 'test@example.test', fullName: 'Test User', role: 'CUSTOMER', status: 'ACTIVE', emailVerified: true, mfaEnabled: false },
};
let server: Server | undefined;
afterEach(async () => {
  if (!server) return;
  const active = server;
  active.closeAllConnections();
  await new Promise<void>((resolve) => active.close(() => resolve()));
  server = undefined;
});

async function setup(beforeRefresh: () => Promise<void> = async () => {}) {
  let current: Session | null = session;
  let refreshes = 0;
  const store: SessionStore = { read: () => current, write: (next) => { current = next; }, clear: () => { current = null; } };
  server = createServer(async (request, response) => {
    response.setHeader('Content-Type', 'application/json');
    if (request.url === '/auth/refresh') {
      refreshes++;
      await beforeRefresh();
      response.end(JSON.stringify({ ...session, accessToken: 'new-access', refreshToken: 'new-refresh' }));
    } else if (request.headers.authorization === 'Bearer new-access') {
      response.end(JSON.stringify({ ok: true }));
    } else {
      response.writeHead(401); response.end(JSON.stringify({ message: 'Expired' }));
    }
  });
  await new Promise<void>((resolve) => server!.listen(0, '127.0.0.1', resolve));
  const address = server.address();
  if (!address || typeof address === 'string') throw new Error('Test server did not bind.');
  return { client: createApiClient(`http://127.0.0.1:${address.port}`, store), store, refreshes: () => refreshes };
}

describe('session refresh', () => {
  it('shares one refresh across simultaneous expired requests', async () => {
    const { client, store, refreshes } = await setup();
    const responses = await Promise.all([client.get('/accounts'), client.get('/transactions')]);
    expect(responses.every((response) => response.data.ok)).toBe(true);
    expect(refreshes()).toBe(1);
    expect(store.read()?.refreshToken).toBe('new-refresh');
  });

  it('does not restore a session when logout happens during refresh', async () => {
    let release: () => void = () => {};
    let started: () => void = () => {};
    const waiting = new Promise<void>((resolve) => { release = resolve; });
    const refreshing = new Promise<void>((resolve) => { started = resolve; });
    const { client, store } = await setup(async () => { started(); await waiting; });
    const request = client.get('/accounts');
    const result = expect(request).rejects.toThrow('The session changed.');
    await refreshing;
    await store.clear();
    release();
    await result;
    expect(store.read()).toBeNull();
  });
});
