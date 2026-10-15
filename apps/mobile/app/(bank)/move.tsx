import { useRef, useState } from 'react';
import { Text } from 'react-native';
import * as Crypto from 'expo-crypto';
import { router, useLocalSearchParams } from 'expo-router';
import {
  type Account,
  type Beneficiary,
  type Transaction,
  formatMoney,
  normalizeAmount,
  paymentKeyKeeper,
} from '@securebank/contracts';
import { api } from '../../src/session';
import {
  Screen,
  Card,
  Copy,
  Heading,
  Form,
  Button,
  ErrorState,
  useData,
  go,
  type InputField,
} from '../../src/ui';
interface Draft {
  type: string;
  sourceAccount: string | null;
  destinationAccountNumber: string | null;
  amount: string;
  description: string;
}
export default function Move() {
  const params = useLocalSearchParams<{ type?: string }>();
  const type = ['TRANSFER', 'DEPOSIT', 'WITHDRAWAL'].includes(params.type ?? '')
    ? params.type!
    : 'TRANSFER';
  const [keyFor] = useState(() => paymentKeyKeeper(Crypto.randomUUID));
  return <MoveFlow key={type} type={type} keyFor={keyFor} />;
}
function MoveFlow({ type, keyFor }: { type: string; keyFor: ReturnType<typeof paymentKeyKeeper> }) {
  const accounts = useData<Account[]>('/accounts');
  const recipients = useData<Beneficiary[]>('/beneficiaries');
  const [draft, setDraft] = useState<Draft | null>(null);
  const [recipient, setRecipient] = useState('');
  const key = useRef('');
  const fields: InputField[] = [
    {
      name: 'account',
      label: type === 'DEPOSIT' ? 'To account' : 'From account',
      options:
        accounts.data
          ?.filter((account) => account.status === 'ACTIVE')
          .map((account) => ({
            value: account.id,
            label: `${account.type} · ${account.accountNumber} · ${formatMoney(account.balance)}`,
          })) ?? [],
    },
    ...(type === 'TRANSFER'
      ? [{ name: 'destinationAccountNumber', label: 'Recipient account number' }]
      : []),
    { name: 'amount', label: 'Amount (ZMW)', numeric: true },
    { name: 'description', label: 'Description' },
  ];
  return (
    <Screen title="Move money." subtitle="A little momentum for your everyday.">
      <ErrorState error={accounts.error ?? recipients.error} />
      <Card>
        {draft ? (
          <>
            <Heading>One last look.</Heading>
            <Text style={{ fontSize: 34, color: '#254c37' }}>{formatMoney(draft.amount)}</Text>
            <Copy>
              {draft.type} · {draft.description}
            </Copy>
            <Copy>
              From:{' '}
              {draft.sourceAccount
                ? accounts.data?.find((account) => account.id === draft.sourceAccount)
                    ?.accountNumber
                : 'Simulated cash'}
            </Copy>
            <Copy>To: {recipient || 'Simulated cash'}</Copy>
            <Form
              fields={[]}
              submit="Confirm transaction"
              action={async () => {
                const { data } = await api.post<Transaction>('/transactions', draft, {
                  headers: { 'Idempotency-Key': key.current },
                });
                keyFor.completed(draft);
                setDraft(null);
                go('/receipt?id=' + data.id);
              }}
            >
              <Copy>
                If your connection fails, retry here to safely check the same transaction. All funds
                are simulated.
              </Copy>
            </Form>
            <Button secondary title="Edit details" onPress={() => setDraft(null)} />
          </>
        ) : (
          <>
            <Heading>What would you like to do?</Heading>
            {[
              ['TRANSFER', 'Send money'],
              ['DEPOSIT', 'Add simulated funds'],
              ['WITHDRAWAL', 'Withdraw simulated funds'],
            ].map(([value, label]) => (
              <Button
                key={value}
                secondary={type !== value}
                title={label}
                onPress={() => router.setParams({ type: value })}
              />
            ))}
            <Form
              key={type}
              fields={fields}
              submit="Review transaction"
              action={async (values) => {
                const amount = normalizeAmount(values.amount);
                let destination: string | null = null;
                let label = '';
                if (type === 'DEPOSIT') {
                  destination =
                    accounts.data?.find((account) => account.id === values.account)
                      ?.accountNumber ?? null;
                  label = destination ?? '';
                }
                if (type === 'TRANSFER') {
                  const { data } = await api.get<{ fullName: string; accountNumber: string }>(
                    '/accounts/lookup',
                    { params: { number: values.destinationAccountNumber } },
                  );
                  destination = data.accountNumber;
                  label = data.fullName + ' · ' + data.accountNumber;
                }
                setRecipient(label);
                const next = {
                  type,
                  sourceAccount: type === 'DEPOSIT' ? null : values.account,
                  destinationAccountNumber: destination,
                  amount,
                  description: values.description,
                };
                key.current = keyFor(next);
                setDraft(next);
              }}
            />
            {type === 'TRANSFER' &&
              recipients.data?.map((recipient) => (
                <Copy key={recipient.id}>
                  {recipient.name}: {recipient.accountNumber}
                </Copy>
              ))}
          </>
        )}
      </Card>
      <Card>
        <Heading>Every detail matters.</Heading>
        <Copy>
          Review your recipient before confirming. Some transfers need staff review; no money moves
          while a transfer is held.
        </Copy>
      </Card>
    </Screen>
  );
}
