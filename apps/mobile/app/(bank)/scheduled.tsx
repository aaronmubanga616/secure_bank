import { View } from 'react-native';
import { type Account, type Beneficiary, type Schedule, formatMoney } from '@securebank/contracts';
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
export default function Scheduled() {
  const query = useData<Schedule[]>('/scheduled-transfers');
  const accounts = useData<Account[]>('/accounts');
  const recipients = useData<Beneficiary[]>('/beneficiaries');
  const { session } = useSession();
  return (
    <Screen title="A little planning." subtitle="Set your next payment in motion.">
      <ErrorState error={query.error ?? accounts.error ?? recipients.error} />
      <Card>
        <Heading>Your plan</Heading>
        {query.isPending ? (
          <Loading />
        ) : (
          query.data?.map((schedule) => (
            <View key={schedule.id} style={styles.row}>
              <Heading>
                {schedule.beneficiaryName} · {formatMoney(schedule.amount)}
              </Heading>
              <Copy>
                {schedule.frequency} · {new Date(schedule.nextRunAt).toLocaleString()}
              </Copy>
              <Copy>
                {schedule.status} · Last result: {schedule.lastResult ?? 'Not run yet'}{' '}
                {schedule.lastError}
              </Copy>
              {['ACTIVE', 'PAUSED'].includes(schedule.status) &&
                [schedule.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE', 'CANCELLED'].map((status) => (
                  <Form
                    key={status}
                    fields={[]}
                    submit={
                      status === 'ACTIVE'
                        ? 'Resume'
                        : status === 'PAUSED'
                          ? 'Pause'
                          : 'Cancel schedule'
                    }
                    action={async () => {
                      await api.patch('/scheduled-transfers/' + schedule.id, { status });
                    }}
                  />
                ))}
            </View>
          ))
        )}
        {query.data?.length === 0 && (
          <Copy>No scheduled transfers yet. Save a recipient to begin.</Copy>
        )}
      </Card>
      <Card>
        <Heading>Schedule a transfer</Heading>
        <Form
          fields={[
            {
              name: 'sourceAccount',
              label: 'From account',
              options:
                accounts.data?.map((account) => ({
                  value: account.id,
                  label: account.type + ' · ' + account.accountNumber,
                })) ?? [],
            },
            {
              name: 'beneficiaryId',
              label: 'Saved recipient',
              options:
                recipients.data?.map((recipient) => ({
                  value: recipient.id,
                  label: recipient.name,
                })) ?? [],
            },
            { name: 'amount', label: 'Amount (ZMW)', numeric: true },
            { name: 'description', label: 'Description' },
            {
              name: 'frequency',
              label: 'Frequency',
              options: ['ONCE', 'DAILY', 'WEEKLY', 'MONTHLY'].map((value) => ({
                value,
                label: value,
              })),
            },
            { name: 'startsAt', label: 'First payment (YYYY-MM-DDTHH:mm, device local time)' },
            ...stepUp(session?.user.mfaEnabled ?? false),
          ]}
          submit="Create schedule"
          action={async (values) => {
            const time = new Date(values.startsAt);
            if (!Number.isFinite(time.getTime()))
              throw new Error('Use a valid date and time, for example 2026-12-20T09:00.');
            await api.post('/scheduled-transfers', { ...values, startsAt: time.toISOString() });
            return 'Your transfer is scheduled.';
          }}
        />
      </Card>
    </Screen>
  );
}
