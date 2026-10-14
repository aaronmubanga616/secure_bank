import {
  cloneElement,
  isValidElement,
  useId,
  useState,
  useSyncExternalStore,
  type FormEvent,
  type ReactNode,
  type ReactElement,
} from 'react';
import { QueryClient } from '@tanstack/react-query';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ArrowUpRight, ShieldCheck, LoaderCircle } from 'lucide-react';
import {
  createApiClient,
  errorMessage,
  type Session,
  type LoginResult,
} from '@securebank/contracts';

let current: Session | null = null;
const subscribers = new Set<() => void>();
export const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 15_000, refetchOnWindowFocus: true } },
});
export const sessionStore = {
  read: () => current,
  write: (session: Session) => {
    if (current?.user.id !== session.user.id) queryClient.clear();
    current = session;
    subscribers.forEach((listener) => listener());
  },
  clear: () => {
    current = null;
    queryClient.clear();
    subscribers.forEach((listener) => listener());
  },
};
export function useSession() {
  return useSyncExternalStore((listener) => {
    subscribers.add(listener);
    return () => {
      subscribers.delete(listener);
    };
  }, sessionStore.read);
}
export const api = createApiClient('/api/v1', sessionStore);
let device = localStorage.getItem('securebank-device');
if (!device) {
  device = crypto.randomUUID();
  localStorage.setItem('securebank-device', device);
}
api.defaults.headers.common['X-Device-ID'] = device;
export async function signOut() {
  const session = sessionStore.read();
  sessionStore.clear();
  if (session)
    await api.post('/auth/logout', { refreshToken: session.refreshToken }).catch(() => {});
}
export function Brand({ staff = false }: { staff?: boolean }) {
  return (
    <Link to="/" className="brand">
      <span className="brand-mark">
        <ShieldCheck size={24} />
      </span>
      <span>
        securebank
        <span className="brand-sub">{staff ? 'OPERATIONS' : 'A LITTLE MORE CERTAINTY'}</span>
      </span>
    </Link>
  );
}
export function Heading({
  eyebrow,
  title,
  children,
}: {
  eyebrow: string;
  title: string;
  children?: ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
      </div>
      {children}
    </div>
  );
}
export function Notice({ children, error = false }: { children: ReactNode; error?: boolean }) {
  return (
    <p role={error ? 'alert' : 'status'} className={`notice ${error ? 'error' : ''}`}>
      {children}
    </p>
  );
}
export function Loading() {
  return (
    <div className="loading" role="status">
      <LoaderCircle className="spin" /> Loading your information…
    </div>
  );
}
export function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="empty">
      <ShieldCheck size={30} />
      <h3>{title}</h3>
      <p>{children}</p>
    </div>
  );
}
export function Badge({ value }: { value: string }) {
  return (
    <span
      className={`badge ${['FAILED', 'FROZEN', 'SUSPENDED', 'CONFIRMED_FRAUD', 'DEAD'].includes(value) ? 'danger' : ['FLAGGED', 'OPEN', 'PAUSED', 'PENDING'].includes(value) ? 'warning' : ''}`}
    >
      {value.replaceAll('_', ' ')}
    </span>
  );
}
export function Field({ label, children }: { label: string; children: ReactNode }) {
  const id = useId();
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {isValidElement(children)
        ? cloneElement(children as ReactElement<{ id?: string }>, { id })
        : children}
    </div>
  );
}
export function TaskForm({
  action,
  children,
  submit = 'Save changes',
  className = '',
  reset = false,
}: {
  action: (data: FormData) => Promise<string | void>;
  children: ReactNode;
  submit?: string;
  className?: string;
  reset?: boolean;
}) {
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState('');
  const [failed, setFailed] = useState(false);
  async function handle(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    setPending(true);
    setMessage('');
    try {
      const result = await action(new FormData(form));
      setFailed(false);
      setMessage(result ?? 'Changes saved.');
      if (reset) form.reset();
      await queryClient.invalidateQueries();
    } catch (error) {
      setFailed(true);
      setMessage(
        error instanceof Error && !('isAxiosError' in error) ? error.message : errorMessage(error),
      );
    } finally {
      setPending(false);
    }
  }
  return (
    <form
      className={`form ${className}`}
      onSubmit={(event) => {
        void handle(event);
      }}
    >
      <fieldset disabled={pending}>
        {children}
        <button className="button" type="submit">
          {pending ? (
            <>
              <LoaderCircle className="spin" size={16} /> Working…
            </>
          ) : (
            submit
          )}
        </button>
      </fieldset>
      {message && <Notice error={failed}>{message}</Notice>}
    </form>
  );
}
export const values = (data: FormData) =>
  Object.fromEntries(data.entries()) as Record<string, string>;
export const dateLabel = (value: string) =>
  new Intl.DateTimeFormat('en-ZM', { dateStyle: 'medium', timeStyle: 'short' }).format(
    new Date(value),
  );

export function AuthPage({ staff = false }: { staff?: boolean }) {
  const location = useLocation();
  const navigate = useNavigate();
  const [challenge, setChallenge] = useState('');
  const [token] = useState(() => new URLSearchParams(location.hash.slice(1)).get('token') ?? '');
  const mode = location.pathname.slice(1) || 'login';
  const titles: Record<string, string> = {
    login: staff ? 'Welcome back, team.' : 'Welcome to your everyday.',
    register: 'Make room for possibility.',
    'forgot-password': 'Let’s get you back in.',
    'reset-password': 'A fresh start.',
    'verify-email': 'One more step.',
  };
  async function accept(result: LoginResult) {
    if (result.mfaRequired) {
      setChallenge(result.challenge);
      return;
    }
    if (staff ? result.user.role === 'CUSTOMER' : result.user.role !== 'CUSTOMER') {
      await api.post('/auth/logout', { refreshToken: result.refreshToken });
      throw new Error('Use the correct portal for your account.');
    }
    sessionStore.write(result);
    navigate(staff && !result.user.mfaEnabled ? '/security' : '/', { replace: true });
  }
  return (
    <div className="auth-layout">
      <aside className="auth-story">
        <Brand staff={staff} />
        <div>
          <span className="pill">BUILT AROUND YOUR EVERYDAY</span>
          <h1>
            A clearer view.
            <br />A confident
            <br />
            <em>next step.</em>
          </h1>
          <p>A thoughtful space to move, manage, and understand your money.</p>
          <div className="story-art" aria-hidden="true">
            <div className="orb" />
            <span className="art-label">
              A little more
              <br />
              peace of mind. <ArrowUpRight />
            </span>
          </div>
        </div>
        <small>Educational banking simulator · Simulated ZMW</small>
      </aside>
      <main className="auth-main">
        <div className="auth-card">
          <p className="eyebrow">
            {staff ? 'SECUREBANK / STAFF ACCESS' : 'SECUREBANK / PERSONAL BANKING'}
          </p>
          <h2>{challenge ? 'Your security code.' : (titles[mode] ?? titles.login)}</h2>
          <p className="muted">
            {challenge
              ? 'Enter a code from your authenticator or a recovery code.'
              : 'Your next chapter starts with a secure sign-in.'}
          </p>
          <TaskForm
            key={mode + challenge}
            submit={
              challenge
                ? 'Verify & sign in'
                : ({
                    register: 'Create account',
                    'forgot-password': 'Send reset link',
                    'reset-password': 'Set new password',
                    'verify-email': 'Verify email',
                  }[mode] ?? 'Sign in')
            }
            action={async (form) => {
              const data = values(form);
              if (challenge) {
                await accept(
                  (await api.post<LoginResult>('/auth/mfa/verify', { challenge, code: data.code }))
                    .data,
                );
                return;
              }
              if (mode === 'login') {
                await accept((await api.post<LoginResult>('/auth/login', data)).data);
                return;
              }
              if (mode === 'register') {
                await api.post('/auth/register', data);
                return 'Check your email to verify your account. Local emails appear in Mailpit on port 8025.';
              }
              if (mode === 'forgot-password') {
                await api.post('/auth/forgot-password', data);
                return 'If your account is eligible, a reset link is on its way.';
              }
              if (mode === 'reset-password') {
                await api.post('/auth/reset-password', {
                  token: data.token || token,
                  password: data.password,
                });
                history.replaceState(null, '', location.pathname);
                return 'Password changed. You can now sign in.';
              }
              await api.post('/auth/verify-email', { token: data.token || token });
              history.replaceState(null, '', location.pathname);
              return 'Email verified. You can now sign in.';
            }}
          >
            {challenge ? (
              <Field label="Authenticator or recovery code">
                <input name="code" autoComplete="one-time-code" required autoFocus />
              </Field>
            ) : (
              <>
                {mode === 'register' && (
                  <Field label="Full name">
                    <input
                      name="fullName"
                      minLength={2}
                      maxLength={100}
                      autoComplete="name"
                      required
                    />
                  </Field>
                )}
                {['login', 'register', 'forgot-password'].includes(mode) && (
                  <Field label="Email address">
                    <input
                      name="email"
                      type="email"
                      autoComplete="email"
                      maxLength={254}
                      required
                    />
                  </Field>
                )}
                {['login', 'register', 'reset-password'].includes(mode) && (
                  <Field label={mode === 'login' ? 'Password' : 'Choose a password'}>
                    <input
                      name="password"
                      type="password"
                      autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                      minLength={mode === 'login' ? 1 : 12}
                      maxLength={72}
                      required
                    />
                  </Field>
                )}
                {['verify-email', 'reset-password'].includes(mode) && !token && (
                  <Field label="Token from your email link">
                    <input name="token" required autoComplete="off" />
                  </Field>
                )}
                {mode === 'register' && (
                  <small className="muted">
                    Use at least 12 characters. This simulator never holds real money.
                  </small>
                )}
              </>
            )}
          </TaskForm>
          <div className="auth-links">
            {mode === 'login' ? (
              <>
                <Link to="/forgot-password">Forgot password?</Link>
                {!staff && (
                  <Link to="/register">
                    Create an account <ArrowUpRight size={14} />
                  </Link>
                )}
              </>
            ) : (
              <Link to="/login">Back to sign in</Link>
            )}
          </div>
          <p className="auth-foot">
            <ShieldCheck size={16} /> Protected sessions. Authenticator support. Your control.
          </p>
        </div>
      </main>
    </div>
  );
}

export function SecurityPage() {
  const session = useSession();
  const [setup, setSetup] = useState<{ secret: string; otpauthUrl: string } | null>(null);
  const [recovery, setRecovery] = useState<string[]>([]);
  if (recovery.length)
    return (
      <section className="panel">
        <h2>Save your recovery codes</h2>
        <Notice>These are shown once. Store them somewhere private, then sign in again.</Notice>
        <pre className="recovery">{recovery.join('\n')}</pre>
        <button
          className="button"
          onClick={() => {
            void signOut();
          }}
        >
          Saved — sign in again
        </button>
      </section>
    );
  return (
    <>
      <Heading eyebrow="YOUR ACCOUNT" title="Security & peace of mind" />
      <div className="two-columns">
        <section className="panel">
          <h2>Authenticator protection</h2>
          <p className="muted">
            {session?.user.mfaEnabled
              ? 'Your account uses an authenticator at sign-in.'
              : 'Add a second step to protect your account. Required for staff operations.'}
          </p>
          {!session?.user.mfaEnabled ? (
            !setup ? (
              <TaskForm
                submit="Set up authenticator"
                action={async (form) => {
                  setSetup((await api.post('/auth/mfa/setup', values(form))).data);
                }}
              >
                <Field label="Confirm password">
                  <input name="password" type="password" autoComplete="current-password" required />
                </Field>
              </TaskForm>
            ) : (
              <>
                <p>Add this setup key to your authenticator using time-based codes:</p>
                <code className="secret">{setup.secret}</code>
                <TaskForm
                  submit="Enable & get recovery codes"
                  action={async (form) => {
                    const { data } = await api.post<{ recoveryCodes: string[] }>(
                      '/auth/mfa/confirm',
                      values(form),
                    );
                    setRecovery(data.recoveryCodes);
                  }}
                >
                  <Field label="Six-digit code">
                    <input
                      name="code"
                      inputMode="numeric"
                      pattern="[0-9]{6}"
                      autoComplete="one-time-code"
                      required
                    />
                  </Field>
                </TaskForm>
              </>
            )
          ) : (
            <TaskForm
              submit="Disable authenticator"
              action={async (form) => {
                await api.post('/auth/mfa/disable', values(form));
                await signOut();
              }}
            >
              <Field label="Password">
                <input name="password" type="password" autoComplete="current-password" required />
              </Field>
              <Field label="Authenticator or recovery code">
                <input name="code" autoComplete="one-time-code" required />
              </Field>
            </TaskForm>
          )}
        </section>
        <section className="panel">
          <h2>Change your password</h2>
          <p className="muted">Updating your password signs you out on all devices.</p>
          <TaskForm
            submit="Update password"
            action={async (form) => {
              await api.post('/auth/change-password', values(form));
              await signOut();
            }}
          >
            <Field label="Current password">
              <input
                name="currentPassword"
                type="password"
                autoComplete="current-password"
                required
              />
            </Field>
            <Field label="New password">
              <input
                name="newPassword"
                type="password"
                minLength={12}
                maxLength={72}
                autoComplete="new-password"
                required
              />
            </Field>
          </TaskForm>
        </section>
      </div>
    </>
  );
}
