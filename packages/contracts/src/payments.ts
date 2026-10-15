export interface PaymentDraft {
  type: string;
  sourceAccount: string | null;
  destinationAccountNumber: string | null;
  amount: string;
  description: string;
}
export function normalizeAmount(value: string): string {
  const match = /^(\d{1,16})(?:\.(\d{1,2}))?$/.exec(value.trim());
  if (!match) throw new Error('Use a positive amount with at most two decimal places.');
  const result = `${BigInt(match[1])}.${(match[2] ?? '').padEnd(2, '0')}`;
  if (BigInt(match[1]) * 100n + BigInt((match[2] ?? '').padEnd(2, '0')) === 0n)
    throw new Error('The amount must be greater than zero.');
  return result;
}
export function paymentKeyKeeper(newKey: () => string) {
  const keys = new Map<string, string>();
  const signatureFor = (draft: PaymentDraft) =>
    JSON.stringify([
      draft.type,
      draft.sourceAccount,
      draft.destinationAccountNumber?.trim().toUpperCase() ?? null,
      normalizeAmount(draft.amount),
      draft.description.trim(),
    ]);
  const keyFor = (draft: PaymentDraft) => {
    const signature = signatureFor(draft);
    let key = keys.get(signature);
    if (!key) {
      key = newKey();
      keys.set(signature, key);
    }
    return key;
  };
  return Object.assign(keyFor, {
    completed: (draft: PaymentDraft) => keys.delete(signatureFor(draft)),
  });
}
