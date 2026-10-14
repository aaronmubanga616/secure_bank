import { type User } from '@securebank/contracts';
import { api, useSession, sessionStore } from '../../src/session';
import { Screen, Card, Copy, Form, Loading, ErrorState, useData } from '../../src/ui';
export default function Profile() {
  const { session } = useSession();
  const query = useData<User>('/users/me');
  return (
    <Screen title="Your details.">
      <ErrorState error={query.error} />
      {query.isPending ? (
        <Loading />
      ) : (
        query.data && (
          <Card>
            <Copy>{query.data.email} · Verified email</Copy>
            <Form
              fields={[{ name: 'fullName', label: 'Full name', initial: query.data.fullName }]}
              submit="Save profile"
              action={async (values) => {
                const { data } = await api.patch<User>('/users/me', values);
                if (session) await sessionStore.write({ ...session, user: data });
              }}
            />
          </Card>
        )
      )}
    </Screen>
  );
}
