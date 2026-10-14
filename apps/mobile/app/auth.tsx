import { useState } from 'react';
import { useLocalSearchParams } from 'expo-router';
import { type LoginResult } from '@securebank/contracts';
import { api, sessionStore } from '../src/session';
import { Screen, Card, Copy, Form, Button, type InputField } from '../src/ui';
export default function Auth() {
  const params = useLocalSearchParams<{ mode?: string; token?: string }>();
  const [mode, setMode] = useState(params.mode ?? 'login');
  const [challenge, setChallenge] = useState('');
  const fields: InputField[] = challenge
    ? [{ name: 'code', label: 'Authenticator or recovery code' }]
    : [
        ...(mode === 'register' ? [{ name: 'fullName', label: 'Full name' }] : []),
        ...(['login', 'register', 'forgot'].includes(mode)
          ? [{ name: 'email', label: 'Email address' }]
          : []),
        ...(['login', 'register', 'reset'].includes(mode)
          ? [
              {
                name: 'password',
                label: mode === 'login' ? 'Password' : 'New password (12+ characters)',
                secure: true,
              },
            ]
          : []),
        ...(['verify', 'reset'].includes(mode)
          ? [{ name: 'token', label: 'Token from email link', initial: params.token }]
          : []),
      ];
  async function accept(result: LoginResult) {
    if (result.mfaRequired) {
      setChallenge(result.challenge);
      return;
    }
    if (result.user.role !== 'CUSTOMER') {
      await api.post('/auth/logout', { refreshToken: result.refreshToken });
      throw new Error('Use the staff web portal for this account.');
    }
    await sessionStore.write(result);
  }
  return (
    <Screen
      title={
        challenge
          ? 'Your security code.'
          : mode === 'login'
            ? 'A clearer everyday.'
            : mode === 'register'
              ? 'Make room for possibility.'
              : mode === 'verify'
                ? 'Verify your email.'
                : mode === 'reset'
                  ? 'A fresh start.'
                  : 'Let’s get you back in.'
      }
      subtitle="Your money, in your hands."
    >
      <Card>
        <Form
          key={mode + challenge}
          fields={fields}
          submit={
            challenge
              ? 'Verify & sign in'
              : ({
                  login: 'Sign in',
                  register: 'Create account',
                  verify: 'Verify email',
                  reset: 'Set password',
                  forgot: 'Send reset email',
                }[mode] ?? 'Continue')
          }
          action={async (values) => {
            if (challenge) {
              await accept(
                (await api.post<LoginResult>('/auth/mfa/verify', { challenge, code: values.code }))
                  .data,
              );
              return;
            }
            if (mode === 'login') {
              await accept((await api.post<LoginResult>('/auth/login', values)).data);
              return;
            }
            if (mode === 'register') {
              await api.post('/auth/register', values);
              return 'Open your verification email, then sign in. Local email is available in Mailpit.';
            }
            if (mode === 'verify') {
              await api.post('/auth/verify-email', { token: values.token });
              return 'Email verified. You can now sign in.';
            }
            if (mode === 'reset') {
              await api.post('/auth/reset-password', values);
              return 'Password changed. Sign in with your new password.';
            }
            await api.post('/auth/forgot-password', values);
            return 'If eligible, a reset email is on its way.';
          }}
        />
      </Card>
      <Card>
        <Copy>Secure sessions. Authenticator support. Simulated funds only.</Copy>
        {[
          ['login', 'Sign in'],
          ['register', 'Create an account'],
          ['forgot', 'Forgot password?'],
          ['verify', 'Verify email'],
          ['reset', 'Reset with an email token'],
        ]
          .filter(([value]) => value !== mode)
          .map(([value, label]) => (
            <Button
              secondary
              key={value}
              title={label}
              onPress={() => {
                setMode(value);
                setChallenge('');
              }}
            />
          ))}
      </Card>
    </Screen>
  );
}
