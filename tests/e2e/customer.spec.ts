import { test, expect } from '@playwright/test';
import { backend, customer, password, verificationToken } from './helpers';

test('customer opens an account, moves simulated money, and downloads a statement', async ({
  page,
  request,
}) => {
  const email = `test-${crypto.randomUUID()}@example.test`;
  await page.goto('/register');
  await page.getByLabel('Full name').fill('Maya Banda');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Choose a password').fill(password);
  await page.getByRole('button', { name: 'Create account', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Check your email');
  const token = await verificationToken(request, email);
  await page.goto(`/verify-email#token=${token}`);
  await page.getByRole('button', { name: 'Verify email', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Email verified');
  await page.getByRole('link', { name: 'Back to sign in' }).click();
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Hello, Maya.' })).toBeVisible();
  await page.getByRole('navigation').getByRole('link', { name: 'My accounts' }).click();
  await page.getByLabel('Account type').selectOption('CURRENT');
  await page.getByRole('button', { name: 'Open account', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('ready');
  await page.getByRole('navigation').getByRole('link', { name: 'Move money' }).click();
  await page.getByLabel('What would you like to do?').selectOption('DEPOSIT');
  await page.getByLabel('To account', { exact: true }).selectOption({ index: 1 });
  await page.getByLabel('Amount (ZMW)').fill('1500.50');
  await page.getByLabel('What’s it for?').fill('Monthly simulator funding');
  await page.getByRole('button', { name: 'Review transaction' }).click();
  await expect(page.getByRole('heading', { name: 'One last look.' })).toBeVisible();
  await page.getByRole('button', { name: 'Confirm transaction' }).click();
  await expect(page.locator('.badge')).toHaveText('COMPLETED');
  await page.getByRole('navigation').getByRole('link', { name: 'Overview', exact: true }).click();
  await expect(page.locator('.balance-card h2')).toHaveText('K1,500.50');
  await page.screenshot({ path: 'test-results/customer-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({
    path: 'test-results/customer-mobile.png',
    fullPage: true,
    animations: 'disabled',
  });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByRole('button', { name: 'Open menu' }).click();
  await page.getByRole('navigation').getByRole('link', { name: 'Statements', exact: true }).click();
  await page.getByLabel('Account', { exact: true }).selectOption({ index: 1 });
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Download PDF' }).click();
  expect((await download).suggestedFilename()).toBe('securebank-statement.pdf');
  await page.getByRole('button', { name: 'Open menu' }).click();
  await page
    .getByRole('navigation')
    .getByRole('link', { name: 'Notifications', exact: true })
    .click();
  await expect(
    page.getByRole('heading', { name: 'Transaction completed', exact: true }),
  ).toBeVisible();
});

test('a declined withdrawal displays its failure without changing the balance', async ({
  page,
  request,
}) => {
  const user = await customer(request);
  await request.post(`${backend}/accounts`, { headers: user.headers, data: { type: 'CURRENT' } });
  await page.goto('/login');
  await page.getByLabel('Email address').fill(user.email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await page.getByRole('navigation').getByRole('link', { name: 'Move money' }).click();
  await page.getByLabel('What would you like to do?').selectOption('WITHDRAWAL');
  await page.getByLabel('From account', { exact: true }).selectOption({ index: 1 });
  await page.getByLabel('Amount (ZMW)').fill('10.00');
  await page.getByLabel('What’s it for?').fill('Insufficient funds check');
  await page.getByRole('button', { name: 'Review transaction' }).click();
  await page.getByRole('button', { name: 'Confirm transaction' }).click();
  await expect(page.getByRole('alert')).toContainText('insufficient funds');
  await expect(page.locator('.badge')).toHaveText('FAILED');
});
