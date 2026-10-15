import { useState } from 'react';
import { Text, View } from 'react-native';
import { type Page, type Transaction, formatMoney, lusakaRange } from '@securebank/contracts';
import {
  Screen,
  Card,
  Copy,
  Form,
  Button,
  ErrorState,
  Loading,
  useData,
  styles,
  go,
} from '../../src/ui';
export default function Activity() {
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState<Record<string, string>>({});
  const query = useData<Page<Transaction>>('/transactions', { page, size: 15, ...filters });
  return (
    <Screen
      title="Your everyday, in detail."
      subtitle="Search and review your transaction history."
    >
      <Card>
        <Form
          fields={[
            { name: 'query', label: 'Search description or reference', optional: true },
            {
              name: 'status',
              label: 'Status',
              optional: true,
              options: [
                { value: '', label: 'All' },
                { value: 'COMPLETED', label: 'Completed' },
                { value: 'FLAGGED', label: 'Under review' },
                { value: 'FAILED', label: 'Declined' },
              ],
            },
            { name: 'from', label: 'From date (YYYY-MM-DD)', optional: true },
            { name: 'to', label: 'To date (YYYY-MM-DD)', optional: true },
            { name: 'minimum', label: 'Minimum amount', numeric: true, optional: true },
            { name: 'maximum', label: 'Maximum amount', numeric: true, optional: true },
          ]}
          submit="Apply filters"
          action={async (values) => {
            const next = { ...values };
            Object.assign(next, lusakaRange(next.from, next.to));
            setFilters(Object.fromEntries(Object.entries(next).filter(([, value]) => value)));
            setPage(0);
          }}
        />
      </Card>
      <ErrorState error={query.error} />
      <Card>
        {query.isPending ? (
          <Loading />
        ) : (
          query.data?.content.map((tx) => (
            <View key={tx.id} style={styles.row}>
              <Text style={styles.heading}>{tx.description}</Text>
              <Copy>
                {formatMoney(tx.amount)} · {tx.direction} ·{' '}
                {new Date(tx.createdAt).toLocaleDateString()}
              </Copy>
              <Text style={styles.status}>{tx.status}</Text>
              <Button secondary title="View receipt" onPress={() => go('/receipt?id=' + tx.id)} />
            </View>
          ))
        )}
        {query.data?.totalElements === 0 && <Copy>No transactions match these filters.</Copy>}
        <Copy>
          Page {page + 1} · {query.data?.totalElements ?? 0} transactions
        </Copy>
        <Button
          secondary
          title="Previous"
          disabled={page === 0}
          onPress={() => setPage(page - 1)}
        />
        <Button
          secondary
          title="Next"
          disabled={!query.data || (page + 1) * 15 >= query.data.totalElements}
          onPress={() => setPage(page + 1)}
        />
      </Card>
    </Screen>
  );
}
