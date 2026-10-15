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
import { type User, type Account, type Page, formatMoney } from '@securebank/contracts';
import { QueryError } from './App';
import { useData } from './data';
interface Customer extends User {
  createdAt: string;
}
interface Detail {
  user: Customer;
  accounts: Account[];
}
interface StaffAction {
  actor_id: string;
  action: string;
  reason: string;
  created_at: string;
}
export function Customers() {
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState<Record<string, string>>({});
  const query = useData<Page<Customer>>('/admin/users', { page, size: 20, ...filters });
  return (
    <>
      <Heading eyebrow="OPERATIONS / CUSTOMERS" title="The people behind the numbers." />
      <section className="panel">
        <TaskForm
          submit="Find customers"
          action={async (form) => {
            setFilters(
              Object.fromEntries(Object.entries(values(form)).filter(([, value]) => value)),
            );
            setPage(0);
          }}
        >
          <div className="toolbar">
            <Field label="Name, email, or customer ID">
              <input name="query" maxLength={100} placeholder="Search customers" />
            </Field>
            <Field label="Access status">
              <select name="status">
                <option value="">All customers</option>
                <option>ACTIVE</option>
                <option>SUSPENDED</option>
              </select>
            </Field>
          </div>
        </TaskForm>
        <QueryError error={query.error} />
        {query.isPending ? (
          <Loading />
        ) : query.data?.content.length ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Customer</th>
                  <th>Email</th>
                  <th>Access</th>
                  <th>Authenticator</th>
                  <th>Joined</th>
                </tr>
              </thead>
              <tbody>
                {query.data.content.map((customer) => (
                  <tr key={customer.id}>
                    <td>
                      <Link className="text-button" to={'/users/' + customer.id}>
                        {customer.fullName}
                      </Link>
                    </td>
                    <td>{customer.email}</td>
                    <td>
                      <Badge value={customer.status} />
                    </td>
                    <td>{customer.mfaEnabled ? 'Enabled' : 'Not enabled'}</td>
                    <td>{new Date(customer.createdAt).toLocaleDateString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <Empty title="No customers found">Try a different name, email, or status.</Empty>
        )}
        <div className="pager">
          <button className="button secondary" disabled={!page} onClick={() => setPage(page - 1)}>
            Previous
          </button>
          <span>
            Page {page + 1} · {query.data?.totalElements ?? 0} customers
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
export function CustomerDetail() {
  const { id } = useParams();
  const session = useSession();
  const query = useData<Detail>('/admin/users/' + id);
  const actions = useData<StaffAction[]>('/admin/actions', { resource: 'users/' + id });
  const admin = session?.user.role === 'ADMIN';
  const detail = query.data;
  return (
    <>
      <Heading eyebrow="CUSTOMER RECORD" title={detail?.user.fullName ?? 'Customer details'} />
      <QueryError error={query.error ?? actions.error} />
      {query.isPending ? (
        <Loading />
      ) : (
        detail && (
          <>
            <div className="two-columns">
              <section className="panel">
                <h2>Account holder</h2>
                <dl className="receipt">
                  <div>
                    <dt>Email</dt>
                    <dd>{detail.user.email}</dd>
                  </div>
                  <div>
                    <dt>Customer ID</dt>
                    <dd>{detail.user.id}</dd>
                  </div>
                  <div>
                    <dt>Access</dt>
                    <dd>
                      <Badge value={detail.user.status} />
                    </dd>
                  </div>
                  <div>
                    <dt>Verified email</dt>
                    <dd>{detail.user.emailVerified ? 'Yes' : 'No'}</dd>
                  </div>
                  <div>
                    <dt>Authenticator</dt>
                    <dd>{detail.user.mfaEnabled ? 'Enabled' : 'Not enabled'}</dd>
                  </div>
                </dl>
                {admin && (
                  <>
                    <h2 style={{ marginTop: 25 }}>Update customer access</h2>
                    <TaskForm
                      submit="Update access"
                      action={async (form) => {
                        await api.patch(`/admin/users/${id}/status`, values(form));
                        return 'Access updated. Existing sessions have been revoked.';
                      }}
                    >
                      <Field label="New access status">
                        <select name="status" defaultValue={detail.user.status}>
                          <option>ACTIVE</option>
                          <option>SUSPENDED</option>
                        </select>
                      </Field>
                      <Field label="Reason for access change">
                        <textarea name="reason" minLength={10} maxLength={500} required rows={3} />
                      </Field>
                    </TaskForm>
                  </>
                )}
              </section>
              <section className="panel">
                <h2>Customer accounts</h2>
                {detail.accounts.map((account) => (
                  <div className="account-card" key={account.id}>
                    <div className="account-top">
                      <span>
                        {account.type} · {account.accountNumber}
                      </span>
                      <Badge value={account.status} />
                    </div>
                    <p className="amount">{formatMoney(account.balance)}</p>
                    {admin && (
                      <TaskForm
                        submit={account.status === 'FROZEN' ? 'Unfreeze account' : 'Freeze account'}
                        action={async (form) => {
                          await api.patch(`/admin/accounts/${account.id}/status`, {
                            status: account.status === 'FROZEN' ? 'ACTIVE' : 'FROZEN',
                            reason: form.get('reason'),
                          });
                          return 'Account status updated.';
                        }}
                      >
                        <Field label={`Reason for ${account.type.toLowerCase()} status change`}>
                          <input name="reason" minLength={10} maxLength={500} required />
                        </Field>
                      </TaskForm>
                    )}
                  </div>
                ))}
                {!detail.accounts.length && (
                  <Empty title="No accounts yet">This customer has not opened an account.</Empty>
                )}
              </section>
            </div>
            <section className="panel" style={{ marginTop: 25 }}>
              <h2>Access change history</h2>
              {actions.data?.map((action, index) => (
                <div className="list-row" key={index}>
                  <div>
                    <h3>{action.action.replaceAll('_', ' ')}</h3>
                    <p>{action.reason}</p>
                    <small className="muted">
                      {dateLabel(action.created_at)} · {action.actor_id}
                    </small>
                  </div>
                </div>
              ))}
              {!actions.data?.length && (
                <Notice>No staff access changes are recorded for this customer.</Notice>
              )}
            </section>
          </>
        )
      )}
    </>
  );
}
