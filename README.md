# Network authentication module

Integrated Spring Boot BFF, React application and Keycloak identity service. The original repository contained only [the requirements](promt.md). No existing application data or code was replaced.

## Run locally

Prerequisites: Docker with Compose, Node 24, and enough memory for the identity service and application. Java 21 and Maven are needed only for host-side Java tests.

```sh
node scripts/init-env.mjs
docker compose up -d --build
docker compose exec -T postgres psql -U platform -d platform -v ON_ERROR_STOP=1 < scripts/seed-company.sql
```

Open **http://localhost:18080**. Keycloak is at **http://localhost:18180**. Local verification email is at **http://localhost:18025**. These ports intentionally avoid other applications found running on the development machine.

Generated development credentials are in ignored, mode-0600 `.env`; they are never printed by the initialization script. Do not copy this file to production. Bootstrap is create-only: restarting the stack does not overwrite a realm, user, credential, or operator's federation configuration. Use explicit Admin API changes for existing environments.

Register using an email address as your sign-in identifier, or an international mobile number beginning with `+`. Email addresses are verified through Mailpit. Mobile users receive a test SMS through an authenticated local adapter. The adapter's `GET /messages?phone=...` requires the generated `SMS_TEST_TOKEN` bearer credential. It exists only in development, is bound to loopback, and is never part of the production chart.

The required actions guide users through terms acceptance, contact verification, QR enrollment and recovery-code acknowledgement. Both Google Authenticator and Microsoft Authenticator use the same TOTP setup. Security settings provide replacement enrollment, recovery regeneration, password/profile changes, session listing and revocation.

## Architecture

```text
Browser -- same-origin HttpOnly cookie --> Nginx / React
                                      --> Spring Boot BFF
                                           | Redis: server-side sessions/tokens
                                           | PostgreSQL: application authorization
                                           | Keycloak: OIDC / credentials / MFA
                                                        | Google / Microsoft
                                                        | Organization OIDC / SAML
                                                        | SMTP / SMS
```

The BFF is the application backend, not a second authorization server. Spring Security performs Authorization Code + S256 PKCE, state/nonce handling and ID-token verification. Tokens never enter browser storage. Native Keycloak login, registration, password-reset and required-action pages are part of the product journey and use the Network theme.

Keycloak exclusively owns credentials, external issuer/subject associations, verified contacts, identity sessions, OTP secrets and hashed recovery codes. The application owns organization memberships, invitations, approved provider mappings, policies and application audit events. It stores a minimal profile keyed by immutable issuer + subject.

Application authorization reloads membership and policy on each organization operation. A verified domain is only a discovery hint. JIT requires the exact approved broker alias; revoked membership rows are never reactivated by JIT. Policies use optimistic versions and invitation acceptance locks its database row. Credential, OTP, and token exchanges are never blindly retried.

Identity sessions are introspected on every authenticated application API request. An IdP outage fails closed. Access tokens expire after five minutes; the application returns 401 and the user re-enters the OIDC flow. This implementation deliberately does not refresh tokens in parallel across BFF replicas. A valid Keycloak session can restore access through OIDC. Realm refresh rotation is enabled for supported consumers, but this BFF does not redeem refresh tokens.

## Source structure

- [backend](backend): Spring Security BFF, organization authorization, API, Flyway migration and Java tests.
- [keycloak-extension](keycloak-extension): phone verification, enrollment binding/expiry, atomic recovery reservation, assurance integration, registration validation and hosted theme.
- [frontend](frontend): responsive React screens, same-origin API client and Playwright tests.
- [scripts](scripts): secret initialization, create-only realm bootstrap, local SMS adapter, federation onboarding and test organization fixture.
- [infra](infra): container builds, Helm deployment and optional Prometheus configuration.
- [docs](docs): API, federation, operational, security and acceptance guides.

## Build and test

```sh
mvn -B -ntp test
cd frontend
npm ci
npm run build
npx playwright install chromium
npm run test:e2e
```

Testcontainers requires Docker. A test-only Docker API compatibility setting supports the Docker 29 engine found on this machine. Playwright uses the real local Keycloak, BFF, PostgreSQL, Redis and SMS adapter. Browser traces, screenshots and videos are disabled for authentication tests because they can contain enrollment secrets.

See [verification results](docs/verification.md) for actual execution evidence and outstanding checks. Build success is not a claim that every enterprise acceptance requirement has passed.

## Configuration and deployment

Read [provider configuration](docs/federation.md), [operations and deployment](docs/operations.md), [security decisions](docs/security.md), [API contracts](docs/api.md), and the [real-device acceptance procedure](docs/mobile-acceptance.md).

Google/Microsoft application credentials, enterprise tenant approval, production SMTP/Twilio credentials, public DNS/TLS, a registry and a deployment cluster must be supplied by the operator. No cloud account or cluster was supplied for this task. The local deployment and production templates are distinct deliverables; a chart is not evidence of a production rollout.
