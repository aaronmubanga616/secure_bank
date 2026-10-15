import { useState } from 'react';
import { Link, NavLink, Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import {
  ArrowUpRight,
  ArrowDownLeft,
  ArrowRightLeft,
  Bell,
  CalendarClock,
  ChevronRight,
  CircleHelp,
  Download,
  FileText,
  LayoutDashboard,
  LogOut,
  Menu,
  Plus,
  ShieldCheck,
  Users,
  Wallet,
  X,
  Settings,
  Landmark,
} from 'lucide-react';
import {
  useSession,
  signOut,
  Brand,
  AuthPage,
  SecurityPage,
  Heading,
  Notice,
  Loading,
  Empty,
  Badge,
} from '@securebank/ui';
import {
  formatMoney,
  sumMoney,
  errorMessage,
  type Account,
  type Transaction,
  type Page,
  type Schedule,
} from '@securebank/contracts';
import { useData } from './data';
import { History, Receipt, MoveMoney } from './Payments';
import {
  Accounts,
  AccountDetail,
  Recipients,
  Schedules,
  Statements,
  Notifications,
  Profile,
} from './Manage';

export function QueryError({ error }: { error: unknown }) {
  return error ? <Notice error>{errorMessage(error)}</Notice> : null;
}
const links = [
  ['/', 'Overview', LayoutDashboard],
  ['/accounts', 'My accounts', Wallet],
  ['/transactions', 'Transactions', ArrowRightLeft],
  ['/move', 'Move money', ArrowUpRight],
  ['/recipients', 'Recipients', Users],
  ['/scheduled', 'Scheduled transfers', CalendarClock],
  ['/statements', 'Statements', FileText],
  ['/notifications', 'Notifications', Bell],
  ['/security', 'Security', ShieldCheck],
  ['/profile', 'Profile', Settings],
] as const;
function Layout() {
  const session = useSession();
  const location = useLocation();
  const [open, setOpen] = useState(false);
  if (!session) return <Navigate to="/login" replace />;
  return (
    <div className="shell">
      <aside className={`sidebar ${open ? 'open' : ''}`}>
        <button
          className="icon-button mobile-menu"
          aria-label="Close menu"
          onClick={() => setOpen(false)}
        >
          <X size={18} />
        </button>
        <Brand />
        <p className="nav-caption">YOUR EVERYDAY</p>
        <nav>
          {links.map(([path, label, Icon]) => (
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
            <CircleHelp size={19} />
            <h3>Here for your next step.</h3>
            <p>Explore safely with simulated funds. Every amount is in Zambian kwacha.</p>
            <Link className="text-button" to="/security">
              Keep your account safe <ArrowUpRight size={12} />
            </Link>
          </div>
          <button
            className="nav-link"
            onClick={() => {
              void signOut();
            }}
          >
            <LogOut /> Sign out
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
            Personal banking{' '}
            <ChevronRight size={10} style={{ display: 'inline', margin: '0 10px' }} />
            <strong>
              {links.find(([path]) => path === location.pathname)?.[1] ?? 'Transaction details'}
            </strong>
          </span>
          <div className="top-actions">
            <Link className="icon-button" to="/notifications" aria-label="Notifications">
              <Bell size={15} />
            </Link>
            <Link to="/profile" className="user-chip">
              <span className="avatar">
                {session.user.fullName
                  .split(' ')
                  .map((word) => word[0])
                  .slice(0, 2)
                  .join('')}
              </span>
              <span className="user-name">
                {session.user.fullName}
                <small>Personal account</small>
              </span>
            </Link>
          </div>
        </header>
        <main className="content">
          <Outlet />
          <footer className="footer">
            <span>© {new Date().getFullYear()} SecureBank · A little more certainty.</span>
            <span>Educational simulator · No real money · ZMW</span>
          </footer>
        </main>
      </div>
    </div>
  );
}
export function App() {
  return (
    <Routes>
      {['login', 'register', 'forgot-password', 'reset-password', 'verify-email'].map((path) => (
        <Route key={path} path={path} element={<AuthPage />} />
      ))}
      <Route element={<Layout />}>
        <Route index element={<Dashboard />} />
        <Route path="accounts" element={<Accounts />} />
        <Route path="accounts/:id" element={<AccountDetail />} />
        <Route path="dashboard" element={<Navigate to="/" replace />} />
        <Route path="transfer" element={<Navigate to="/move" replace />} />
        <Route path="beneficiaries" element={<Navigate to="/recipients" replace />} />
        <Route path="scheduled-transfers" element={<Navigate to="/scheduled" replace />} />
        <Route path="transactions" element={<History />} />
        <Route path="transactions/:id" element={<Receipt />} />
        <Route path="move" element={<MoveMoney />} />
        <Route path="recipients" element={<Recipients />} />
        <Route path="scheduled" element={<Schedules />} />
        <Route path="statements" element={<Statements />} />
        <Route path="notifications" element={<Notifications />} />
        <Route path="security" element={<SecurityPage />} />
        <Route path="profile" element={<Profile />} />
        <Route
          path="*"
          element={
            <Empty title="We couldn’t find that page">
              <Link to="/">Return to overview</Link>
            </Empty>
          }
        />
      </Route>
    </Routes>
  );
}
export function AccountCard({ account }: { account: Account }) {
  return (
    <div className={`account-card ${account.type === 'SAVINGS' ? 'savings' : ''}`}>
      <div className="account-top">
        <span>{account.type === 'SAVINGS' ? 'Savings account' : 'Everyday current'}</span>
        <Landmark size={15} />
      </div>
      <p className="amount">{formatMoney(account.balance)}</p>
      <div className="account-top" style={{ marginBottom: 0 }}>
        <span className="account-number">{account.accountNumber}</span>
        <Badge value={account.status} />
      </div>
      <Link className="text-button" to={`/accounts/${account.id}`}>
        Account details ↗
      </Link>
    </div>
  );
}
export function TransactionTable({ rows }: { rows: Transaction[] }) {
  if (!rows.length)
    return (
      <Empty title="Your story starts here">
        Your deposits, withdrawals, and transfers will appear here.
      </Empty>
    );
  return (
    <div className="table-scroll">
      <table>
        <thead>
          <tr>
            <th>Transaction</th>
            <th>Date</th>
            <th>Amount</th>
            <th>Status</th>
            <th>
              <span className="sr-only">Details</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.id}>
              <td>
                <div className="transaction-name">
                  {row.direction === 'IN' ? (
                    <ArrowDownLeft className="tx-icon" />
                  ) : (
                    <ArrowUpRight className="tx-icon" />
                  )}
                  <Link to={`/transactions/${row.id}`}>
                    {row.description}
                    <small>
                      {row.type.replaceAll('_', ' ')} · {row.reference.slice(0, 11)}
                    </small>
                  </Link>
                </div>
              </td>
              <td>
                {new Date(row.createdAt).toLocaleDateString('en-ZM', {
                  day: '2-digit',
                  month: 'short',
                })}
              </td>
              <td className={`amount ${row.direction === 'IN' ? 'credit' : ''}`}>
                {row.direction === 'IN' ? '+' : row.direction === 'OUT' ? '−' : ''}
                {formatMoney(row.amount)}
              </td>
              <td>
                <Badge value={row.status} />
              </td>
              <td>
                <Link to={`/transactions/${row.id}`} aria-label={`View ${row.description}`}>
                  <ChevronRight size={12} />
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
function Dashboard() {
  const session = useSession();
  const accounts = useData<Account[]>('/accounts');
  const recent = useData<Page<Transaction>>('/transactions', { size: 100 });
  const schedules = useData<Schedule[]>('/scheduled-transfers');
  const total = sumMoney((accounts.data ?? []).map((account) => account.balance));
  const days = Array.from({ length: 7 }, (_, index) => {
    const day = new Date();
    day.setDate(day.getDate() - 6 + index);
    const count =
      recent.data?.content.filter(
        (tx) => new Date(tx.createdAt).toDateString() === day.toDateString(),
      ).length ?? 0;
    return { label: day.toLocaleDateString('en', { weekday: 'short' }), count };
  });
  const maximum = Math.max(...days.map((day) => day.count), 1);
  return (
    <>
      <Heading
        eyebrow="YOUR MONEY, AT A GLANCE"
        title={`Hello, ${session?.user.fullName.split(' ')[0]}.`}
      >
        <Link className="button secondary" to="/statements">
          <Download size={13} /> Download statement
        </Link>
      </Heading>
      <p className="intro" style={{ marginTop: -17, marginBottom: 28 }}>
        A fresh perspective on your everyday banking.
      </p>
      <QueryError error={accounts.error ?? recent.error ?? schedules.error} />
      {accounts.isPending ? (
        <Loading />
      ) : (
        <div className="dashboard-grid">
          <div className="main-stack">
            <section className="balance-card">
              <p className="eyebrow">TOTAL AVAILABLE BALANCE</p>
              <h2>{formatMoney(total)}</h2>
              <span className="currency">ZAMBIAN KWACHA · SIMULATED FUNDS</span>
              <div className="balance-bottom">
                <span>{accounts.data?.length ?? 0} connected accounts · A clearer picture</span>
                <Link className="button" to="/accounts">
                  View accounts <ArrowUpRight size={12} />
                </Link>
              </div>
            </section>
            <div className="quick-actions">
              <Link className="quick-action" to="/move?type=TRANSFER">
                <ArrowUpRight />
                Send money
              </Link>
              <Link className="quick-action" to="/move?type=DEPOSIT">
                <Plus />
                Add funds
              </Link>
              <Link className="quick-action" to="/move?type=WITHDRAWAL">
                <ArrowDownLeft />
                Withdraw
              </Link>
              <Link className="quick-action" to="/scheduled">
                <CalendarClock />
                Schedule
              </Link>
            </div>
            <section className="panel">
              <div className="panel-head">
                <h2>Recent transactions</h2>
                <Link to="/transactions">
                  View all <ArrowUpRight size={11} style={{ display: 'inline' }} />
                </Link>
              </div>
              {recent.isPending ? (
                <Loading />
              ) : (
                <TransactionTable rows={recent.data?.content.slice(0, 5) ?? []} />
              )}
            </section>
            <section className="panel">
              <div className="panel-head">
                <h2>Your week in motion</h2>
                <span className="muted" style={{ fontSize: 10 }}>
                  Activity from your latest 100 transactions
                </span>
              </div>
              <div
                className="bar-chart"
                role="img"
                aria-label={days.map((day) => `${day.label}: ${day.count} transactions`).join(', ')}
              >
                {days.map((day, index) => (
                  <div className="bar-column" key={index}>
                    <small>{day.count || ''}</small>
                    <span style={{ height: `${Math.max(3, (day.count / maximum) * 80)}px` }} />
                    <small>{day.label}</small>
                  </div>
                ))}
              </div>
            </section>
          </div>
          <div className="side-stack">
            <section className="panel">
              <div className="panel-head">
                <h2>My accounts</h2>
                <Link to="/accounts" aria-label="Manage accounts">
                  <Plus size={15} />
                </Link>
              </div>
              {accounts.data?.map((account) => (
                <AccountCard key={account.id} account={account} />
              ))}
              {!accounts.data?.length && (
                <Empty title="A place for your plans">
                  <Link className="text-button" to="/accounts">
                    Open your first account
                  </Link>
                </Empty>
              )}
              <Link className="text-button" to="/accounts">
                Explore your accounts <ArrowUpRight size={12} />
              </Link>
            </section>
            <section className="panel">
              <div className="panel-head">
                <h2>Coming up next</h2>
                <Link to="/scheduled">Manage</Link>
              </div>
              {schedules.data
                ?.filter((schedule) => schedule.status === 'ACTIVE')
                .slice(0, 3)
                .map((schedule) => (
                  <div className="mini-card" key={schedule.id}>
                    <CalendarClock size={19} />
                    <div className="grow">
                      <h3>{schedule.beneficiaryName}</h3>
                      <p>{new Date(schedule.nextRunAt).toLocaleDateString()}</p>
                    </div>
                    <strong>{formatMoney(schedule.amount)}</strong>
                  </div>
                ))}
              {!schedules.data?.some((schedule) => schedule.status === 'ACTIVE') && (
                <p className="muted" style={{ fontSize: 11 }}>
                  No upcoming payments. A little planning goes a long way.
                </p>
              )}
            </section>
            <section className="safety">
              <ShieldCheck size={25} />
              <h3>A little more peace of mind.</h3>
              <p>Protect your everyday with an authenticator and a strong, unique password.</p>
              <Link className="text-button" to="/security">
                Review security <ArrowUpRight size={12} />
              </Link>
            </section>
          </div>
        </div>
      )}
    </>
  );
}
