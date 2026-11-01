Act as a **Principal Software Architect, Identity and Access Management (IAM) Architect, Senior Full-Stack Engineer, Security Engineer, and DevSecOps Lead**.

Your task is to **design, implement, integrate, test, secure, and deploy a complete enterprise-grade authentication and user registration module** for a LinkedIn-like professional networking platform.

This is an **implementation task, not merely an architecture or documentation exercise**.

Deliver working backend code, frontend authentication screens, database migrations, identity-provider integrations, MFA enrollment, authorization policies, automated tests, local infrastructure, deployment configuration, and operational documentation.

Implement the complete user journey:

**Registration → Email/Mobile Verification → Login → MFA Enrollment → QR Code Scan → TOTP Verification → Authenticated Session → Protected Application → Logout**

Also implement:

**Organization SSO → Organization Identity Provider → Authentication → MFA Policy Evaluation → Authorized Application Access**

Users must be able to configure either **Microsoft Authenticator or Google Authenticator on their mobile devices** by scanning an application-generated QR code and subsequently entering the six-digit code to complete MFA.

---

### 2. EXISTING PROJECT AND IMPLEMENTATION INSTRUCTIONS

Before implementing:

1. Inspect the existing repository, services, frontend, architecture, dependency versions, database schemas, security configuration, and coding conventions.
2. Read applicable project instructions and architecture documentation.
3. Identify existing authentication, user, organization, notification, API gateway, and authorization components.
4. Reuse established components and infrastructure wherever suitable.
5. Avoid duplicating user management, credential stores, session management, or identity-provider functionality.
6. Preserve unrelated functionality and existing database records.
7. Identify existing API and event contracts before changing them.
8. Avoid introducing new microservices or dependencies without a justified operational or architectural need.
9. Use official documentation for version-specific behavior.
10. Make routine technical decisions independently, but explicitly flag genuine security, compatibility, or architectural blockers.

If an existing repository is available, modify and integrate with it rather than creating an unrelated demonstration application.

If no repository is provided, create an independently runnable, modular implementation with clearly documented assumptions.

### Definition of completion

A requirement is complete only when its relevant backend, frontend, persistence, security configuration, tests, and user-facing behavior are implemented and verified.

Do not claim a feature is implemented merely because an endpoint, interface, mock, or architecture diagram exists.

---

### 3. TECHNOLOGY STACK

Use the existing project's stack where one is already established.

For a new implementation, prefer:

| Layer | Preferred technology |
|---|---|
| Backend | Java 21, Spring Boot |
| Security | Spring Security 6+, OAuth 2.0, OpenID Connect |
| Identity Provider | Keycloak, open source |
| Enterprise Federation | OIDC, SAML 2.0 where required |
| Frontend | Next.js / React, TypeScript |
| Database | PostgreSQL |
| Cache / Session Support | Redis |
| Database Migration | Flyway |
| API Contracts | OpenAPI 3.x |
| API Integration | Spring Security OAuth2 Client / Resource Server |
| Authorization | RBAC with organization-scoped authorization |
| Containerization | Docker, Docker Compose |
| Cloud | AWS, with LocalStack for applicable AWS integrations |
| Orchestration | Kubernetes |
| Testing | JUnit 5, Spring Boot Test, Testcontainers, Playwright |
| Observability | OpenTelemetry, Micrometer, Prometheus, Grafana |
| Secrets | AWS Secrets Manager / KMS or equivalent |
| CI/CD | Existing repository pipeline, otherwise GitHub Actions |

Prefer **Keycloak for identity-provider capabilities, identity brokering, and standards-based authentication flows**.

Do not implement a custom OAuth 2.0 authorization server, password cryptography, token signer, or TOTP engine if a maintained identity platform provides the required behavior.

Use custom extensions only where necessary, and keep those extensions minimal, documented, isolated, and tested.

Pin and document compatible dependency versions. Do not assume the newest release is compatible without verification.

---

## 4. CORE FUNCTIONAL REQUIREMENTS

### FR-01: User Registration

Implement registration using:

- Email address and password
- Mobile number and password
- Google account using OpenID Connect
- Microsoft account using OpenID Connect
- Organization invitation
- Organization SSO with just-in-time provisioning, when permitted

Registration fields:

- First name
- Last name
- Email address and/or mobile number
- Password for locally managed accounts
- Confirm password
- Country calling code for phone registration
- Acceptance of Terms and Privacy Policy
- Optional organization invitation token

Requirements:

1. Email registration must require email verification.
2. Mobile registration must require phone verification using a one-time code.
3. Normalize email and phone identifiers correctly.
4. Use E.164 for telephone numbers.
5. Reject duplicate identities according to verified identifier and account-linking rules.
6. Enforce configurable password and account-protection policies.
7. Validate all inputs on both frontend and backend.
8. Prevent account enumeration.
9. Rate-limit registration and verification requests.
10. Ensure registration retries do not create duplicate users.
11. Ensure unverified users cannot receive privileges requiring verified identity.
12. Provide controlled resend and expiry behavior for verification codes.
13. Support an account registered by phone subsequently adding and verifying an email, and vice versa.

For phone-based registration, explicitly identify how the selected identity platform supports the registration and verification flow. If a custom extension or verification provider is required, implement and test it instead of assuming native Keycloak support.

For local development, supply a test OTP delivery adapter. For deployed environments, use an appropriately configured email/SMS provider.

Never expose verification codes in production API responses or logs.

### FR-02: Login and Authentication

Implement:

- Email and password login
- Mobile number and password login
- Google social login
- Microsoft social login
- Organization SSO
- MFA-enabled login
- Password reset
- Session restoration
- Logout from the current session
- Logout from all sessions
- Session and device management
- Account lockout / throttling
- Account recovery

Use **OpenID Connect for authentication** and OAuth 2.0 for delegated authorization.

Use Authorization Code Flow with PKCE (S256) for browser-based authorization flows.

Do not implement implicit grant or resource-owner-password grant as application authentication shortcuts.

For browser applications, prefer a Backend-for-Frontend (BFF) architecture with server-side token storage and secure HttpOnly cookies where practical.

Avoid storing long-lived access or refresh tokens in `localStorage`.

### FR-03: Google and Microsoft OAuth Login

Implement the following frontend actions:

- Continue with Google
- Continue with Microsoft

Backend/identity requirements:

1. Initiate proper authorization requests.
2. Handle secure redirects and callbacks.
3. Validate `state`, `nonce`, issuer, audience, signature, and token expiry as applicable.
4. Use OIDC discovery and validated signing keys.
5. Establish the application session only after authentication and policy checks.
6. Provision users only where registration policies allow.
7. Link external accounts using immutable provider identity identifiers.
8. Never silently merge existing accounts based solely on email equality.
9. Require secure reauthentication before linking a new identity provider to an existing account.
10. Handle canceled authorization, provider outage, invalid tokens, missing email claims, and disabled accounts.

Google social login, Microsoft social login, Google Authenticator, and Microsoft Authenticator must be treated as distinct capabilities.

---

## 5. ENTERPRISE ORGANIZATION SSO

### FR-04: Organization SSO

Implement multi-tenant organization SSO supporting:

- Microsoft Entra ID
- Google Workspace identity federation
- Standards-compliant OIDC identity providers
- SAML 2.0 identity providers where required

Use Keycloak identity brokering or an equivalently appropriate existing federation layer.

Organization administrator capabilities:

- Register an organization
- Configure organization identity provider
- Manage issuer/metadata configuration
- Configure allowed redirect and callback behavior
- Enable or disable organization SSO
- Enforce SSO-only login
- Enforce mandatory MFA
- Manage allowed users and organizational access
- Configure just-in-time provisioning
- Configure organization roles
- View authentication and policy audit events

Organization SSO discovery must support:

- Explicit organization identifier
- Verified corporate email-domain discovery
- Organization-specific SSO URLs

Domain matching is discovery only; it is not proof that a user belongs to an organization.

Secure organization membership using validated identity-provider claims, verified tenant mappings, approved invitations, or authorized provisioning.

For Microsoft Entra ID, validate tenant identity and issuer against the organization's approved configuration.

Implement isolation between organizations. Prevent a user authenticated through one organization from obtaining access to another merely by changing an organization ID or email address.

### Required enterprise SSO journey

1. User opens the login page.
2. User selects **Continue with Company SSO**.
3. User enters their corporate email or organization identifier.
4. Backend resolves the configured organization.
5. Application redirects the user to the trusted organization identity provider.
6. User authenticates with the organization's identity provider.
7. The organization enforces its configured MFA policy.
8. Application validates the federated authentication result.
9. Backend resolves or provisions the local application identity.
10. Backend verifies organization membership, roles, account status, and MFA requirements.
11. User receives an application session and is redirected to the authorized dashboard.

Implement safe handling for identity-provider errors, timeouts, denied access, unmapped organizations, revoked access, and invalid federation metadata.

---

## 6. MULTIFACTOR AUTHENTICATION — CORE REQUIREMENT

### FR-05: Authenticator-Based MFA

Implement TOTP-based multifactor authentication compatible with:

- Microsoft Authenticator
- Google Authenticator
- Other standards-compatible TOTP applications

Use RFC 6238 and the conventional `otpauth://` provisioning URI.

Compatibility defaults:

- Algorithm: HMAC-SHA1
- Code length: 6 digits
- Code rotation: 30 seconds
- Cryptographically secure per-user secrets
- Limited clock-drift tolerance, preferably at most one adjacent time window
- Bounded verification attempts

Do not create separate Microsoft and Google MFA protocols. Both applications should work with the same interoperable TOTP enrollment mechanism.

Do not confuse standard TOTP verification with Microsoft Authenticator push approvals, number matching, passwordless login, or Microsoft Entra-managed MFA APIs.

### FR-06: MFA QR Code Enrollment

Implement a complete MFA enrollment wizard.

**Step 1 — Initiate**

- User signs in.
- User navigates to Settings → Security → Two-Factor Authentication.
- User selects Enable Authenticator App.
- Backend requires recent authentication or equivalent step-up verification.

**Step 2 — Generate secret**

- Generate a unique, unpredictable secret for the enrollment.
- Store it securely as a pending enrollment.
- Associate it with the authenticated account and enrollment transaction.
- Set a short enrollment expiration.
- Prevent one user from changing another user's enrollment.

**Step 3 — Generate QR code**

Generate a standards-compatible provisioning URI:

`otpauth://totp/{issuer}:{account}?secret={secret}&issuer={issuer}&algorithm=SHA1&digits=6&period=30`

Replace placeholders with securely encoded application and account values.

Do not use a hardcoded demonstration secret in real enrollment.

Render a scannable QR code, preferably generated through a reviewed library.

Display:

- QR code
- Application/account identifier
- Instructions for Microsoft Authenticator
- Instructions for Google Authenticator
- Manual setup key as a fallback
- Input for six-digit verification code
- Continue / Verify action

Protect QR codes and setup secrets as authentication credentials.

Apply `Cache-Control: no-store`, prevent sensitive analytics capture, and ensure secrets are not logged or leaked through unrelated APIs.

**Step 4 — Mobile enrollment**

Microsoft Authenticator instructions:

1. Open Microsoft Authenticator.
2. Select Add Account (+).
3. Select Other Account.
4. Scan the QR code.
5. Locate the six-digit verification code.

Google Authenticator instructions:

1. Open Google Authenticator.
2. Select Add a Code (+).
3. Select Scan a QR Code.
4. Scan the displayed QR code.
5. Locate the six-digit verification code.

Provide an option to enter the secret manually if QR scanning is unavailable.

**Step 5 — Enrollment verification**

1. User enters the six-digit code.
2. Backend validates it against the pending secret.
3. Reject invalid, expired, replayed, or excessively attempted codes.
4. Atomically activate MFA only after successful verification.
5. Generate recovery codes.
6. Show recovery codes once and require user acknowledgement.
7. Record a security audit event.
8. Notify the user that MFA was enabled.

A pending QR enrollment must never by itself activate MFA.

### FR-07: Login Using Authenticator Code

Implement the following login states:

`UNAUTHENTICATED → PRIMARY_AUTHENTICATED → MFA_REQUIRED → FULLY_AUTHENTICATED`

After successful primary authentication:

1. Determine whether MFA is mandatory for the account and current organization.
2. If MFA is required and not yet enrolled, direct the user to mandatory enrollment.
3. If already enrolled, present the MFA verification screen.
4. User opens Microsoft Authenticator or Google Authenticator.
5. User enters the current six-digit code.
6. Backend validates the code using the configured authenticator.
7. Backend completes the authentication flow.
8. User receives a fully authorized application session.
9. User is redirected to the requested protected page.

Before MFA completion:

- Do not grant full application access.
- Do not issue a session with unrestricted application privileges.
- Permit only narrowly scoped endpoints required to complete authentication, recovery, or enrollment.
- Ensure MFA state cannot be elevated by modifying frontend state, session attributes, JWT claims, or client requests.

Prevent reuse of an accepted TOTP in the same time interval wherever supported by the identity provider. If there is a provider limitation, document the limitation and implement appropriate compensating controls.

### FR-08: MFA and Organization SSO Policy

Implement an explicit policy for:

- Locally managed user MFA
- Social-login user MFA
- Organization-managed MFA
- Application-enforced step-up authentication

When organization SSO already satisfies the configured MFA assurance requirement, do not automatically require a redundant second challenge.

Evaluate trustworthy assurance information from the identity provider where supported, such as appropriately validated `acr` or `amr` claims.

Do not assume that every valid SSO login included MFA.

If the organization's assurance policy cannot be verified, enforce an appropriate application MFA challenge or deny access according to policy.

Provide clear configuration for:

- MFA optional
- MFA mandatory
- MFA satisfied by trusted external identity provider
- MFA required again for sensitive operations

---

## 7. MFA RECOVERY AND LIFECYCLE

### FR-09: Recovery Codes

Generate a set of cryptographically secure recovery codes after MFA activation.

Requirements:

- Single-use
- Stored using appropriate one-way hashing
- Never stored in plaintext
- Displayed only at creation
- Download/copy supported
- Rate-limited validation
- Atomic consumption
- Audit logging
- Regeneration invalidates previous unused codes
- Regeneration requires step-up authentication

### FR-10: MFA Device and Security Settings

Implement:

- MFA status
- Enable MFA
- Disable MFA
- Replace authenticator
- Re-enroll after mobile-device loss
- Regenerate recovery codes
- View session/device history
- Revoke sessions
- Recent security events

Sensitive operations must require recent reauthentication and appropriate MFA or secure recovery verification.

Do not permit MFA bypass through password reset, account linking, support workflows, or alternate login methods.

Provide an audited administrative recovery procedure with strong authorization, identity verification, and separation of duties where appropriate.

---

## 8. FRONTEND IMPLEMENTATION

Build and integrate responsive, accessible authentication UI for:

1. Registration
2. Email verification
3. Mobile OTP verification
4. Login
5. Continue with Google / Microsoft
6. Organization SSO discovery
7. MFA required
8. MFA enrollment
9. QR code setup
10. Microsoft/Google Authenticator instructions
11. TOTP verification
12. Recovery code display
13. Recovery code login
14. Forgot/reset password
15. Security settings
16. Active session management
17. Organization security policy administration
18. Authentication failures and recovery

### UX requirements

- Responsive desktop/mobile layouts
- Accessible forms and keyboard navigation
- Loading, success, failure, and expired-session states
- Correct focus management
- Clear field validation
- Paste support for verification codes
- Resend countdown for delivery-based OTPs
- Six-digit TOTP input that accepts leading zeros
- QR refresh only through a controlled enrollment lifecycle
- Password visibility toggle
- Safe redirect to originally requested page
- Helpful clock synchronization error when appropriate
- No sensitive credentials in browser console output

Maintain a single consistent authentication state derived from the authoritative server.

Do not rely on frontend role checks for security.

Use the existing design system and routing framework when present.

---

## 9. BACKEND ARCHITECTURE

Use pragmatic DDD and Clean/Hexagonal Architecture.

Recommended logical components:

- Authentication integration
- Identity and account management
- Organization and tenant management
- Identity-provider federation configuration
- MFA policy and enrollment integration
- Authorization policies
- Session and token lifecycle
- Security auditing
- Email/SMS notification adapters

Keep credential verification, TOTP secrets, and session issuance under the identity platform's responsibility wherever supported.

Maintain business-specific user profiles, organization membership, invitations, and authorization mappings in the application domain.

Do not duplicate all Keycloak identity records in a second credential database.

Use appropriate ports/adapters for integration boundaries, but avoid excessive service fragmentation.

### Identity model

Model at least:

- User
- ExternalIdentity
- VerifiedContactMethod
- Organization
- OrganizationMembership
- OrganizationIdentityProvider
- OrganizationSecurityPolicy
- SessionReference
- SecurityAuditEvent

For application-managed security data, only create entities that are not already authoritatively managed by the identity provider.

Use stable identifiers and enforce referential integrity.

Identity-provider subject identifiers must be stored and resolved with their issuer/provider context.

### Recommended authentication architecture

```text
Frontend (React / Next.js)
           |
           v
API Gateway / BFF
           |
           +------> Keycloak (Authentication & IAM)
           |            |
           |            +--> Local Identity
           |            +--> Google OIDC
           |            +--> Microsoft OIDC
           |            +--> Organization OIDC/SAML
           |            +--> TOTP MFA
           |
           +------> Spring Boot Application
                          |
                          +--> User / Profile Domain
                          +--> Organization Domain
                          +--> Authorization
                          +--> Security Audit
                          |
                     PostgreSQL / Redis
```

This is a reference architecture, not a mandate to introduce services that the repository does not need.

---

## 10. API AND EVENT CONTRACTS

Inspect the identity provider's existing OIDC endpoints and management APIs before introducing custom routes.

Where an application-facing BFF/API is required, define documented contracts for these capabilities:

| Capability | Example logical endpoint |
|---|---|
| Current user | `GET /api/v1/me` |
| Logout | `POST /api/v1/auth/logout` |
| Logout all sessions | `POST /api/v1/auth/logout-all` |
| Organization SSO discovery | `POST /api/v1/auth/sso/discovery` |
| MFA status | `GET /api/v1/security/mfa` |
| MFA enrollment initiation | `POST /api/v1/security/mfa/enrollments` |
| MFA enrollment confirmation | `POST /api/v1/security/mfa/enrollments/{id}/confirm` |
| MFA disable | `DELETE /api/v1/security/mfa` |
| Recovery-code regeneration | `POST /api/v1/security/mfa/recovery-codes/rotate` |
| Session listing | `GET /api/v1/security/sessions` |
| Session revocation | `DELETE /api/v1/security/sessions/{id}` |
| Organization SSO settings | `GET/PUT /api/v1/organizations/{id}/sso` |
| Organization security policy | `GET/PUT /api/v1/organizations/{id}/security-policy` |

These are proposed contracts, not requirements to duplicate Keycloak-managed endpoints.

If the identity platform handles enrollment, challenges, registration, or password reset through its own secure hosted flow, integrate that flow rather than creating unnecessary credential-handling endpoints.

For every application endpoint:

- Define request and response DTOs.
- Define authorization requirements.
- Validate request inputs.
- Define error contracts using Problem Details where appropriate.
- Ensure correct tenant scoping.
- Document idempotency requirements.
- Define rate limits.
- Do not reveal secrets after enrollment.
- Publish OpenAPI specifications.

If Kafka already exists in the platform, integrate security audit events through an appropriate reliable publishing mechanism when required.

Do not add Kafka solely for synchronous authentication or OTP validation.

---

## 11. SECURITY CONTROLS

Implement or verify:

**Identity and sessions**

- Secure password hashing through the identity provider
- Password reset with short-lived single-use tokens
- Account enumeration protection
- Authentication throttling
- Refresh-token rotation where applicable
- Refresh-token reuse detection where supported
- Session invalidation
- Account disablement
- Device/session revocation
- Appropriate token expiry
- Secure signing-key rotation

**OAuth 2.0 / OIDC**

- Authorization Code + PKCE
- Exact redirect URI validation
- State/nonce validation
- Issuer and audience validation
- JWK signature verification
- Token substitution/mix-up defenses
- CSRF protection
- Safe redirect validation
- No trust in unverified ID token payloads
- No access tokens in query parameters

**Application security**

- HTTPS
- Secure and HttpOnly cookies
- Appropriate SameSite policy
- Secure CORS configuration
- CSP and security headers
- CSRF protection for cookie-authenticated mutations
- Strict tenant isolation
- Server-side authorization
- Secret encryption at rest where reversible storage is necessary
- Secret-manager integration
- Logging redaction

**MFA security**

- CSPRNG-generated secrets
- Secure storage with encryption and key rotation controls
- Enrollment expiration
- Verification attempt limits
- Replay protection
- Atomic enrollment and code consumption
- Recovery-code protection
- Audited disable/reset operations
- Reauthentication for security changes
- No MFA bypass through alternate authentication paths

**Audit and observability**

Audit:

- Registration
- Successful/failed login
- SSO login
- MFA enrollment
- MFA verification failure
- MFA reset/disable
- Password reset
- Account linking
- Session revocation
- Organization SSO policy changes
- Privileged administrator actions

Never log passwords, active TOTP values, recovery codes, TOTP secrets, raw session cookies, or bearer tokens.

Add structured logs, correlation IDs, security metrics, traces, and actionable alerts without exposing sensitive authentication data.

---

## 12. FAILURE HANDLING AND DISTRIBUTED SYSTEM CONCERNS

Handle at minimum:

- Duplicate registration requests
- Parallel identity provisioning
- Concurrent MFA enrollment attempts
- Concurrent MFA confirmation requests
- Repeated verification of the same TOTP
- Redis outage
- Database outage
- Identity-provider outage
- Expired SSO authorization state
- OIDC callback replay
- Stale JWK metadata
- Token refresh races
- User disabled during an active session
- Organization membership revoked
- Cross-organization authorization attempts
- Delayed SMS/email delivery
- Incorrect device clock
- Lost mobile device
- Recovery-code race conditions
- Identity-provider configuration changes
- SSO-only policy enabled while users have active local sessions

Apply appropriate timeouts, bounded retries, transaction boundaries, idempotency, optimistic/pessimistic concurrency controls, and fail-closed behavior at security-critical boundaries.

Do not blindly retry credential validation, token redemption, or other non-idempotent operations.

Document session consistency and revocation latency where immediate revocation is not technically guaranteed.

---

## 13. DATABASE AND DATA INTEGRITY

Create version-controlled migrations for application-owned tables.

Include:

- Primary and foreign keys
- Unique identity/provider constraints
- Organization-scoped uniqueness
- Indexes for principal lookup
- Membership indexes
- Audit-event indexes
- Appropriate lifecycle/retention fields
- Optimistic version fields where needed
- Minimal PII storage
- Encryption for recoverable security secrets, if application-managed

Use database transactions for sensitive state transitions.

If the identity provider has its own database schema, do not directly write to or modify that schema.

Use supported administration APIs, federation mechanisms, or extension points.

---

## 14. LOCAL DEVELOPMENT AND DEPLOYMENT

Provide a runnable local environment using Docker Compose with:

- Keycloak
- PostgreSQL
- Redis
- Spring Boot backend
- Frontend
- Observability components where appropriate
- Local email test service
- Mock or test SMS provider

Provide a reproducible Keycloak configuration with:

- Realm
- OIDC clients
- Roles
- Authentication flows
- TOTP policy
- Registration settings
- Federation placeholders
- Test identities
- Organization test configuration

Never commit real client secrets or production identity-provider credentials.

Use placeholders and environment-specific secret injection.

Support development, test, staging, and production configuration.

Provide Kubernetes manifests or Helm configuration consistent with the existing deployment conventions.

Include health checks, readiness checks, resource limits, security contexts, TLS integration, secret configuration, backup guidance, and rollback instructions.

Use LocalStack only for AWS services actually required by the implementation.

---

## 15. TESTING REQUIREMENTS

Implement automated tests at the appropriate layers.

### Registration tests

- Valid email registration
- Valid mobile registration
- Duplicate account prevention
- Invalid verification code
- Expired verification code
- Code resend limits
- Concurrent registration
- Unauthorized verification attempts

### OAuth and SSO tests

- Valid Google OIDC login
- Valid Microsoft OIDC login
- Valid organization SSO login
- Invalid issuer
- Invalid audience
- Invalid signature
- Expired token
- Incorrect nonce/state
- PKCE failure
- Wrong organization/tenant
- Account-linking attack attempts
- Denied federation login
- Provider unavailability

Use controlled test identity providers where real external-provider integrations cannot run automatically.

### MFA tests

- QR enrollment initiation
- QR secret compatibility
- Valid TOTP confirmation
- Invalid TOTP
- Expired TOTP
- Adjacent-window tolerance
- Replayed TOTP
- Excessive failed attempts
- Concurrent code verification
- Enrollment interruption
- Pending enrollment expiration
- Recovery-code success
- Recovery-code reuse rejection
- MFA disable authorization
- MFA reset policy
- Login blocked until MFA completion
- SSO MFA assurance evaluation

### End-to-end frontend tests

Use Playwright or the repository's established E2E framework.

Test:

1. Register user.
2. Verify identity.
3. Log in.
4. Open Security Settings.
5. Start MFA enrollment.
6. Display QR code.
7. Validate its provisioning URI.
8. Generate a valid TOTP in test automation from the test secret.
9. Confirm MFA enrollment.
10. Log out.
11. Log in again.
12. Enter TOTP.
13. Access protected dashboard.
14. Reject incorrect/replayed codes.
15. Exercise account recovery.
16. Validate organization SSO flow using a test provider.

Validate the QR output against real Microsoft Authenticator and Google Authenticator on mobile devices during acceptance testing. Automated interoperability checks alone do not prove real-device compatibility.

Run tests and report actual execution results. Never mark an unexecuted test as passed.

---

## 16. ACCEPTANCE CRITERIA

The implementation is accepted only when:

- [ ] Users can register with email and verify their email.
- [ ] Users can register with mobile number and verify ownership.
- [ ] Local users can log in with supported verified identifiers and password.
- [ ] Google OIDC login is integrated.
- [ ] Microsoft OIDC login is integrated.
- [ ] Organization SSO works with configured test identity providers.
- [ ] Organization SSO policies and tenant isolation are enforced.
- [ ] Users can initiate MFA enrollment.
- [ ] A standards-compatible QR code is generated.
- [ ] Microsoft Authenticator can enroll the account using that QR code.
- [ ] Google Authenticator can enroll the account using that QR code.
- [ ] TOTP enrollment requires successful code confirmation.
- [ ] Subsequent logins require TOTP when policy demands it.
- [ ] Invalid, expired, and replayed codes are rejected according to the implemented policy.
- [ ] Users cannot access protected resources before completing mandatory MFA.
- [ ] SSO assurance is evaluated correctly without assuming MFA.
- [ ] Recovery codes are supported and single-use.
- [ ] MFA reset and disable flows are protected.
- [ ] Password reset does not bypass MFA.
- [ ] Sessions can be revoked.
- [ ] API authorization and organization isolation are tested.
- [ ] Backend and frontend are integrated.
- [ ] Relevant automated tests pass.
- [ ] Local setup is reproducible.
- [ ] Security events are audited without leaking secrets.
- [ ] Deployment configuration and operational guidance are documented.

Distinguish automated test coverage from real mobile-device validation and externally configured Microsoft/Google integrations.

---

## 17. IMPLEMENTATION EXECUTION PLAN

Execute the work in this order:

**Phase 1 — Repository assessment**

Inspect existing code, architecture, dependencies, authentication flows, deployment and test infrastructure. Identify gaps and design decisions.

**Phase 2 — Architecture and contracts**

Define identity ownership, account lifecycle, tenant model, authentication state machine, authorization policies, OIDC integration, MFA strategy, and API contracts.

**Phase 3 — Identity infrastructure**

Configure Keycloak or the existing identity provider, PostgreSQL, local infrastructure, OAuth clients, federation configuration, and secure baseline settings.

**Phase 4 — Backend implementation**

Implement registration integration, user resolution, verification adapters, organization management, SSO configuration, policy enforcement, session integration, authorization, and audit functionality.

**Phase 5 — MFA implementation**

Configure and integrate authenticator enrollment, QR provisioning, TOTP verification, recovery codes, trusted federation assurance, and account recovery.

**Phase 6 — Frontend integration**

Implement and connect all required login, registration, SSO, MFA, QR enrollment, recovery, and security settings screens.

**Phase 7 — Integration verification**

Run unit, integration, security, and end-to-end tests. Fix failures and verify key attack scenarios.

**Phase 8 — Deployment**

Complete local infrastructure, deployment configuration, secrets strategy, observability, operational guidance, and documented smoke tests.

Do not stop at architecture diagrams or a list of suggested steps. Carry changes through the repository within the authorized scope.

---

## 18. REQUIRED FINAL DELIVERABLES

Provide:

1. Architecture overview and rationale.
2. Actual repository changes.
3. Updated project/file structure.
4. Backend implementation.
5. Frontend implementation.
6. Identity-provider configuration.
7. OAuth 2.0/OIDC and organization SSO integrations.
8. TOTP MFA enrollment and verification.
9. Working QR enrollment interface.
10. Recovery-code and MFA lifecycle implementation.
11. Database migrations.
12. OpenAPI contracts and security requirements.
13. Automated tests.
14. Docker Compose environment.
15. Deployment configuration.
16. Environment variable and secrets documentation.
17. Local run instructions.
18. Google/Microsoft OAuth configuration instructions.
19. Microsoft Entra ID and Google Workspace SSO configuration instructions.
20. Real-device MFA acceptance procedure.
21. Security and operational documentation.
22. Verification results and remaining limitations.

For the final implementation report, include:

- **What changed**
- **Why the architecture was chosen**
- **How to configure and run it**
- **What tests actually passed**
- **Which integrations require external credentials or administrator configuration**
- **Any unverified behavior**
- **Remaining security or operational risks**

### FINAL EXECUTION DIRECTIVE

Implement a complete, maintainable authentication and identity-management solution, not a collection of disconnected examples.

Prioritize correct authentication flows, secure MFA enrollment, verified organization SSO federation, tenant isolation, frontend/backend integration, strong authorization, and real failure-path handling.

Use established standards and mature identity-management capabilities rather than inventing custom authentication protocols.

Do not replace implementation with pseudocode, mock success responses, hardcoded OTP values, disabled security settings, or fabricated verification results.

**The target outcome is an integrated authentication module where a user can register, sign in, enroll Microsoft Authenticator or Google Authenticator by scanning a QR code, enter the generated TOTP during subsequent logins, and securely access the platform, while enterprise users can authenticate through their organization's SSO provider under the configured MFA assurance policy.**