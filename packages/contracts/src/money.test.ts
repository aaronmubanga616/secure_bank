import { describe, expect, it } from 'vitest';
import { formatMoney } from './index';

describe('money display', () => {
  it('preserves exact cents even beyond the safe integer range', () => {
    expect(formatMoney('9007199254740993.05')).toBe('K9,007,199,254,740,993.05');
    expect(formatMoney('-0.01')).toBe('-K0.01');
    expect(formatMoney('25000')).toBe('K25,000.00');
  });
  it('rejects malformed values rather than displaying NaN or silently rounding', () => {
    for (const amount of ['1e3', 'NaN', '1.999', '']) expect(() => formatMoney(amount)).toThrow();
  });
});
