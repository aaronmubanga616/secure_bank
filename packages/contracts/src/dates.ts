export function lusakaRange(from?: string, to?: string): { from?: string; to?: string } {
  const result: { from?: string; to?: string } = {};
  for (const value of [from, to])
    if (
      value &&
      (!/^\d{4}-\d{2}-\d{2}$/.test(value) ||
        !Number.isFinite(new Date(value + 'T00:00:00Z').getTime()) ||
        new Date(value + 'T00:00:00Z').toISOString().slice(0, 10) !== value)
    )
      throw new Error('Use a valid date in YYYY-MM-DD format.');
  if (from) result.from = from + 'T00:00:00+02:00';
  if (to) {
    const next = new Date(to + 'T00:00:00Z');
    next.setUTCDate(next.getUTCDate() + 1);
    result.to = next.toISOString().slice(0, 10) + 'T00:00:00+02:00';
  }
  return result;
}
