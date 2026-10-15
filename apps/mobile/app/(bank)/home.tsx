import { Text, View } from 'react-native';
import {
  type Account,
  type Page,
  type Transaction,
  type Notification,
  formatMoney,
  sumMoney,
} from '@securebank/contracts';
import { useSession } from '../../src/session';
import {
  Screen,
  Card,
  Heading,
  Copy,
  Loading,
  Button,
  ErrorState,
  styles,
  useData,
  go,
} from '../../src/ui';
export default function Home() {
  const { session } = useSession();
  const accounts = useData<Account[]>('/accounts');
  const recent = useData<Page<Transaction>>('/transactions', { size: 5 });
  const notices = useData<Page<Notification>>('/notifications', { size: 5 });
  return (
    <Screen
      title={`Hello, ${session?.user.fullName.split(' ')[0]}.`}
      subtitle="A fresh perspective on your everyday."
    >
      <ErrorState error={accounts.error ?? recent.error ?? notices.error} />
      <Card dark>
        <Text style={{ color: '#b5c89f', fontSize: 10, letterSpacing: 2 }}>
          TOTAL AVAILABLE BALANCE
        </Text>
        {accounts.isPending ? (
          <Loading />
        ) : (
          <Text style={[styles.amount, { color: '#f2f8df', fontSize: 40 }]}>
            {formatMoney(sumMoney((accounts.data ?? []).map((account) => account.balance)))}
          </Text>
        )}
        <Text style={{ fontSize: 10, color: '#b1c498' }}>ZAMBIAN KWACHA · SIMULATED FUNDS</Text>
        <Button secondary title="Your accounts →" onPress={() => go('/accounts')} />
      </Card>
      <View style={{ flexDirection: 'row', gap: 12 }}>
        <View style={{ flex: 1 }}>
          <Button title="Send money ↗" onPress={() => go('/move')} />
        </View>
        <View style={{ flex: 1 }}>
          <Button secondary title="Your inbox" onPress={() => go('/notifications')} />
        </View>
      </View>
      <View style={{ flexDirection: 'row', gap: 12 }}>
        <View style={{ flex: 1 }}>
          <Button secondary title="Add funds" onPress={() => go('/move?type=DEPOSIT')} />
        </View>
        <View style={{ flex: 1 }}>
          <Button secondary title="Withdraw" onPress={() => go('/move?type=WITHDRAWAL')} />
        </View>
      </View>
      {accounts.data?.map((account) => (
        <Card key={account.id}>
          <Heading>{account.type === 'SAVINGS' ? 'Your savings' : 'Everyday current'}</Heading>
          <Text style={styles.amount}>{formatMoney(account.balance)}</Text>
          <Copy>
            {account.accountNumber} · {account.status}
          </Copy>
          <Button
            secondary
            title="Account details"
            onPress={() => go('/account?id=' + account.id)}
          />
        </Card>
      ))}
      {notices.data?.content
        .filter((notice) => notice.category === 'SECURITY' && !notice.read)
        .slice(0, 2)
        .map((notice) => (
          <Card key={notice.id}>
            <Heading>{notice.title}</Heading>
            <Copy>{notice.message}</Copy>
            <Button secondary title="Review security update" onPress={() => go('/notifications')} />
          </Card>
        ))}
      <Card>
        <Heading>Recent activity</Heading>
        {recent.isPending ? (
          <Loading />
        ) : (
          recent.data?.content.map((tx) => (
            <View key={tx.id} style={styles.row}>
              <Text style={styles.heading}>{tx.description}</Text>
              <Text style={styles.copy}>
                {formatMoney(tx.amount)} · {tx.type}
              </Text>
              <Text style={styles.status}>{tx.status}</Text>
              <Button secondary title="View receipt" onPress={() => go('/receipt?id=' + tx.id)} />
            </View>
          ))
        )}
        {recent.data?.content.length === 0 && (
          <Copy>Your next chapter starts here. Open an account and add simulated funds.</Copy>
        )}
      </Card>
      <Card>
        <Heading>A little more peace of mind.</Heading>
        <Copy>Add an authenticator and keep your device session protected.</Copy>
        <Button secondary title="Review security" onPress={() => go('/security')} />
      </Card>
    </Screen>
  );
}
