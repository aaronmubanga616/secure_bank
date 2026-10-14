import { Screen, Card, Button, go } from '../../src/ui';
import { signOut } from '../../src/session';
export default function More() {
  return (
    <Screen title="Make yourself at home." subtitle="Your plans. Your people. Your peace of mind.">
      <Card>
        {[
          ['accounts', 'My accounts'],
          ['recipients', 'Saved recipients'],
          ['scheduled', 'Scheduled transfers'],
          ['notifications', 'Notification inbox'],
          ['statements', 'Download statements'],
          ['profile', 'Personal information'],
          ['security', 'Security & device unlock'],
        ].map(([path, label]) => (
          <Button secondary key={path} title={label + ' →'} onPress={() => go('/' + path)} />
        ))}
      </Card>
      <Button
        title="Sign out"
        onPress={() => {
          void signOut();
        }}
      />
    </Screen>
  );
}
