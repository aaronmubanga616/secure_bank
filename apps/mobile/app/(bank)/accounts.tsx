import { Text } from 'react-native';
import { type Account, formatMoney } from '@securebank/contracts';
import { api } from '../../src/session';
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
export default function Accounts() {
  const query = useData<Account[]>('/accounts');
  return (
    <Screen title="My accounts." subtitle="For your everyday and your plans.">
      <ErrorState error={query.error} />
      {query.isPending ? (
        <Loading />
      ) : (
        query.data?.map((account) => (
          <Card key={account.id}>
            <Heading>{account.type === 'CURRENT' ? 'Everyday current' : 'Savings account'}</Heading>
            <Text style={styles.amount}>{formatMoney(account.balance)}</Text>
            <Copy>
              {account.accountNumber} · {account.status}
            </Copy>
          </Card>
        ))
      )}
      {query.data && query.data.length < 2 && (
        <Card>
          <Heading>Make room for what’s next.</Heading>
          <Form
            fields={[
              {
                name: 'type',
                label: 'Account type',
                options: ['CURRENT', 'SAVINGS']
                  .filter((type) => !query.data?.some((account) => account.type === type))
                  .map((type) => ({
                    value: type,
                    label: type === 'CURRENT' ? 'Everyday current' : 'Savings',
                  })),
              },
            ]}
            submit="Open account"
            action={async (values) => {
              await api.post('/accounts', values);
              return 'Your account is ready.';
            }}
          />
        </Card>
      )}
    </Screen>
  );
}
