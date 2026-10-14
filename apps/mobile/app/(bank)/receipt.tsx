import { useLocalSearchParams } from 'expo-router';
import { Text } from 'react-native';
import { type Transaction, formatMoney } from '@securebank/contracts';
import {
  Screen,
  Card,
  Copy,
  Notice,
  Heading,
  Loading,
  ErrorState,
  useData,
  styles,
  Button,
  go,
} from '../../src/ui';
export default function Receipt() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const query = useData<Transaction>('/transactions/' + id);
  const tx = query.data;
  return (
    <Screen title="A clear record.">
      <ErrorState error={query.error} />
      {query.isPending ? (
        <Loading />
      ) : (
        tx && (
          <Card>
            <Heading>{tx.description}</Heading>
            <Text style={styles.amount}>{formatMoney(tx.amount)}</Text>
            <Text style={styles.status}>{tx.status}</Text>
            {tx.status === 'FLAGGED' && (
              <Notice>This transfer is under review. No funds have moved.</Notice>
            )}
            {tx.status === 'FAILED' && (
              <Notice error>No funds moved. {tx.failureReason?.replaceAll('_', ' ')}</Notice>
            )}
            <Copy selectable>{tx.reference}</Copy>
            <Copy>From: {tx.sourceAccountNumber ?? 'Simulated cash'}</Copy>
            <Copy>To: {tx.destinationAccountNumber ?? 'Simulated cash'}</Copy>
            <Copy>Submitted: {new Date(tx.createdAt).toLocaleString()}</Copy>
            <Copy>
              Posted: {tx.completedAt ? new Date(tx.completedAt).toLocaleString() : 'Not posted'}
            </Copy>
            <Button title="Back to overview" onPress={() => go('/home')} />
          </Card>
        )
      )}
    </Screen>
  );
}
