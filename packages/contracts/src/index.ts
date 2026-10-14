export const API_PREFIX = '/api/v1';
export const CURRENCY = 'ZMW';

export interface ApiError {
  status: number;
  code: string;
  message: string;
  correlationId: string;
  fieldErrors?: Record<string, string>;
}

export interface PlatformInfo {
  name: string;
  mode: string;
  currency: string;
}

export function formatMoney(amount: string | number): string {
  return new Intl.NumberFormat('en-ZM', {
    style: 'currency', currency: CURRENCY, currencyDisplay: 'narrowSymbol',
  }).format(Number(amount));
}
