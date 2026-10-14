import { Screen, Card, Copy, Form, Button } from '../src/ui';
import { unlockSession, signOut } from '../src/session';
export default function Unlock() {
  return (
    <Screen title="Welcome back." subtitle="A little more peace of mind.">
      <Card>
        <Copy>
          Unlock your saved device session. The server still verifies your account and session
          before showing your information.
        </Copy>
        <Form
          fields={[]}
          submit="Unlock SecureBank"
          action={async () => {
            await unlockSession();
          }}
        />
        <Button
          secondary
          title="Use password sign-in"
          onPress={() => {
            void signOut();
          }}
        />
      </Card>
    </Screen>
  );
}
