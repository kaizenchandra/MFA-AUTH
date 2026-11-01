import React, { useEffect, useState, type FormEvent } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Routes, Route, Link } from "react-router-dom";
import { api, ApiError, type Me, type Organization } from "./api";
import "./style.css";
const login = "/oauth2/authorization/keycloak";
function Alert({ message }: { message: string }) {
  return message ? (
    <p className="alert" role="alert">
      {message}
    </p>
  ) : null;
}
function App() {
  const [me, setMe] = useState<Me | null>(null),
    [loading, setLoading] = useState(true),
    [error, setError] = useState("");
  useEffect(() => {
    api<Me>("/me")
      .then(setMe)
      .catch((e) => {
        if (!(e instanceof ApiError) || e.status !== 401) setError(e.message);
      })
      .finally(() => setLoading(false));
  }, []);
  async function logout() {
    try {
      await api("/auth/logout-current", "POST");
      location.assign("/");
    } catch (e) {
      setError((e as Error).message);
    }
  }
  return (
    <>
      <a className="skip" href="#main">
        Skip to content
      </a>
      <header>
        <Link className="brand" to="/">
          n<span>Network</span>
        </Link>
        <nav aria-label="Main navigation">
          {me ? (
            <>
              <Link to="/app">Dashboard</Link>
              <Link to="/security">Security</Link>
              <Link to="/organizations">Organizations</Link>
              <button className="quiet" onClick={logout}>
                Sign out
              </button>
            </>
          ) : (
            <a href={login}>Sign in</a>
          )}
        </nav>
      </header>
      <main id="main">
        <Alert message={error} />
        {loading ? (
          <p role="status">Restoring your secure session…</p>
        ) : (
          <Routes>
            <Route path="/" element={me ? <Dashboard me={me} /> : <Login />} />
            <Route path="/sso/:slug" element={<Login />} />
            <Route
              path="/app"
              element={me ? <Dashboard me={me} /> : <SignIn />}
            />
            <Route path="/security" element={me ? <Security /> : <SignIn />} />
            <Route
              path="/organizations"
              element={me ? <Organizations me={me} /> : <SignIn />}
            />
            <Route
              path="/invite"
              element={me ? <AcceptInvitation /> : <SignIn />}
            />
            <Route
              path="*"
              element={
                <p>
                  Page not found. <Link to="/">Return home</Link>
                </p>
              }
            />
          </Routes>
        )}
      </main>
      <footer>Professional connections. Protected identities.</footer>
    </>
  );
}
function SignIn() {
  if (location.pathname === "/invite") return <InviteLanding />;
  return (
    <section className="card">
      <h1>Sign in to continue</h1>
      <p>Your identity is verified by our secure sign-in service.</p>
      <a
        className="button"
        href={login + "?returnTo=" + encodeURIComponent(location.pathname)}
      >
        Sign in securely
      </a>
    </section>
  );
}
function Login() {
  const [identifier, setIdentifier] = useState(
      decodeURIComponent(
        location.pathname.startsWith("/sso/") ? location.pathname.slice(5) : "",
      ),
    ),
    [error, setError] = useState(
      new URLSearchParams(location.search).has("error")
        ? "Sign-in was not completed. Try again or contact your organization administrator."
        : "",
    ),
    [busy, setBusy] = useState(false);
  async function sso(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const data = await api<{ authorizationUrl: string }>(
        "/auth/sso/discovery",
        "POST",
        { identifier },
      );
      location.assign(data.authorizationUrl);
    } catch (e) {
      setError((e as Error).message);
      setBusy(false);
    }
  }
  return (
    <div className="login-layout">
      <section className="intro">
        <span className="eyebrow">YOUR NEXT CHAPTER STARTS HERE</span>
        <h1>
          Make connections.
          <br />
          Build what’s next.
        </h1>
        <p>
          A professional network built on trust. One secure account for your
          community, your work, and new opportunities.
        </p>
        <div className="trust">
          <span>✓ Verified identities</span>
          <span>✓ Authenticator protection</span>
          <span>✓ Company single sign-on</span>
        </div>
      </section>
      <section className="card">
        <h2>Welcome to Network</h2>
        <p className="muted">Sign in or create your professional profile.</p>
        <Alert message={error} />
        <a className="button full" href={login}>
          Sign in with email or mobile
        </a>
        <div className="social">
          <a className="button secondary" href={login + "?provider=google"}>
            Continue with Google
          </a>
          <a className="button secondary" href={login + "?provider=microsoft"}>
            Continue with Microsoft
          </a>
        </div>
        <p className="small">
          Social providers require administrator configuration for this
          deployment.
        </p>
        <div className="divider">Company access</div>
        <form onSubmit={sso}>
          <label htmlFor="company">
            Corporate email or organization identifier
          </label>
          <input
            id="company"
            value={identifier}
            onChange={(e) => setIdentifier(e.target.value)}
            maxLength={254}
            required
            autoComplete="organization"
            placeholder="you@company.com or acme"
          />
          <button className="secondary full" disabled={busy}>
            {busy ? "Finding your organization…" : "Continue with Company SSO"}
          </button>
        </form>
        <p>
          New here? <a href={login + "?mode=register"}>Create an account</a>
        </p>
        <p className="small">
          Registration, contact verification, password recovery, QR setup, and
          authenticator challenges open on our identity service.
        </p>
      </section>
    </div>
  );
}
function Dashboard({ me }: { me: Me }) {
  return (
    <>
      <span className="eyebrow">YOUR WORKSPACE</span>
      <h1>Welcome, {me.name}</h1>
      <p>
        You’re signed in with verified identity and multifactor authentication.
      </p>
      <div className="grid">
        <section className="card">
          <h2>Account protected</h2>
          <p className="badge">MFA verified</p>
          <p>
            Manage your authenticator, recovery codes, and signed-in devices.
          </p>
          <Link className="button" to="/security">
            Security settings
          </Link>
        </section>
        <section className="card">
          <h2>Your organizations</h2>
          {me.organizations.length ? (
            me.organizations.map((o) => (
              <p key={o.id}>
                {o.name} <span className="badge">{o.role.toLowerCase()}</span>
              </p>
            ))
          ) : (
            <p>You haven’t joined an organization yet.</p>
          )}
          <Link to="/organizations">Manage organizations →</Link>
        </section>
      </div>
    </>
  );
}
type Credential = { id: string; type: string; label: string };
type Session = {
  id: string;
  ipAddress: string;
  start: number;
  lastAccess: number;
  current: boolean;
};
function Security() {
  const [mfa, setMfa] = useState<{
      credentials: Credential[];
      accountUrl: string;
    } | null>(null),
    [sessions, setSessions] = useState<Session[]>([]),
    [events, setEvents] = useState<Record<string, string>[]>([]),
    [error, setError] = useState(""),
    [notice, setNotice] = useState("");
  async function load() {
    try {
      const [m, s, e] = await Promise.all([
        api<{ credentials: Credential[]; accountUrl: string }>("/security/mfa"),
        api<Session[]>("/security/sessions"),
        api<Record<string, string>[]>("/security/events"),
      ]);
      setMfa(m);
      setSessions(s);
      setEvents(e);
    } catch (e) {
      setError((e as Error).message);
    }
  }
  useEffect(() => {
    void load();
  }, []);
  async function mutate(path: string, method: string) {
    try {
      await api(path, method);
      setNotice("Security settings updated.");
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  }
  async function all() {
    try {
      await api("/auth/logout-all", "POST");
      location.assign("/");
    } catch (e) {
      setError((e as Error).message);
    }
  }
  return (
    <>
      <span className="eyebrow">ACCOUNT SETTINGS</span>
      <h1>Security & sign-in</h1>
      <p>Protect your account and control where you’re signed in.</p>
      <Alert message={error} />
      <p role="status">{notice}</p>
      <div className="grid">
        <section className="card">
          <h2>Authenticator app</h2>
          <p>
            Use Microsoft Authenticator, Google Authenticator, or another
            compatible TOTP app.
          </p>
          {mfa?.credentials
            .filter((c) => c.type === "otp")
            .map((c) => (
              <div className="row" key={c.id}>
                <span>{c.label}</span>
                <button
                  className="quiet"
                  onClick={() => mutate("/security/mfa/" + c.id, "DELETE")}
                >
                  Remove
                </button>
              </div>
            ))}
          <a className="button" href={login + "?action=CONFIGURE_TOTP"}>
            Enable / replace authenticator
          </a>
          <details>
            <summary>How QR enrollment works</summary>
            <h3>Microsoft Authenticator</h3>
            <ol>
              <li>Open Microsoft Authenticator.</li>
              <li>Select Add Account (+), then Other Account.</li>
              <li>Scan the displayed QR code.</li>
              <li>Enter its six-digit verification code.</li>
            </ol>
            <h3>Google Authenticator</h3>
            <ol>
              <li>Open Google Authenticator.</li>
              <li>Select Add a Code (+), then Scan a QR Code.</li>
              <li>Scan the displayed QR code.</li>
              <li>Enter its six-digit verification code.</li>
            </ol>
            <p>
              A manual setup key is available. Enable automatic date and time if
              codes are rejected.
            </p>
          </details>
          <p className="small">
            Enroll a replacement before removing your last device. Mandatory MFA
            protects all accounts in this deployment.
          </p>
        </section>
        <section className="card">
          <h2>Recovery & credentials</h2>
          <p>
            Keep recovery codes safe and offline. Each code works once, in the
            order shown.
          </p>
          <a
            className="button secondary"
            href={login + "?action=CONFIGURE_RECOVERY_AUTHN_CODES"}
          >
            Regenerate recovery codes
          </a>
          <p className="small">
            A new set invalidates previous unused codes. Reauthentication is
            required.
          </p>
          <a href={login + "?action=UPDATE_PASSWORD"}>Change password</a>
          <br />
          <a href={login + "?action=UPDATE_PROFILE"}>Update email or mobile</a>
          <br />
          <a href={login + "?action=VERIFY_CONTACT"}>Verify a contact method</a>
          {mfa && (
            <p>
              <a href={mfa.accountUrl}>Identity account console →</a>
            </p>
          )}
          <p className="small">
            Lost your phone? Select “Try another way” during the authenticator
            challenge and use your next recovery code.
          </p>
        </section>
      </div>
      <section className="card">
        <h2>Active sessions</h2>
        <p>Revocation takes effect on the next protected API request.</p>
        {sessions.map((s) => (
          <div className="row" key={s.id}>
            <div>
              <strong>
                {s.current ? "This session" : "Signed-in session"}
              </strong>
              <p className="small">
                {s.ipAddress} · Last used{" "}
                {new Date(s.lastAccess).toLocaleString()}
              </p>
            </div>
            <button
              className="secondary"
              onClick={() => mutate("/security/sessions/" + s.id, "DELETE")}
            >
              Revoke
            </button>
          </div>
        ))}
        <button className="danger" onClick={all}>
          Sign out of all sessions
        </button>
        <p>
          <a href={login + "?action=UPDATE_PROFILE"}>
            Reauthenticate for sensitive changes
          </a>
        </p>
      </section>
      <section className="card">
        <h2>Recent application security events</h2>
        {events.length ? (
          events.map((e, i) => (
            <p key={i}>
              {e.event_type.replaceAll("_", " ").toLowerCase()} ·{" "}
              {new Date(e.created_at).toLocaleString()}
            </p>
          ))
        ) : (
          <p>
            No application security changes yet. Identity and MFA events are
            retained by the identity service.
          </p>
        )}
      </section>
    </>
  );
}
function Organizations({ me }: { me: Me }) {
  const [selected, setSelected] = useState<Organization | null>(null),
    [error, setError] = useState("");
  async function create(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    try {
      await api("/organizations", "POST", {
        name: form.get("name"),
        slug: form.get("slug"),
      });
      location.reload();
    } catch (e) {
      setError((e as Error).message);
    }
  }
  return (
    <>
      <h1>Organizations</h1>
      <Alert message={error} />
      <div className="grid">
        {me.organizations.map((o) => (
          <section className="card" key={o.id}>
            <h2>{o.name}</h2>
            <p>{o.role}</p>
            {["ADMIN", "OWNER"].includes(o.role) && (
              <button onClick={() => setSelected(o)}>
                Security administration
              </button>
            )}
          </section>
        ))}
      </div>
      {selected && <OrganizationAdmin organization={selected} />}
      <section className="card narrow">
        <h2>Create an organization</h2>
        <form onSubmit={create}>
          <label htmlFor="orgname">Organization name</label>
          <input name="name" id="orgname" maxLength={160} required />
          <label htmlFor="slug">Unique identifier</label>
          <input
            name="slug"
            id="slug"
            pattern="[a-z0-9][a-z0-9-]{2,62}"
            required
            placeholder="company-name"
          />
          <button>Create organization</button>
        </form>
      </section>
    </>
  );
}
type Policy = {
  sso_only: boolean;
  mfa_mode: string;
  jit_enabled: boolean;
  version: number;
};
function OrganizationAdmin({
  organization: o,
}: {
  organization: Organization;
}) {
  const [policy, setPolicy] = useState<Policy | null>(null),
    [sso, setSso] = useState<Record<string, unknown>>({}),
    [members, setMembers] = useState<Record<string, unknown>[]>([]),
    [message, setMessage] = useState(""),
    [error, setError] = useState(""),
    [invitation, setInvitation] = useState("");
  async function load() {
    try {
      const [p, s, m] = await Promise.all([
        api<Policy>("/organizations/" + o.id + "/security-policy"),
        api<Record<string, unknown>>("/organizations/" + o.id + "/sso"),
        api<Record<string, unknown>[]>("/organizations/" + o.id + "/members"),
      ]);
      setPolicy(p);
      setSso(s);
      setMembers(m);
    } catch (e) {
      setError((e as Error).message);
    }
  }
  useEffect(() => {
    void load();
  }, [o.id]);
  async function save(e: FormEvent) {
    e.preventDefault();
    if (!policy) return;
    try {
      await api("/organizations/" + o.id + "/security-policy", "PUT", {
        ssoOnly: policy.sso_only,
        mfaMode: policy.mfa_mode,
        jitEnabled: policy.jit_enabled,
        version: policy.version,
      });
      setMessage("Policy saved.");
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  }
  async function invite(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    try {
      const v = await api<{ acceptPath: string }>(
        "/organizations/" + o.id + "/invitations",
        "POST",
        { email: form.get("email"), role: form.get("role") },
      );
      setInvitation(location.origin + v.acceptPath);
    } catch (e) {
      setError((e as Error).message);
    }
  }
  async function revoke(m: Record<string, unknown>) {
    try {
      await api("/organizations/" + o.id + "/members/" + m.user_id, "PUT", {
        role: m.role,
        status: "REVOKED",
        version: m.version,
      });
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  }
  async function toggle() {
    try {
      await api("/organizations/" + o.id + "/sso", "PUT", {
        enabled: !sso.enabled,
        version: sso.version,
      });
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  }
  return (
    <section className="card">
      <h2>{o.name}: security administration</h2>
      <Alert message={error} />
      <p role="status">{message}</p>
      <p>
        SSO provider: {String(sso.alias || "Not configured")}.{" "}
        {sso.approved
          ? "Approved"
          : "An identity operator must provision and approve federation metadata."}
      </p>
      {!!sso.approved && (
        <button className="secondary" onClick={toggle}>
          {sso.enabled ? "Disable" : "Enable"} SSO access
        </button>
      )}
      {policy && (
        <form onSubmit={save}>
          <label className="check">
            <input
              type="checkbox"
              checked={policy.sso_only}
              onChange={(e) =>
                setPolicy({ ...policy, sso_only: e.target.checked })
              }
            />
            Require company SSO
          </label>
          <label htmlFor="mfa-policy">MFA policy</label>
          <select
            id="mfa-policy"
            value={policy.mfa_mode}
            onChange={(e) => setPolicy({ ...policy, mfa_mode: e.target.value })}
          >
            <option value="MANDATORY">Mandatory application MFA</option>
            <option value="OPTIONAL">
              Optional (realm baseline still applies)
            </option>
            <option value="TRUSTED_EXTERNAL">
              Trusted external assurance, or application MFA
            </option>
          </select>
          <label className="check">
            <input
              type="checkbox"
              checked={policy.jit_enabled}
              onChange={(e) =>
                setPolicy({ ...policy, jit_enabled: e.target.checked })
              }
            />
            Allow new members from the approved organization provider
          </label>
          <button>Save security policy</button>
        </form>
      )}
      <h3>Invite a colleague</h3>
      <form onSubmit={invite}>
        <label htmlFor="invite-email">Email</label>
        <input name="email" id="invite-email" type="email" required />
        <label htmlFor="invite-role">Role</label>
        <select name="role" id="invite-role">
          <option value="MEMBER">Member</option>
          <option value="ADMIN">Administrator</option>
        </select>
        <button>Create invitation link</button>
      </form>
      {invitation && (
        <p role="status">
          Share securely with the intended recipient:{" "}
          <a href={invitation}>{invitation}</a>
        </p>
      )}
      <h3>Members</h3>
      {members.map((m) => (
        <div className="row" key={String(m.user_id)}>
          <span>
            {String(m.user_id)} · {String(m.role)} · {String(m.status)}
          </span>
          {m.role !== "OWNER" && m.status === "ACTIVE" && (
            <button className="quiet" onClick={() => revoke(m)}>
              Revoke membership
            </button>
          )}
        </div>
      ))}
    </section>
  );
}
function InviteLanding() {
  const [error, setError] = useState("");
  async function prepare(register: boolean) {
    try {
      if (location.hash)
        await api("/auth/invitations/prepare", "POST", {
          token: location.hash.slice(1),
        });
      history.replaceState(null, "", "/invite");
      location.assign(
        login + "?returnTo=/invite" + (register ? "&mode=register" : ""),
      );
    } catch (e) {
      setError((e as Error).message);
    }
  }
  return (
    <section className="card">
      <h1>You’re invited</h1>
      <p>
        Sign in or register with the email address that received this
        invitation.
      </p>
      <Alert message={error} />
      <button onClick={() => prepare(false)}>Sign in to accept</button>{" "}
      <button className="secondary" onClick={() => prepare(true)}>
        Register to accept
      </button>
    </section>
  );
}
function AcceptInvitation() {
  const [error, setError] = useState("");
  async function accept() {
    try {
      await api(
        "/invitations/accept",
        "POST",
        location.hash ? { token: location.hash.slice(1) } : {},
      );
      location.replace("/organizations");
    } catch (e) {
      setError((e as Error).message);
    }
  }
  return (
    <section className="card">
      <h1>Join your organization</h1>
      <p>Your verified email must match the invitation recipient.</p>
      <Alert message={error} />
      <button onClick={accept}>Accept invitation</button>
    </section>
  );
}
createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>,
);
