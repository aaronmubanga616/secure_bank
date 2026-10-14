import { Redirect } from 'expo-router';
import { useSession } from '../src/session';
import { Loading } from '../src/ui';
export default function Index() {
  const state = useSession();
  if (!state.ready) return <Loading />;
  return <Redirect href={state.locked ? '/unlock' : state.session ? '/home' : '/auth'} />;
}
