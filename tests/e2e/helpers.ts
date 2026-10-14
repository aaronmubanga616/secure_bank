import { expect, type APIRequestContext } from '@playwright/test';
export const backend = process.env.API_URL ?? 'http://localhost:8180/api/v1';
export const mailpit = process.env.MAILPIT_URL ?? 'http://localhost:8025';
export const password = 'A private test password 123!';
export async function verificationToken(request: APIRequestContext, email: string) {
  let id = '';
  await expect
    .poll(
      async () => {
        const response = await request.get(`${mailpit}/api/v1/search`, {
          params: { query: `to:${email}` },
        });
        const result = (await response.json()) as { messages: { ID: string }[] };
        id = result.messages?.[0]?.ID ?? '';
        return id;
      },
      { timeout: 30_000 },
    )
    .not.toBe('');
  const message = (await (await request.get(`${mailpit}/api/v1/message/${id}`)).json()) as {
    Text: string;
  };
  const token = /#token=([A-Za-z0-9_-]+)/.exec(message.Text)?.[1];
  if (!token) throw new Error('Verification email did not contain a token.');
  return token;
}
export async function customer(request: APIRequestContext, name = 'Maya Banda') {
  const email = `test-${crypto.randomUUID()}@example.test`;
  expect(
    (
      await request.post(`${backend}/auth/register`, { data: { email, fullName: name, password } })
    ).status(),
  ).toBe(202);
  const token = await verificationToken(request, email);
  expect((await request.post(`${backend}/auth/verify-email`, { data: { token } })).status()).toBe(
    204,
  );
  const session = (await (
    await request.post(`${backend}/auth/login`, { data: { email, password } })
  ).json()) as { accessToken: string };
  return { email, headers: { Authorization: `Bearer ${session.accessToken}` } };
}
