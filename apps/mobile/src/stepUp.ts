import { type InputField } from './ui';
export function stepUp(mfa: boolean): InputField[] {
  return [
    { name: 'password', label: 'Confirm password', secure: true },
    ...(mfa ? [{ name: 'code', label: 'Authenticator or recovery code' }] : []),
  ];
}
