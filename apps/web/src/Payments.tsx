import { useRef, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { ShieldCheck } from 'lucide-react';
import {
  api,
  queryClient,
  Heading,
  Notice,
  Loading,
  Badge,
  Field,
  TaskForm,
  values,
  dateLabel,
} from '@securebank/ui';
import {
  formatMoney,
  normalizeAmount,
  paymentKeyKeeper,
  lusakaRange,
  type Account,
  type Transaction,
  type Page,
  type Beneficiary,
} from '@securebank/contracts';
import { useData } from './data';
import { QueryError, TransactionTable } from './App';

export function History() {
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState<Record<string, string>>({});
  const query = useData<Page<Transaction>>('/transactions', { page, size: 15, ...filters });
  return (
    <>
      <Heading eyebrow="THE DETAILS BEHIND YOUR EVERYDAY" title="Transactions" />
      <section className="panel">
        <TaskForm
          submit="Apply filters"
          action={async (data) => {
            const fields = values(data);
            Object.assign(fields, lusakaRange(fields.from, fields.to));
            setFilters(Object.fromEntries(Object.entries(fields).filter(([, value]) => value)));
            setPage(0);
          }}
        >
          <div className="toolbar">
            <Field label="Search">
              <input name="query" placeholder="Description or reference" maxLength={100} />
            </Field>
            <Field label="Status">
              <select name="status">
                <option value="">All transactions</option>
                {['COMPLETED', 'FAILED', 'FLAGGED'].map((status) => (
                  <option key={status}>{status}</option>
                ))}
              </select>
            </Field>
            <Field label="From">
              <input name="from" type="date" />
            </Field>
            <Field label="To">
              <input name="to" type="date" />
            </Field>
            <Field label="Minimum K">
              <input name="minimum" type="number" min="0" step="0.01" />
            </Field>
            <Field label="Maximum K">
              <input name="maximum" type="number" min="0" step="0.01" />
            </Field>
          </div>
        </TaskForm>
        <QueryError error={query.error} />
        {query.isPending ? <Loading /> : <TransactionTable rows={query.data?.content ?? []} />}
        <div className="pager">
          <button
            className="button secondary"
            disabled={page === 0}
            onClick={() => setPage(page - 1)}
          >
            Previous
          </button>
          <span>
            Page {page + 1} · {query.data?.totalElements ?? 0} transactions
          </span>
          <button
            className="button secondary"
            disabled={!query.data || (page + 1) * 15 >= query.data.totalElements}
            onClick={() => setPage(page + 1)}
          >
            Next
          </button>
        </div>
      </section>
    </>
  );
}
export function Receipt() {
  const { id } = useParams();
  const query = useData<Transaction>(`/transactions/${id}`);
  const row = query.data;
  return (
    <>
      <Heading eyebrow="TRANSACTION DETAILS" title="A clear record." />
      <QueryError error={query.error} />
      {query.isPending ? (
        <Loading />
      ) : (
        row && (
          <section className="panel" style={{ maxWidth: 760 }}>
            <div className="panel-head">
              <h2>{row.description}</h2>
              <Badge value={row.status} />
            </div>
            <h1 className="amount" style={{ margin: '20px 0' }}>
              {formatMoney(row.amount)}
            </h1>
            {row.status === 'FLAGGED' && (
              <Notice>This transfer is under staff review. No funds have moved.</Notice>
            )}
            {row.status === 'FAILED' && (
              <Notice error>
                No funds moved. {row.failureReason?.replaceAll('_', ' ').toLowerCase()}.
              </Notice>
            )}
            <dl className="receipt">
              {Object.entries({
                Reference: row.reference,
                Type: row.type,
                From: row.sourceAccountNumber ?? 'Simulated cash',
                To: row.destinationAccountNumber ?? 'Simulated cash',
                Submitted: dateLabel(row.createdAt),
                Completed: row.completedAt ? dateLabel(row.completedAt) : 'Not posted',
                Currency: 'ZMW · simulated funds',
              }).map(([label, value]) => (
                <div key={label}>
                  <dt>{label}</dt>
                  <dd>{value}</dd>
                </div>
              ))}
            </dl>
            <div className="action-row">
              <Link className="button secondary" to="/transactions">
                All transactions
              </Link>
              <Link className="button" to="/move">
                Make another transaction
              </Link>
            </div>
          </section>
        )
      )}
    </>
  );
}
interface Draft {
  type: string;
  sourceAccount: string | null;
  destinationAccountNumber: string | null;
  amount: string;
  description: string;
}
export function MoveMoney() {
  const [params] = useSearchParams();
  const initial = params.get('type') ?? 'TRANSFER';
  const [type, setType] = useState(
    ['TRANSFER', 'DEPOSIT', 'WITHDRAWAL'].includes(initial) ? initial : 'TRANSFER',
  );
  const [draft, setDraft] = useState<Draft | null>(null);
  const [recipient, setRecipient] = useState('');
  const [done, setDone] = useState(false);
  const key = useRef('');
  const [keyFor] = useState(() => paymentKeyKeeper(() => crypto.randomUUID()));
  const navigate = useNavigate();
  const accounts = useData<Account[]>('/accounts');
  const recipients = useData<Beneficiary[]>('/beneficiaries');
  const limits = useData<Record<string, string>>('/transactions/limits');
  return (
    <>
      <Heading eyebrow="A LITTLE MOMENTUM" title="Move money" />
      <QueryError error={accounts.error ?? recipients.error ?? limits.error} />
      <div className="two-columns">
        <section className="panel">
          {draft ? (
            <>
              <h2>One last look.</h2>
              <dl className="receipt">
                {Object.entries({
                  Action: draft.type,
                  Amount: formatMoney(draft.amount),
                  From:
                    accounts.data?.find((account) => account.id === draft.sourceAccount)
                      ?.accountNumber ?? 'Simulated cash',
                  To: recipient || 'Simulated cash',
                  Description: draft.description,
                }).map(([label, value]) => (
                  <div key={label}>
                    <dt>{label}</dt>
                    <dd>{value}</dd>
                  </div>
                ))}
              </dl>
              <TaskForm
                submit="Confirm transaction"
                action={async () => {
                  if (done) return;
                  const result = (
                    await api.post<Transaction>('/transactions', draft, {
                      headers: { 'Idempotency-Key': key.current },
                    })
                  ).data;
                  keyFor.completed(draft);
                  setDone(true);
                  await queryClient.invalidateQueries();
                  navigate(`/transactions/${result.id}`);
                }}
              >
                <Notice>
                  Simulated funds only. If the connection fails, retry here to safely check the same
                  transaction.
                </Notice>
                <button className="text-button" type="button" onClick={() => setDraft(null)}>
                  Edit details
                </button>
              </TaskForm>
            </>
          ) : (
            <TaskForm
              submit="Review transaction"
              action={async (form) => {
                const data = values(form);
                let destination = data.destinationAccountNumber || null;
                let name = '';
                if (type === 'DEPOSIT') {
                  const target = accounts.data?.find((account) => account.id === data.destination);
                  destination = target?.accountNumber ?? null;
                  name = destination ?? '';
                }
                if (type === 'TRANSFER') {
                  const lookup = (
                    await api.get<{ fullName: string; accountNumber: string }>('/accounts/lookup', {
                      params: { number: destination },
                    })
                  ).data;
                  name = `${lookup.fullName} · ${lookup.accountNumber}`;
                }
                const amount = normalizeAmount(data.amount);
                const next = {
                  type,
                  sourceAccount: type === 'DEPOSIT' ? null : data.sourceAccount,
                  destinationAccountNumber: type === 'WITHDRAWAL' ? null : destination,
                  amount,
                  description: data.description,
                };
                key.current = keyFor(next);
                setRecipient(name);
                setDraft(next);
                setDone(false);
              }}
            >
              <Field label="What would you like to do?">
                <select value={type} onChange={(event) => setType(event.target.value)}>
                  <option value="TRANSFER">Send money</option>
                  <option value="DEPOSIT">Add simulated funds</option>
                  <option value="WITHDRAWAL">Withdraw simulated funds</option>
                </select>
              </Field>
              {type !== 'DEPOSIT' && (
                <Field label="From account">
                  <select name="sourceAccount" required>
                    <option value="">Choose account</option>
                    {accounts.data
                      ?.filter((account) => account.status === 'ACTIVE')
                      .map((account) => (
                        <option key={account.id} value={account.id}>
                          {account.type} · {account.accountNumber} · {formatMoney(account.balance)}
                        </option>
                      ))}
                  </select>
                </Field>
              )}
              {type === 'DEPOSIT' && (
                <Field label="To account">
                  <select name="destination" required>
                    <option value="">Choose account</option>
                    {accounts.data
                      ?.filter((account) => account.status === 'ACTIVE')
                      .map((account) => (
                        <option key={account.id} value={account.id}>
                          {account.type} · {account.accountNumber}
                        </option>
                      ))}
                  </select>
                </Field>
              )}
              {type === 'TRANSFER' && (
                <Field label="Recipient account number">
                  <input
                    name="destinationAccountNumber"
                    list="recipients"
                    pattern="[A-Za-z0-9]{8,20}"
                    placeholder="SB1000000001"
                    required
                  />
                  <datalist id="recipients">
                    {recipients.data?.map((beneficiary) => (
                      <option key={beneficiary.id} value={beneficiary.accountNumber}>
                        {beneficiary.name}
                      </option>
                    ))}
                  </datalist>
                </Field>
              )}
              <Field label="Amount (ZMW)">
                <input
                  name="amount"
                  inputMode="decimal"
                  pattern="[0-9]+(\.[0-9]{1,2})?"
                  placeholder="0.00"
                  required
                />
              </Field>
              <Field label="What’s it for?">
                <input name="description" maxLength={160} placeholder="A little context" required />
              </Field>
            </TaskForm>
          )}
        </section>
        <div className="main-stack">
          <section className="safety">
            <ShieldCheck size={26} />
            <h3>Every detail matters.</h3>
            <p>
              Review the recipient and amount before confirming. Transfers between SecureBank
              accounts are processed together, keeping both balances accurate.
            </p>
            <p>Some activity may need a staff review before money moves.</p>
          </section>
          <section className="panel">
            <h2>Your transaction limits</h2>
            <dl className="receipt">
              {Object.entries(limits.data ?? {}).map(([label, amount]) => (
                <div key={label}>
                  <dt>{label.replace(/([A-Z])/g, ' $1')}</dt>
                  <dd>{formatMoney(amount)}</dd>
                </div>
              ))}
            </dl>
          </section>
        </div>
      </div>
    </>
  );
}
