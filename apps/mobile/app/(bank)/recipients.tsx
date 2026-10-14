import { View } from 'react-native';
import { type Beneficiary } from '@securebank/contracts';
import { api, useSession } from '../../src/session';
import { stepUp } from '../../src/stepUp';
import {
  Screen,
  Card,
  Copy,
  Heading,
  Form,
  Loading,
  ErrorState,
  useData,
  styles,
} from '../../src/ui';
export default function Recipients() {
  const query = useData<Beneficiary[]>('/beneficiaries');
  const { session } = useSession();
  return (
    <Screen title="Your people." subtitle="Familiar faces, fewer details.">
      <ErrorState error={query.error} />
      <Card>
        <Heading>Saved recipients</Heading>
        {query.isPending ? (
          <Loading />
        ) : (
          query.data?.map((recipient) => (
            <View style={styles.row} key={recipient.id}>
              <Heading>{recipient.name}</Heading>
              <Copy>
                {recipient.fullName} · {recipient.accountNumber}
              </Copy>
              <Form
                fields={[]}
                submit="Remove & cancel schedules"
                action={async () => {
                  await api.delete('/beneficiaries/' + recipient.id);
                  return 'Recipient removed. Associated schedules are cancelled.';
                }}
              />
            </View>
          ))
        )}
        {query.data?.length === 0 && (
          <Copy>Save someone new to make your next payment easier.</Copy>
        )}
      </Card>
      <Card>
        <Heading>Add someone new</Heading>
        <Form
          fields={[
            { name: 'name', label: 'Name or nickname' },
            { name: 'accountNumber', label: 'SecureBank account number' },
            ...stepUp(session?.user.mfaEnabled ?? false),
          ]}
          submit="Save recipient"
          action={async (values) => {
            await api.post('/beneficiaries', values);
            return 'Recipient saved.';
          }}
        />
      </Card>
    </Screen>
  );
}
