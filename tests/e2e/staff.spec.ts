import { test, expect } from '@playwright/test';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { backend, customer } from './helpers';
import { totp } from './totp';
const adminURL = process.env.ADMIN_URL ?? 'http://localhost:5174';
test('staff enrolls MFA, releases a held payment, freezes an account, and follows the audit trail', async ({
  page,
  request,
}) => {
  test.skip(
    process.env.BOOTSTRAP_ADMIN_ENABLED !== 'true',
    'Enable private bootstrap credentials for the live staff journey.',
  );
  const alice = await customer(request, 'Aisha Zulu ' + crypto.randomUUID().slice(0, 4));
  const bob = await customer(request, 'Daniel Phiri');
  const source = (await (
    await request.post(backend + '/accounts', { headers: alice.headers, data: { type: 'CURRENT' } })
  ).json()) as { id: string; accountNumber: string };
  const target = (await (
    await request.post(backend + '/accounts', { headers: bob.headers, data: { type: 'CURRENT' } })
  ).json()) as { id: string; accountNumber: string };
  const headers = { ...alice.headers, 'Idempotency-Key': crypto.randomUUID() };
  await request.post(backend + '/transactions', {
    headers,
    data: {
      type: 'DEPOSIT',
      destinationAccountNumber: source.accountNumber,
      amount: '40000',
      description: 'Investigation test funding',
    },
  });
  const held = (await (
    await request.post(backend + '/transactions', {
      headers: { ...alice.headers, 'Idempotency-Key': crypto.randomUUID() },
      data: {
        type: 'TRANSFER',
        sourceAccount: source.id,
        destinationAccountNumber: target.accountNumber,
        amount: '25000',
        description: 'Staff review example',
      },
    })
  ).json()) as { id: string; status: string };
  expect(held.status).toBe('FLAGGED');
  await page.goto(adminURL + '/login');
  await page.getByLabel('Email address').fill(process.env.BOOTSTRAP_ADMIN_EMAIL!);
  await page.getByLabel('Password', { exact: true }).fill(process.env.BOOTSTRAP_ADMIN_PASSWORD!);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  const statePath = '.local/e2e-admin.json';
  let secret = '';
  await expect(
    page.getByRole('heading', { name: /Your security code\.|Security & peace of mind/ }),
  ).toBeVisible();
  if (await page.getByRole('heading', { name: 'Your security code.' }).isVisible()) {
    if (!existsSync(statePath))
      throw new Error(
        'This test administrator already has MFA. Provide its local test authenticator state.',
      );
    secret = (JSON.parse(readFileSync(statePath, 'utf8')) as { secret: string }).secret;
  } else {
    await expect(page.getByRole('heading', { name: 'Security & peace of mind' })).toBeVisible();
    await page.getByLabel('Confirm password').fill(process.env.BOOTSTRAP_ADMIN_PASSWORD!);
    await page.getByRole('button', { name: 'Set up authenticator' }).click();
    secret = (await page.locator('.secret').textContent())!.trim();
    await page.getByLabel('Six-digit code').fill(totp(secret));
    await page.getByRole('button', { name: 'Enable & get recovery codes' }).click();
    await expect(page.getByRole('heading', { name: 'Save your recovery codes' })).toBeVisible();
    mkdirSync('.local', { recursive: true });
    writeFileSync(statePath, JSON.stringify({ secret }), { mode: 0o600 });
    await page.getByRole('button', { name: 'Saved — sign in again' }).click();
    await page.getByLabel('Email address').fill(process.env.BOOTSTRAP_ADMIN_EMAIL!);
    await page.getByLabel('Password', { exact: true }).fill(process.env.BOOTSTRAP_ADMIN_PASSWORD!);
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  }
  await expect(page.getByLabel('Authenticator or recovery code')).toBeVisible();
  await page.getByLabel('Authenticator or recovery code').fill(totp(secret));
  await page.getByRole('button', { name: 'Verify & sign in' }).click();
  await expect(
    page.getByRole('heading', { level: 1, name: 'A considered response.' }),
  ).toBeVisible();
  await page.getByRole('navigation').getByRole('link', { name: 'Fraud reviews' }).click();
  const panel = page.locator('section.panel').filter({ hasText: held.id });
  await expect(panel).toBeVisible();
  await panel.getByLabel('Review outcome').selectOption('FALSE_POSITIVE');
  await panel
    .getByLabel('Investigation note')
    .fill('Customer confirmed this intended simulator payment.');
  await panel.getByRole('button', { name: 'Record decision' }).click();
  await expect
    .poll(
      async () =>
        (
          (await (
            await request.get(backend + '/transactions/' + held.id, { headers: alice.headers })
          ).json()) as { status: string }
        ).status,
    )
    .toBe('COMPLETED');
  await page.getByRole('navigation').getByRole('link', { name: 'Customers', exact: true }).click();
  await page.getByLabel('Name, email, or customer ID').fill(alice.email);
  await page.getByRole('button', { name: 'Find customers' }).click();
  await page.locator('tbody').getByRole('link').click();
  await page
    .getByLabel('Reason for current status change')
    .fill('Customer requested a simulator account freeze.');
  await page.getByRole('button', { name: 'Freeze account', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Unfreeze account', exact: true })).toBeVisible();
  await page.getByRole('navigation').getByRole('link', { name: 'Audit trail' }).click();
  await page.getByLabel('Action, resource, or correlation ID').fill('FRAUD_FALSE_POSITIVE');
  await page.getByRole('button', { name: 'Search audit trail' }).click();
  await expect(page.locator('tbody')).toContainText('FRAUD_FALSE_POSITIVE');
  await page.getByRole('navigation').getByRole('link', { name: 'Overview', exact: true }).click();
  await page.screenshot({ path: 'test-results/staff-desktop.png', fullPage: true });
});
