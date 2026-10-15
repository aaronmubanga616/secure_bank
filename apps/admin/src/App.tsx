import { useState } from 'react';
import { Link, NavLink, Navigate, Outlet, Route, Routes } from 'react-router-dom';
import {
  LayoutDashboard,
  Users,
  ArrowRightLeft,
  ShieldAlert,
  FileClock,
  Activity,
  SlidersHorizontal,
  ShieldCheck,
  LogOut,
  Menu,
  X,
  ChevronRight,
} from 'lucide-react';
import {
  AuthPage,
  SecurityPage,
  Brand,
  Heading,
  Loading,
  Notice,
  Empty,
  Badge,
  useSession,
  signOut,
  dateLabel,
} from '@securebank/ui';
import { formatMoney, type Page, type Transaction } from '@securebank/contracts';
import { useData, errorText } from './data';
import { Customers, CustomerDetail } from './Customers';
import { Transactions, TransactionDetail, Fraud, Audit, Settings } from './Operations';
export interface System {
  status: string;
  customers: number;
  activeCustomers: number;
  successful: number;
  failed: number;
  flagged: number;
  transactionVolume: string;
  statsAsOf: string;
  accounts: number;
  transactions: number;
  openAlerts: number;
  pendingEvents: number;
  deadEmails: number;
  usedHeapBytes: number;
  timestamp: string;
}
const links = [
  ['/', 'Overview', LayoutDashboard],
  ['/users', 'Customers', Users],
  ['/transactions', 'Transactions', ArrowRightLeft],
  ['/fraud', 'Fraud reviews', ShieldAlert],
  ['/audit', 'Audit trail', FileClock],
  ['/system', 'System health', Activity],
  ['/settings', 'Configuration', SlidersHorizontal],
  ['/security', 'My security', ShieldCheck],
] as const;
export function QueryError({ error }: { error: unknown }) {
  return error ? <Notice error>{errorText(error)}</Notice> : null;
}
export function App() {
  return (
    <Routes>
      {['login', 'forgot-password', 'reset-password', 'verify-email'].map((path) => (
        <Route path={path} key={path} element={<AuthPage staff />} />
      ))}
      <Route element={<Layout />}>
        <Route index element={<Dashboard />} />
        <Route path="users" element={<Customers />} />
        <Route path="users/:id" element={<CustomerDetail />} />
        <Route path="transactions" element={<Transactions />} />
        <Route path="transactions/:id" element={<TransactionDetail />} />
        <Route path="fraud" element={<Fraud />} />
        <Route path="audit" element={<Audit />} />
        <Route path="system" element={<SystemHealth />} />
        <Route path="settings" element={<Settings />} />
        <Route path="security" element={<SecurityPage />} />
        <Route
          path="*"
          element={
            <Empty title="Page not found">
              <Link to="/">Return to operations</Link>
            </Empty>
          }
        />
      </Route>
    </Routes>
  );
}
function Layout() {
  const session = useSession();
  const [open, setOpen] = useState(false);
  if (!session) return <Navigate to="/login" replace />;
  const allowed = links.filter(
    ([path]) =>
      (path !== '/fraud' || session.user.role !== 'SUPPORT_AGENT') &&
      (path !== '/settings' || session.user.role !== 'SUPPORT_AGENT'),
  );
  return (
    <div className="shell staff">
      <aside className={`sidebar ${open ? 'open' : ''}`}>
        <button
          className="icon-button mobile-menu"
          aria-label="Close menu"
          onClick={() => setOpen(false)}
        >
          <X size={18} />
        </button>
        <Brand staff />
        <p className="nav-caption">OPERATIONS WORKSPACE</p>
        <nav>
          {allowed.map(([path, label, Icon]) => (
            <NavLink
              key={path}
              to={path}
              end={path === '/'}
              className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
              onClick={() => setOpen(false)}
            >
              <Icon />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="side-bottom">
          <div className="support-card">
            <ShieldCheck size={23} />
            <h3>A considered response.</h3>
            <p>Every administrative action leaves a record. Use your access with care.</p>
          </div>
          <button
            className="nav-link"
            onClick={() => {
              void signOut();
            }}
          >
            <LogOut />
            Sign out
          </button>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <button
            className="icon-button mobile-menu"
            aria-label="Open menu"
            onClick={() => setOpen(true)}
          >
            <Menu size={18} />
          </button>
          <span className="breadcrumb">
            SecureBank <ChevronRight size={10} style={{ display: 'inline', margin: '0 10px' }} />
            <strong>Operations</strong>
          </span>
          <div className="user-chip">
            <span className="avatar">{session.user.fullName[0]}</span>
            <span className="user-name" style={{ fontSize: 11 }}>
              {session.user.fullName}
              <small>{session.user.role.replaceAll('_', ' ')}</small>
            </span>
          </div>
        </header>
        <main className="content">
          {!session.user.mfaEnabled && (
            <Notice>Enroll an authenticator in My security to unlock staff operations.</Notice>
          )}
          <Outlet />
          <footer className="footer">
            <span>SecureBank operations · Auditable by design</span>
            <span>Educational simulator · No real money</span>
          </footer>
        </main>
      </div>
    </div>
  );
}
function Dashboard() {
  const session = useSession();
  const system = useData<System>('/admin/system');
  const transactions = useData<Page<Transaction>>('/admin/transactions', { size: 6 });
  return (
    <>
      <Heading eyebrow="A COMPLETE VIEW" title="A considered response.">
        <Link className="button secondary" to="/system">
          <Activity size={15} /> System health
        </Link>
      </Heading>
      <QueryError error={system.error ?? transactions.error} />
      {system.isPending ? (
        <Loading />
      ) : (
        <>
          <div className="stat-grid">
            {[
              ['Customers', system.data?.customers],
              ['Active customers', system.data?.activeCustomers],
              ['Accounts', system.data?.accounts],
              [
                'Processed volume',
                system.data?.transactionVolume ? formatMoney(system.data.transactionVolume) : '—',
              ],
              ['Successful', system.data?.successful],
              ['Failed', system.data?.failed],
              ['Held transactions', system.data?.flagged],
              ['Open investigations', system.data?.openAlerts],
            ].map(([label, value]) => (
              <section className="panel" key={label}>
                <p className="eyebrow">{label}</p>
                <p className="stat-number">{value ?? '—'}</p>
                <small className="muted">Current simulator total</small>
              </section>
            ))}
          </div>
          <div className="dashboard-grid">
            <section className="panel">
              <div className="panel-head">
                <h2>Latest activity</h2>
                <Link to="/transactions">Explore transactions ↗</Link>
              </div>
              <TransactionTable rows={transactions.data?.content ?? []} />
            </section>
            <div className="side-stack">
              <section className="safety">
                <ShieldAlert size={27} />
                <h3>Attention, where it matters.</h3>
                <p>
                  {system.data?.openAlerts ?? 0} open investigations. Held transfers do not move
                  funds until reviewed.
                </p>
                {session?.user.role !== 'SUPPORT_AGENT' && (
                  <Link className="button soft" to="/fraud">
                    Review investigations
                  </Link>
                )}
              </section>
              <section className="panel">
                <h2>Delivery overview</h2>
                <dl className="receipt">
                  <div>
                    <dt>Pending events</dt>
                    <dd>{system.data?.pendingEvents}</dd>
                  </div>
                  <div>
                    <dt>Parked emails</dt>
                    <dd>{system.data?.deadEmails}</dd>
                  </div>
                  <div>
                    <dt>Overall health</dt>
                    <dd>
                      <Badge value={system.data?.status ?? 'UNKNOWN'} />
                    </dd>
                  </div>
                </dl>
              </section>
            </div>
            <p className="muted" style={{ fontSize: 10 }}>
              Counts sampled {system.data?.statsAsOf ? dateLabel(system.data.statsAsOf) : 'now'} ·
              Display cache up to five seconds.
            </p>
          </div>
        </>
      )}
    </>
  );
}
export function TransactionTable({ rows }: { rows: Transaction[] }) {
  if (!rows.length)
    return (
      <Empty title="No transactions found">
        Adjust your filters or return after simulator activity.
      </Empty>
    );
  return (
    <div className="table-scroll">
      <table>
        <thead>
          <tr>
            <th>Transaction</th>
            <th>Amount</th>
            <th>Risk</th>
            <th>Status</th>
            <th>Submitted</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((tx) => (
            <tr key={tx.id}>
              <td>
                <Link to={'/transactions/' + tx.id}>
                  {tx.description}
                  <small className="muted" style={{ display: 'block', marginTop: 5 }}>
                    {tx.type} · {tx.reference.slice(0, 11)}
                  </small>
                </Link>
              </td>
              <td className="amount">{formatMoney(tx.amount)}</td>
              <td>{tx.riskScore}/100</td>
              <td>
                <Badge value={tx.status} />
              </td>
              <td>{dateLabel(tx.createdAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
export function SystemHealth() {
  const query = useData<System>('/admin/system');
  return (
    <>
      <Heading eyebrow="OPERATIONS / RELIABILITY" title="System health" />
      <QueryError error={query.error} />
      {query.isPending ? (
        <Loading />
      ) : (
        query.data && (
          <div className="two-columns">
            <section className="panel">
              <h2>Service overview</h2>
              <Badge value={query.data.status} />
              <dl className="receipt">
                <div>
                  <dt>Sampled</dt>
                  <dd>{dateLabel(query.data.timestamp)}</dd>
                </div>
                <div>
                  <dt>JVM memory in use</dt>
                  <dd>{(query.data.usedHeapBytes / 1024 / 1024).toFixed(1)} MiB</dd>
                </div>
                <div>
                  <dt>Pending outbox events</dt>
                  <dd>{query.data.pendingEvents}</dd>
                </div>
                <div>
                  <dt>Emails awaiting intervention</dt>
                  <dd>{query.data.deadEmails}</dd>
                </div>
              </dl>
              <button
                className="button secondary"
                style={{ marginTop: 20 }}
                onClick={() => {
                  void query.refetch();
                }}
              >
                Refresh status
              </button>
            </section>
            <section className="safety">
              <ShieldCheck size={30} />
              <h3>Understand before you respond.</h3>
              <p>
                Outbox delays mean projections may be behind the ledger. Financial balances remain
                in PostgreSQL. Inspect dead letters before replaying messages, and use the
                monitoring dashboard for latency, broker, and database signals.
              </p>
              <Link className="text-button" to="/audit">
                Explore audit trail ↗
              </Link>
            </section>
          </div>
        )
      )}
    </>
  );
}
