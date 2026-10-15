import { useState } from 'react';
import { useLocalSearchParams } from 'expo-router';
import { View, Text } from 'react-native';
import { type Account, type Page, type Transaction, formatMoney } from '@securebank/contracts';
import {
  Screen,
  Card,
  Copy,
  Heading,
  Loading,
  ErrorState,
  Button,
  useData,
  go,
  styles,
} from '../../src/ui';
export default function AccountDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const [page, setPage] = useState(0);
  const account = useData<Account>('/accounts/' + id);
  const history = useData<Page<Transaction>>(`/accounts/${id}/transactions`, { page, size: 10 });
  return (
    <Screen title="Your account, in detail.">
      <ErrorState error={account.error ?? history.error} />
      {account.isPending ? (
        <Loading />
      ) : (
        account.data && (
          <Card>
            <Heading>
              {account.data.type} · {account.data.status}
            </Heading>
            <Text style={styles.amount}>{formatMoney(account.data.balance)}</Text>
            <Copy>{account.data.accountNumber}</Copy>
            <Copy>Opened {new Date(account.data.createdAt).toLocaleDateString()}</Copy>
            <Button secondary title="Get a statement" onPress={() => go('/statements')} />
          </Card>
        )
      )}
      <Card>
        <Heading>Account activity</Heading>
        {history.isPending ? (
          <Loading />
        ) : (
          history.data?.content.map((tx) => (
            <View key={tx.id} style={styles.row}>
              <Heading>{tx.description}</Heading>
              <Copy>
                {formatMoney(tx.amount)} · {tx.status}
              </Copy>
              <Button secondary title="View receipt" onPress={() => go('/receipt?id=' + tx.id)} />
            </View>
          ))
        )}
        {history.data?.totalElements === 0 && <Copy>No transactions on this account yet.</Copy>}
        <Copy>Page {page + 1}</Copy>
        <Button secondary title="Previous" disabled={!page} onPress={() => setPage(page - 1)} />
        <Button
          secondary
          title="Next"
          disabled={!history.data || (page + 1) * 10 >= history.data.totalElements}
          onPress={() => setPage(page + 1)}
        />
      </Card>
    </Screen>
  );
}
