import { expect, test } from 'vitest';
import { normalizeAmount, paymentKeyKeeper, type PaymentDraft } from './payments';
test('web and native amount entry preserve exact cents and reject zero, exponents, and extra decimals', () => {
  expect(normalizeAmount('000012.5')).toBe('12.50');
  expect(normalizeAmount('9999999999999999.99')).toBe('9999999999999999.99');
  for (const value of ['0', '0.00', '1.001', '1e3', '-2', '1,000'])
    expect(() => normalizeAmount(value)).toThrow();
});
test('reopening the same payment review preserves its retry key, while a changed amount gets a new key', () => {
  let sequence = 0;
  const key = paymentKeyKeeper(() => `intent-${++sequence}`);
  const draft: PaymentDraft = {
    type: 'TRANSFER',
    sourceAccount: 'source',
    destinationAccountNumber: 'SB1000000001',
    amount: '10.00',
    description: 'Shared lunch',
  };
  const first = key(draft);
  expect(key({ ...draft, amount: '10', description: ' Shared lunch ' })).toBe(first);
  const changed = key({ ...draft, amount: '20' });
  expect(changed).not.toBe(first);
  expect(key(draft)).toBe(first);
  expect(sequence).toBe(2);
  key.completed(draft);
  expect(key(draft)).not.toBe(first);
});
