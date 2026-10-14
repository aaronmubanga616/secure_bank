import { useState } from 'react';
import {
  api,
  useSession,
  sessionStore,
  Heading,
  Notice,
  Loading,
  Empty,
  Badge,
  Field,
  TaskForm,
  values,
  dateLabel,
} from '@securebank/ui';
import {
  formatMoney,
  type Account,
  type Page,
  type Beneficiary,
  type Schedule,
  type Notification,
  type User,
} from '@securebank/contracts';
import { useData } from './data';
import { AccountCard, QueryError } from './App';

export function Accounts() {
  const query = useData<Account[]>('/accounts');
  return (
    <>
      <Heading eyebrow="YOUR EVERYDAY & YOUR PLANS" title="My accounts" />
      <QueryError error={query.error} />
      <div className="two-columns">
        <section className="panel">
          <h2>Your balances</h2>
          {query.isPending ? (
            <Loading />
          ) : (
            query.data?.map((account) => <AccountCard key={account.id} account={account} />)
          )}
          {query.data?.length === 0 && (
            <Empty title="Make yourself at home">Open a current or savings account to begin.</Empty>
          )}
        </section>
        <section className="panel">
          <h2>Make space for what’s next.</h2>
          <p className="muted">
            One everyday current account, one savings account. All balances are simulated ZMW.
          </p>
          {query.data && query.data.length < 2 && (
            <TaskForm
              submit="Open account"
              action={async (data) => {
                await api.post('/accounts', values(data));
                return 'Your new account is ready.';
              }}
            >
              <Field label="Account type">
                <select name="type">
                  {['CURRENT', 'SAVINGS']
                    .filter((type) => !query.data?.some((account) => account.type === type))
                    .map((type) => (
                      <option key={type}>{type}</option>
                    ))}
                </select>
              </Field>
            </TaskForm>
          )}
          {query.data?.length === 2 && <Notice>You have both account types.</Notice>}
        </section>
      </div>
    </>
  );
}
function StepUpFields() {
  const session = useSession();
  return (
    <>
      <Field label="Confirm password">
        <input name="password" type="password" autoComplete="current-password" required />
      </Field>
      {session?.user.mfaEnabled && (
        <Field label="Authenticator or recovery code">
          <input name="code" autoComplete="one-time-code" required />
        </Field>
      )}
    </>
  );
}
export function Recipients() {
  const query = useData<Beneficiary[]>('/beneficiaries');
  return (
    <>
      <Heading eyebrow="FAMILIAR FACES, FEWER DETAILS" title="Saved recipients" />
      <QueryError error={query.error} />
      <div className="two-columns">
        <section className="panel">
          <h2>Your people</h2>
          {query.isPending ? (
            <Loading />
          ) : (
            query.data?.map((beneficiary) => (
              <div className="list-row" key={beneficiary.id}>
                <div>
                  <h3>{beneficiary.name}</h3>
                  <p>
                    {beneficiary.fullName} · {beneficiary.accountNumber}
                  </p>
                </div>
                <TaskForm
                  submit="Remove & cancel schedules"
                  action={async () => {
                    await api.delete(`/beneficiaries/${beneficiary.id}`);
                    return 'Removed. Associated schedules are cancelled.';
                  }}
                >
                  <span className="sr-only">Remove {beneficiary.name}</span>
                </TaskForm>
              </div>
            ))
          )}
          {query.data?.length === 0 && (
            <Empty title="Keep your people close">
              Save an account to make your next payment easier.
            </Empty>
          )}
        </section>
        <section className="panel">
          <h2>Add someone new</h2>
          <TaskForm
            submit="Save recipient"
            reset
            action={async (form) => {
              await api.post('/beneficiaries', values(form));
              return 'Recipient saved.';
            }}
          >
            <Field label="Name or nickname">
              <input name="name" required maxLength={100} />
            </Field>
            <Field label="SecureBank account number">
              <input name="accountNumber" required pattern="[A-Za-z0-9]{8,20}" />
            </Field>
            <StepUpFields />
          </TaskForm>
        </section>
      </div>
    </>
  );
}
export function Schedules() {
  const query = useData<Schedule[]>('/scheduled-transfers');
  const accounts = useData<Account[]>('/accounts');
  const recipients = useData<Beneficiary[]>('/beneficiaries');
  return (
    <>
      <Heading eyebrow="A LITTLE PLANNING, A LOT OF EASE" title="Scheduled transfers" />
      <QueryError error={query.error ?? accounts.error ?? recipients.error} />
      <div className="two-columns">
        <section className="panel">
          <h2>Your plan</h2>
          {query.isPending ? (
            <Loading />
          ) : (
            query.data?.map((schedule) => (
              <div className="list-row" key={schedule.id}>
                <div>
                  <h3>
                    {schedule.beneficiaryName} · {formatMoney(schedule.amount)}
                  </h3>
                  <p>
                    {schedule.frequency} · {dateLabel(schedule.nextRunAt)}
                  </p>
                  <Badge value={schedule.status} />
                  {schedule.lastResult && (
                    <p>
                      Last result: {schedule.lastResult} {schedule.lastError}
                    </p>
                  )}
                  {['ACTIVE', 'PAUSED'].includes(schedule.status) && (
                    <div className="action-row">
                      {[schedule.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE', 'CANCELLED'].map(
                        (status) => (
                          <TaskForm
                            key={status}
                            submit={
                              status === 'ACTIVE'
                                ? 'Resume'
                                : status === 'PAUSED'
                                  ? 'Pause'
                                  : 'Cancel'
                            }
                            action={async () => {
                              await api.patch(`/scheduled-transfers/${schedule.id}`, { status });
                            }}
                          >
                            <span className="sr-only">{schedule.description}</span>
                          </TaskForm>
                        ),
                      )}
                    </div>
                  )}
                </div>
              </div>
            ))
          )}
          {query.data?.length === 0 && (
            <Empty title="Put your plans in motion">
              Save a recipient, then set up a one-off or recurring payment.
            </Empty>
          )}
        </section>
        <section className="panel">
          <h2>Set up a transfer</h2>
          <TaskForm
            submit="Create schedule"
            action={async (form) => {
              const data = values(form);
              await api.post('/scheduled-transfers', {
                ...data,
                startsAt: new Date(data.startsAt).toISOString(),
              });
              return 'Transfer scheduled. Times follow your device timezone; processing uses Africa/Lusaka.';
            }}
          >
            <Field label="From account">
              <select name="sourceAccount" required>
                <option value="">Choose account</option>
                {accounts.data?.map((account) => (
                  <option key={account.id} value={account.id}>
                    {account.type} · {account.accountNumber}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Recipient">
              <select name="beneficiaryId" required>
                <option value="">Choose saved recipient</option>
                {recipients.data?.map((recipient) => (
                  <option key={recipient.id} value={recipient.id}>
                    {recipient.name}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Amount (ZMW)">
              <input name="amount" inputMode="decimal" pattern="[0-9]+(\.[0-9]{1,2})?" required />
            </Field>
            <Field label="Description">
              <input name="description" required maxLength={160} />
            </Field>
            <Field label="Frequency">
              <select name="frequency">
                {['ONCE', 'DAILY', 'WEEKLY', 'MONTHLY'].map((frequency) => (
                  <option key={frequency}>{frequency}</option>
                ))}
              </select>
            </Field>
            <Field label="First payment (your local time)">
              <input type="datetime-local" name="startsAt" required />
            </Field>
            <StepUpFields />
          </TaskForm>
        </section>
      </div>
    </>
  );
}
export function Statements() {
  const accounts = useData<Account[]>('/accounts');
  const today = new Date().toLocaleDateString('en-CA', { timeZone: 'Africa/Lusaka' });
  return (
    <>
      <Heading eyebrow="YOUR RECORD, READY WHEN YOU ARE" title="Statements" />
      <QueryError error={accounts.error} />
      <section className="panel" style={{ maxWidth: 680 }}>
        <h2>A clearer record of your money.</h2>
        <p className="muted">
          Download a PDF with opening and closing balances and every posted transaction. Choose a
          period of up to one year.
        </p>
        <TaskForm
          submit="Download PDF"
          action={async (form) => {
            const result = await api.get('/statements', {
              params: values(form),
              responseType: 'blob',
            });
            const url = URL.createObjectURL(result.data);
            const anchor = document.createElement('a');
            anchor.href = url;
            anchor.download = 'securebank-statement.pdf';
            anchor.click();
            setTimeout(() => URL.revokeObjectURL(url), 5000);
            return 'Statement downloaded.';
          }}
        >
          <Field label="Account">
            <select name="accountId" required>
              <option value="">Choose account</option>
              {accounts.data?.map((account) => (
                <option value={account.id} key={account.id}>
                  {account.type} · {account.accountNumber}
                </option>
              ))}
            </select>
          </Field>
          <div className="form-grid">
            <Field label="From">
              <input
                name="from"
                type="date"
                defaultValue={today.slice(0, 8) + '01'}
                max={today}
                required
              />
            </Field>
            <Field label="To">
              <input name="to" type="date" defaultValue={today} max={today} required />
            </Field>
          </div>
        </TaskForm>
      </section>
    </>
  );
}
export function Notifications() {
  const [page, setPage] = useState(0);
  const query = useData<Page<Notification>>('/notifications', { page, size: 20 });
  return (
    <>
      <Heading eyebrow="STAY IN THE KNOW" title="Your inbox">
        <TaskForm
          submit="Mark all read"
          action={async () => {
            await api.post('/notifications/read-all');
          }}
        >
          <span className="sr-only">Mark all notifications as read</span>
        </TaskForm>
      </Heading>
      <QueryError error={query.error} />
      <section className="panel">
        {query.isPending ? (
          <Loading />
        ) : (
          query.data?.content.map((notice) => (
            <div key={notice.id} className={`list-row ${notice.read ? '' : 'unread'}`}>
              <div>
                <h3>{notice.title}</h3>
                <p>{notice.message}</p>
                <small className="muted">{dateLabel(notice.createdAt)}</small>
              </div>
              {!notice.read && (
                <TaskForm
                  submit="Mark read"
                  action={async () => {
                    await api.patch(`/notifications/${notice.id}/read`);
                  }}
                >
                  <span className="sr-only">{notice.title}</span>
                </TaskForm>
              )}
            </div>
          ))
        )}
        {query.data?.totalElements === 0 && (
          <Empty title="You’re all caught up">
            Account updates and payment notifications will appear here.
          </Empty>
        )}
        <div className="pager">
          <button
            className="button secondary"
            disabled={page === 0}
            onClick={() => setPage(page - 1)}
          >
            Previous
          </button>
          <span>Page {page + 1}</span>
          <button
            className="button secondary"
            disabled={!query.data || (page + 1) * 20 >= query.data.totalElements}
            onClick={() => setPage(page + 1)}
          >
            Next
          </button>
        </div>
      </section>
    </>
  );
}
export function Profile() {
  const session = useSession();
  const profile = useData<User>('/users/me');
  return (
    <>
      <Heading eyebrow="YOUR DETAILS" title="Make yourself at home." />
      <QueryError error={profile.error} />
      <section className="panel" style={{ maxWidth: 650 }}>
        <h2>Personal information</h2>
        {profile.data && (
          <TaskForm
            submit="Save profile"
            action={async (form) => {
              const { data } = await api.patch<User>('/users/me', values(form));
              if (session) sessionStore.write({ ...session, user: data });
            }}
          >
            <Field label="Full name">
              <input
                name="fullName"
                defaultValue={profile.data.fullName}
                minLength={2}
                maxLength={100}
                autoComplete="name"
                required
              />
            </Field>
            <Field label="Email address">
              <input value={profile.data.email} readOnly />
            </Field>
            <Notice>Your email is verified. Your banking details remain private.</Notice>
          </TaskForm>
        )}
      </section>
    </>
  );
}
