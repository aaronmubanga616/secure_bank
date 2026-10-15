import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  api,
  Heading,
  Notice,
  Loading,
  Empty,
  Badge,
  Field,
  TaskForm,
  values,
  dateLabel,
  useSession,
} from '@securebank/ui';
import { type Page, type Transaction, formatMoney, lusakaRange } from '@securebank/contracts';
import { useData } from './data';
import { QueryError, TransactionTable } from './App';
export function Transactions() {
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState<Record<string, string>>({});
  const query = useData<Page<Transaction>>('/admin/transactions', { page, size: 20, ...filters });
  return (
    <>
      <Heading eyebrow="OPERATIONS / MONEY MOVEMENTS" title="Every movement, accounted for." />
      <section className="panel">
        <TaskForm
          submit="Apply filters"
          action={async (form) => {
            const next = values(form);
            Object.assign(next, lusakaRange(next.from, next.to));
            setFilters(Object.fromEntries(Object.entries(next).filter(([, value]) => value)));
            setPage(0);
          }}
        >
          <div className="toolbar">
            <Field label="Description or reference">
              <input name="query" maxLength={100} />
            </Field>
            <Field label="Status">
              <select name="status">
                <option value="">All statuses</option>
                {['COMPLETED', 'FAILED', 'FLAGGED', 'PROCESSING', 'PENDING'].map((value) => (
                  <option key={value}>{value}</option>
                ))}
              </select>
            </Field>
            <Field label="From date">
              <input type="date" name="from" />
            </Field>
            <Field label="To date">
              <input type="date" name="to" />
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
          <button className="button secondary" disabled={!page} onClick={() => setPage(page - 1)}>
            Previous
          </button>
          <span>
            Page {page + 1} · {query.data?.totalElements ?? 0} transactions
          </span>
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
interface TxDetail {
  transaction: Transaction;
  customerId: string;
  journal: { direction: string; amount: string; code: string }[];
}
export function TransactionDetail() {
  const { id } = useParams();
  const query = useData<TxDetail>('/admin/transactions/' + id);
  const tx = query.data?.transaction;
  return (
    <>
      <Heading
        eyebrow="TRANSACTION INVESTIGATION"
        title={tx?.description ?? 'Transaction details'}
      />
      <QueryError error={query.error} />
      {query.isPending ? (
        <Loading />
      ) : (
        tx && (
          <div className="two-columns">
            <section className="panel">
              <h2>{formatMoney(tx.amount)}</h2>
              <Badge value={tx.status} />
              <dl className="receipt">
                {Object.entries({
                  Reference: tx.reference,
                  Type: tx.type,
                  From: tx.sourceAccountNumber ?? 'Simulated cash',
                  To: tx.destinationAccountNumber ?? 'Simulated cash',
                  Risk: `${tx.riskScore}/100`,
                  Submitted: dateLabel(tx.createdAt),
                  Posted: tx.completedAt ? dateLabel(tx.completedAt) : 'Not posted',
                  Decline: tx.failureReason ?? 'None',
                }).map(([label, value]) => (
                  <div key={label}>
                    <dt>{label}</dt>
                    <dd>{value}</dd>
                  </div>
                ))}
              </dl>
              <Link className="text-button" to={'/users/' + query.data?.customerId}>
                Open customer record ↗
              </Link>
            </section>
            <section className="panel">
              <h2>Double-entry journal</h2>
              {query.data?.journal.map((line) => (
                <div className="list-row" key={line.direction}>
                  <div>
                    <h3>
                      {line.direction} · {formatMoney(line.amount)}
                    </h3>
                    <p style={{ overflowWrap: 'anywhere' }}>{line.code}</p>
                  </div>
                </div>
              ))}
              {!query.data?.journal.length && (
                <Notice>No funds have moved. This transaction has no posted journal.</Notice>
              )}
              <p className="muted" style={{ fontSize: 12, marginTop: 20 }}>
                Posted entries are immutable. Investigation outcomes never erase financial history.
              </p>
            </section>
          </div>
        )
      )}
    </>
  );
}
interface Alert {
  id: string;
  user_id: string;
  transaction_id: string;
  score: number;
  reasons: string;
  status: string;
  review_note: string | null;
  reviewed_by: string | null;
  created_at: string;
  full_name: string;
  email: string;
  reference: string;
  amount: string;
  transaction_status: string;
}
export function Fraud() {
  const session = useSession();
  const permitted = session?.user.role !== 'SUPPORT_AGENT';
  const [status, setStatus] = useState('OPEN');
  const query = useData<Alert[]>('/fraud', { status }, permitted);
  if (!permitted) return <Notice error>Your role does not allow fraud investigations.</Notice>;
  return (
    <>
      <Heading eyebrow="OPERATIONS / FRAUD REVIEW" title="Attention, with context." />
      <div className="toolbar">
        <Field label="Investigation status">
          <select value={status} onChange={(event) => setStatus(event.target.value)}>
            {['OPEN', 'RESOLVED', 'FALSE_POSITIVE', 'CONFIRMED_FRAUD'].map((value) => (
              <option key={value}>{value}</option>
            ))}
          </select>
        </Field>
      </div>
      <QueryError error={query.error} />
      <p className="muted" style={{ fontSize: 11 }}>
        Latest 200 investigations matching this status.
      </p>
      {query.isPending ? (
        <Loading />
      ) : !query.data?.length ? (
        <Empty title="No investigations here">
          New alerts will appear as unusual activity is detected.
        </Empty>
      ) : (
        query.data.map((alert) => (
          <section className="panel" key={alert.id} style={{ marginBottom: 20 }}>
            <div className="panel-head">
              <h2>
                {alert.full_name} · {formatMoney(alert.amount)}
              </h2>
              <Badge value={alert.status} />
            </div>
            <div className="two-columns">
              <div>
                <dl className="receipt">
                  <div>
                    <dt>Risk score</dt>
                    <dd>{alert.score}/100</dd>
                  </div>
                  <div>
                    <dt>Signals</dt>
                    <dd>{alert.reasons.replaceAll('_', ' ').replaceAll(',', ', ')}</dd>
                  </div>
                  <div>
                    <dt>Reference</dt>
                    <dd>{alert.reference}</dd>
                  </div>
                  <div>
                    <dt>Transaction status</dt>
                    <dd>
                      <Badge value={alert.transaction_status} />
                    </dd>
                  </div>
                  <div>
                    <dt>Opened</dt>
                    <dd>{dateLabel(alert.created_at)}</dd>
                  </div>
                </dl>
                <div className="action-row">
                  <Link className="text-button" to={'/transactions/' + alert.transaction_id}>
                    Open transaction ↗
                  </Link>
                  <Link className="text-button" to={'/users/' + alert.user_id}>
                    Open customer ↗
                  </Link>
                </div>
                {alert.review_note && <Notice>{alert.review_note}</Notice>}
              </div>
              {alert.status === 'OPEN' && (
                <TaskForm
                  submit="Record decision"
                  action={async (form) => {
                    await api.post(`/fraud/${alert.id}/review`, values(form));
                    return 'Review recorded. Refresh the transaction to see its current status.';
                  }}
                >
                  <Field label="Review outcome">
                    <select name="outcome">
                      <option value="FALSE_POSITIVE">False positive — approve held payment</option>
                      <option value="RESOLVED">Resolved — approve held payment</option>
                      <option value="CONFIRMED_FRAUD">Confirmed fraud — reject held payment</option>
                    </select>
                  </Field>
                  <Field label="Investigation note">
                    <textarea name="note" minLength={10} maxLength={500} rows={3} required />
                  </Field>
                  <small className="muted">
                    Approval checks funds and account status again. Closing an investigation on a
                    posted payment does not reverse it.
                  </small>
                </TaskForm>
              )}
            </div>
          </section>
        ))
      )}
    </>
  );
}
interface AuditRow {
  id: string;
  userId: string | null;
  action: string;
  result: string;
  resource: string;
  correlationId: string;
  createdAt: string;
}
export function Audit() {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const query = useData<Page<AuditRow>>('/admin/audit', { page, size: 20, query: search });
  return (
    <>
      <Heading eyebrow="OPERATIONS / ACCOUNTABILITY" title="A record you can follow." />
      <section className="panel">
        <TaskForm
          submit="Search audit trail"
          action={async (form) => {
            setSearch(String(form.get('query') ?? ''));
            setPage(0);
          }}
        >
          <Field label="Action, resource, or correlation ID">
            <input name="query" maxLength={100} />
          </Field>
        </TaskForm>
        <QueryError error={query.error} />
        {query.isPending ? (
          <Loading />
        ) : (
          <div className="table-scroll" style={{ marginTop: 24 }}>
            <table>
              <thead>
                <tr>
                  <th>Action</th>
                  <th>Result</th>
                  <th>Resource</th>
                  <th>Actor / correlation</th>
                  <th>Time</th>
                </tr>
              </thead>
              <tbody>
                {query.data?.content.map((row) => (
                  <tr key={row.id}>
                    <td>{row.action}</td>
                    <td>
                      <Badge value={row.result} />
                    </td>
                    <td>{row.resource}</td>
                    <td>
                      <small>
                        {row.userId ?? 'Anonymous'}
                        <br />
                        {row.correlationId}
                      </small>
                    </td>
                    <td>{dateLabel(row.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div className="pager">
          <button className="button secondary" disabled={!page} onClick={() => setPage(page - 1)}>
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
export function Settings() {
  const session = useSession();
  const admin = session?.user.role === 'ADMIN';
  const permitted = session?.user.role !== 'SUPPORT_AGENT';
  const limits = useData<Record<string, string>>('/admin/limits', undefined, permitted);
  const rules = useData<{ largeAmount: string; velocityCount: number; holdScore: number }>(
    '/admin/fraud-rules',
    undefined,
    permitted,
  );
  if (!permitted) return <Notice error>Your role cannot change configuration.</Notice>;
  return (
    <>
      <Heading eyebrow="OPERATIONS / POLICY" title="Thoughtful boundaries." />
      <QueryError error={limits.error ?? rules.error} />
      <div className="two-columns">
        <section className="panel">
          <h2>Transaction limits (ZMW)</h2>
          {limits.data && (
            <TaskForm
              submit="Update limits"
              action={async (form) => {
                if (!admin) throw new Error('Administrator access is required.');
                await api.patch('/admin/limits', values(form));
              }}
            >
              {Object.entries(limits.data).map(([name, amount]) => (
                <Field key={name} label={name.replace(/([A-Z])/g, ' $1')}>
                  <input
                    name={name}
                    defaultValue={amount}
                    type="number"
                    min="0.01"
                    max="1000000000"
                    step="0.01"
                    readOnly={!admin}
                    required
                  />
                </Field>
              ))}
            </TaskForm>
          )}
        </section>
        <section className="panel">
          <h2>Fraud rule thresholds</h2>
          {rules.data && (
            <TaskForm
              submit="Update fraud rules"
              action={async (form) => {
                if (!admin) throw new Error('Administrator access is required.');
                const data = values(form);
                await api.patch(
                  '/admin/fraud-rules',
                  Object.fromEntries(
                    Object.entries(data).map(([key, value]) => [key, Number(value)]),
                  ),
                );
              }}
            >
              <Field label="Large transfer amount (K)">
                <input
                  name="largeAmount"
                  type="number"
                  min="1"
                  max="1000000000"
                  step="1"
                  defaultValue={rules.data.largeAmount}
                  readOnly={!admin}
                  required
                />
              </Field>
              <Field label="Transfers in five minutes">
                <input
                  name="velocityCount"
                  type="number"
                  min="2"
                  max="100"
                  defaultValue={rules.data.velocityCount}
                  readOnly={!admin}
                  required
                />
              </Field>
              <Field label="Hold at risk score">
                <input
                  name="holdScore"
                  type="number"
                  min="31"
                  max="100"
                  defaultValue={rules.data.holdScore}
                  readOnly={!admin}
                  required
                />
              </Field>
            </TaskForm>
          )}
        </section>
      </div>
      {admin && (
        <section className="panel" style={{ marginTop: 25, maxWidth: 650 }}>
          <h2>Create a staff account</h2>
          <TaskForm
            submit="Create staff member"
            reset
            action={async (form) => {
              await api.post('/admin/staff', values(form));
              return 'Staff account created. Share credentials privately and ask them to enroll an authenticator.';
            }}
          >
            <Field label="Staff full name">
              <input name="fullName" minLength={2} maxLength={100} required />
            </Field>
            <Field label="Staff email">
              <input name="email" type="email" maxLength={254} required />
            </Field>
            <Field label="Role">
              <select name="role">
                <option>SUPPORT_AGENT</option>
                <option>COMPLIANCE_OFFICER</option>
                <option>ADMIN</option>
              </select>
            </Field>
            <Field label="Initial password">
              <input
                name="password"
                type="password"
                autoComplete="new-password"
                minLength={12}
                maxLength={72}
                required
              />
            </Field>
          </TaskForm>
        </section>
      )}
    </>
  );
}
