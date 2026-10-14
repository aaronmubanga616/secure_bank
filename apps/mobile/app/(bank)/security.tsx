import { useState } from 'react';
import { api, useSession, signOut, enableBiometrics, disableBiometrics } from '../../src/session';
import { Screen, Card, Copy, Heading, Form, Button } from '../../src/ui';
export default function Security() {
  const state = useSession();
  const [setup, setSetup] = useState('');
  const [recovery, setRecovery] = useState<string[]>([]);
  return (
    <Screen title="Peace of mind." subtitle="Security that stays in your control.">
      {recovery.length ? (
        <Card>
          <Heading>Save your recovery codes</Heading>
          <Copy>Shown once. Store privately, then sign in again.</Copy>
          <Copy selectable>{recovery.join('\n')}</Copy>
          <Button
            title="Saved — sign in again"
            onPress={() => {
              void signOut();
            }}
          />
        </Card>
      ) : (
        <>
          <Card>
            <Heading>Device unlock</Heading>
            <Copy>
              {state.biometrics
                ? 'Your saved refresh token is protected by device biometrics.'
                : 'Add a biometric lock to this device’s saved session.'}{' '}
              Biometrics unlock local storage; the server still checks your account.
            </Copy>
            <Form
              fields={[]}
              submit={state.biometrics ? 'Disable biometric unlock' : 'Enable biometric unlock'}
              action={async () => {
                if (state.biometrics) await disableBiometrics();
                else await enableBiometrics();
              }}
            />
          </Card>
          <Card>
            <Heading>Authenticator protection</Heading>
            {!state.session?.user.mfaEnabled ? (
              !setup ? (
                <Form
                  fields={[{ name: 'password', label: 'Confirm password', secure: true }]}
                  submit="Set up authenticator"
                  action={async (values) => {
                    setSetup(
                      (await api.post<{ secret: string }>('/auth/mfa/setup', values)).data.secret,
                    );
                  }}
                />
              ) : (
                <>
                  <Copy>Add this setup key to a time-based authenticator:</Copy>
                  <Copy selectable>{setup}</Copy>
                  <Form
                    fields={[
                      { name: 'code', label: 'Six-digit authenticator code', numeric: true },
                    ]}
                    submit="Enable & get recovery codes"
                    action={async (values) => {
                      const { data } = await api.post<{ recoveryCodes: string[] }>(
                        '/auth/mfa/confirm',
                        values,
                      );
                      setRecovery(data.recoveryCodes);
                    }}
                  />
                </>
              )
            ) : (
              <Form
                fields={[
                  { name: 'password', label: 'Password', secure: true },
                  { name: 'code', label: 'Authenticator or recovery code' },
                ]}
                submit="Disable authenticator"
                action={async (values) => {
                  await api.post('/auth/mfa/disable', values);
                  await signOut();
                }}
              />
            )}
          </Card>
          <Card>
            <Heading>Update password</Heading>
            <Copy>Changing your password signs you out on all devices.</Copy>
            <Form
              fields={[
                { name: 'currentPassword', label: 'Current password', secure: true },
                { name: 'newPassword', label: 'New password (12+ characters)', secure: true },
              ]}
              submit="Change password"
              action={async (values) => {
                await api.post('/auth/change-password', values);
                await signOut();
              }}
            />
          </Card>
        </>
      )}
    </Screen>
  );
}
