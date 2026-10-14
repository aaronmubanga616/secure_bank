# Native banking app

The Expo Router app includes sign-in, registration, verification/reset token entry, authenticator challenges, dashboard, account opening, filtered history, receipts, deposits/withdrawals/transfers with review and stable retry keys, recipients, recurring schedules, notifications, profile/password/MFA management, and PDF sharing.

Set `EXPO_PUBLIC_API_URL` before `npm run dev:mobile`. Android emulators normally use `http://10.0.2.2:8180/api/v1`; iOS simulators can use `http://localhost:8180/api/v1`. A physical phone needs the computer's LAN IP and an API bound to that network, with the firewall allowing it. Compose gateways bind loopback by default; use an explicitly configured HTTPS development tunnel or a local backend for a phone. HTTP is for trusted local development. Deployed APIs must use HTTPS.

Access tokens and private query data stay in memory. Refresh tokens use Expo SecureStore, never AsyncStorage. Optional device unlock migrates the refresh token into biometric-protected storage and locks the UI on backgrounding. The keystore prompt precedes restoring the server session. Changing enrolled biometrics may invalidate that token and require password/MFA sign-in. Biometrics do not replace backend authentication. Logout clears storage and prevents an in-flight unlock from restoring it.

Use a native build to validate Face ID and SecureStore authentication; Expo Go has platform limitations. Pull down to refresh screens. Notifications use an API inbox rather than OS push delivery. A temporary statement PDF is deleted after the device share sheet completes.

For an internal build, install the EAS CLI, sign in to your Expo account, initialize the project, configure an HTTPS `EXPO_PUBLIC_API_URL` in the selected EAS environment, and run `eas build --profile preview --platform android` (or `ios`). The configuration contains no account IDs or signing secrets. EAS provisions signing through your account; iOS device distribution may require Apple membership. JavaScript exports with `npm run build:mobile` do not prove native packaging or device behavior.

Before distributing, test registration/MFA, background locking, biometric enrollment changes, network loss and payment retry, session expiry, logout during unlock, screen readers, PDF sharing, and notification read state on both platforms.
