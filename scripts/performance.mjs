import { randomUUID } from 'node:crypto';
import { performance } from 'node:perf_hooks';
import { mkdirSync, writeFileSync } from 'node:fs';
const api = (process.env.API_URL ?? 'http://localhost:8180/api/v1').replace(/\/$/, '');
if (!['localhost', '127.0.0.1', 'host.docker.internal'].includes(new URL(api).hostname))
  throw new Error('This bounded simulator load check only runs against a local API.');
async function request(path, method = 'GET', body, token, key) {
  const response = await fetch(api + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
      ...(key ? { 'Idempotency-Key': key } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) throw new Error(`${method} ${path}: HTTP ${response.status}`);
  return response.status === 204 ? null : response.json();
}
const email = `performance-${randomUUID()}@example.test`,
  password = randomUUID() + 'aA1!';
await request('/auth/register', 'POST', { email, fullName: 'Performance Test Customer', password });
const mailpit = process.env.MAILPIT_URL ?? 'http://localhost:8025';
let message;
for (let attempt = 0; attempt < 40; attempt++) {
  const result = await (
    await fetch(`${mailpit}/api/v1/search?query=${encodeURIComponent('to:' + email)}`)
  ).json();
  if (result.messages?.length) {
    message = await (await fetch(`${mailpit}/api/v1/message/${result.messages[0].ID}`)).json();
    break;
  }
  await new Promise((done) => setTimeout(done, 500));
}
const token = /#token=([A-Za-z0-9_-]+)/.exec(message?.Text ?? '')?.[1];
if (!token) throw new Error('No local verification email arrived.');
await request('/auth/verify-email', 'POST', { token });
const session = await request('/auth/login', 'POST', { email, password });
const account = await request('/accounts', 'POST', { type: 'CURRENT' }, session.accessToken);
const latencies = [];
let cursor = 0;
const start = performance.now();
await Promise.all(
  Array.from({ length: 10 }, async () => {
    while (cursor < 100) {
      cursor++;
      const began = performance.now();
      const result = await request(
        '/transactions',
        'POST',
        {
          type: 'DEPOSIT',
          destinationAccountNumber: account.accountNumber,
          amount: '1.00',
          description: 'Bounded local load check',
        },
        session.accessToken,
        randomUUID(),
      );
      if (result.status !== 'COMPLETED') throw new Error('A load-test deposit was declined.');
      latencies.push(performance.now() - began);
    }
  }),
);
const duration = performance.now() - start;
const retryKey = randomUUID();
const duplicate = await Promise.all(
  Array.from({ length: 10 }, () =>
    request(
      '/transactions',
      'POST',
      {
        type: 'DEPOSIT',
        destinationAccountNumber: account.accountNumber,
        amount: '2.00',
        description: 'Concurrent duplicate check',
      },
      session.accessToken,
      retryKey,
    ),
  ),
);
if (new Set(duplicate.map((tx) => tx.id)).size !== 1)
  throw new Error('Concurrent retries created multiple transactions.');
const balance = await request('/accounts/' + account.id, 'GET', undefined, session.accessToken);
if (balance.balance !== '102.00') throw new Error('The final ledger-derived balance is incorrect.');
latencies.sort((a, b) => a - b);
const report = {
  checkedAt: new Date().toISOString(),
  concurrency: 10,
  uniqueDeposits: 101,
  duplicateRequests: 10,
  finalBalance: balance.balance,
  currency: 'SIMULATED ZMW',
  durationMs: Math.round(duration),
  throughputPerSecond: Number((100 / (duration / 1000)).toFixed(2)),
  p50Ms: Math.round(latencies[49]),
  p95Ms: Math.round(latencies[94]),
  maximumMs: Math.round(latencies.at(-1)),
};
mkdirSync('.local', { recursive: true });
writeFileSync('.local/performance-report.json', JSON.stringify(report, null, 2));
console.log(JSON.stringify(report, null, 2));
if (report.p95Ms > 5000) throw new Error('The local p95 target of five seconds was exceeded.');
