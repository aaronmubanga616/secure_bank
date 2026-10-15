import { expect, test } from 'vitest';
import { lusakaRange } from './dates';
test('an inclusive date filter includes the complete last day across month and year boundaries', () => {
  expect(lusakaRange('2028-02-01', '2028-02-29')).toEqual({
    from: '2028-02-01T00:00:00+02:00',
    to: '2028-03-01T00:00:00+02:00',
  });
  expect(lusakaRange(undefined, '2026-12-31').to).toBe('2027-01-01T00:00:00+02:00');
  expect(() => lusakaRange('2026-02-30')).toThrow();
});
