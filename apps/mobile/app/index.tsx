import { Redirect } from 'expo-router';
import { useSession } from '../src/session';
import { Loading, Screen, Copy } from '../src/ui';
export default function Index() {
  const state = useSession();
  if (!state.ready)
    return (
      <Screen title="SecureBank." subtitle="Your everyday, with a little more certainty.">
        <Loading />
        <Copy>Opening your secure space…</Copy>
      </Screen>
    );
  return <Redirect href={state.locked ? '/unlock' : state.session ? '/home' : '/auth'} />;
}
