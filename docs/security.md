# Security decisions and boundaries

## Identity state

UNAUTHENTICATED → primary credential/broker authentication → required contact verification and MFA → authorization code → BFF session → organization authorization.

Pending enrollment does not yield an application authorization code. A server-side session note is emitted only after the configured MFA subflow; required actions must finish before token issuance. The application rejects missing contact verification and missing assurance. Claim mappers are administrator-controlled. Never attach the assurance-stamp authenticator to a password-only flow.

All accounts use a mandatory local MFA baseline, unless an operator explicitly configures and validates a trusted external OIDC ACR contract. Organization OPTIONAL does not weaken this realm baseline. MANDATORY requires application MFA; TRUSTED_EXTERNAL accepts an approved provider's validated assurance or local MFA. SAML and OIDC without a proven assurance contract fall back to application TOTP. Merely setting an organization policy does not confer trust on an upstream provider.

The broker extension reads Keycloak's already validated ID token, compares the configured issuer, optionally checks the pinned Entra tenant claim, and matches one exact operator-configured ACR value. It does not decode an untrusted JWT or assume a generic AMR string is sufficient. Changing a provider from trusted external assurance to mandatory local MFA can deny an existing external-only session; the next sign-in must use an appropriately configured local challenge.

## MFA lifecycle

- Native RFC 6238 SHA-1, six digits, 30-second period, one adjacent time window.
- Keycloak generates the QR and setup key. They stay on hosted identity pages with no analytics.
- The extension binds confirmation to Keycloak's pending secret, expires enrollment after five minutes, limits it to five validation attempts and atomically reserves confirmation.
- Keycloak's `otpPolicyCodeReusable=false` uses its cluster single-use store to reject repeated accepted codes.
- Recovery codes use Keycloak's native hashing and ordered-code UI. The extension reserves a valid recovery code in the cluster single-use store before native consumption to reject concurrent reuse.
- Regeneration replaces the entire prior recovery credential. Application-initiated actions require fresh sign-in.
- The BFF permits authenticator removal only after a replacement exists and recent authentication is proven. The last local authenticator cannot be disabled under the mandatory baseline.
- Account-console clients are disabled in provisioning; sensitive lifecycle operations use scoped BFF endpoints and Keycloak required actions.
- Password reset preserves OTP/recovery credentials. It does not grant application access before required actions finish.

Native Keycloak credential secrets need recoverable storage. They are not application database fields. Encrypt Keycloak PostgreSQL storage, replicas and backups using KMS-backed infrastructure, restrict database administrators, protect heap dumps and rotate infrastructure keys. This project does not claim per-field KMS encryption of the native Keycloak TOTP secret.

## Registration and delivery

The registration form validates email or E.164 sign-in identifiers and enforces a cluster-wide per-source rate limit. Keycloak enforces username/email uniqueness. The phone extension associates the code with the user, phone and challenge, stores only a salted hash, expires it in five minutes, allows five attempts, enforces resend cooldown and per-phone hourly limits, and consumes it atomically. A user-editable pending phone cannot set `verified_phone` or `phone_verified`.

The test delivery adapter requires an independent bearer secret and does not log bodies. Production explicitly uses the Twilio adapter with HTTPS and bounded request timeouts; missing configuration fails closed. Delivery is not retried automatically because a timeout may follow successful delivery. Native email verification uses single-use identity-platform action tokens.

Registration error wording is generic, but complete timing-indistinguishability of existing and new account registration has not been demonstrated. Apply perimeter bot controls in production; per-source limits behind NAT have operational tradeoffs.

## Browser, session and tenant controls

HttpOnly SameSite=Lax cookies, Secure in production; same-origin BFF; CSRF tokens for all mutations; exact OIDC redirects; PKCE; no implicit/password grant for application clients; no open application redirects; no bearer token query parameters or localStorage. The frontend never determines authorization.

Redis stores serialized server-side sessions and tokens. Require network isolation, authentication, TLS, at-rest encryption, restricted backups and trusted writers. Redis loss invalidates application sessions. BFF identity introspection is uncached, so Keycloak revocation is checked on the next API request, subject to the identity cluster's own consistency.

Membership revocation, SSO-only changes and organization disablement are checked from PostgreSQL on each organization request. Keycloak subject identity is never inferred from an email address. An organization administrator cannot approve arbitrary federation metadata: an identity operator provisions the provider and mapping, with an audited operational change.

The service client can view/manage realm users for credential metadata and session revocation. This is a privileged integration and a documented blast-radius risk: protect it in a secret manager, restrict network egress to Keycloak, rotate it, and do not expose its token. A production fine-grained admin-permission rollout should be independently tested.

## Administrative recovery

Require two authorized operators and an independently verified identity case. The requester must prove identity through the organization's approved process; email possession alone is not sufficient to remove MFA. Record the case ID, reviewers, evidence references and reason in the organization's ticket/audit system. Revoke all identity sessions, invalidate remaining recovery credentials, and schedule CONFIGURE_TOTP plus recovery-code setup through supported Keycloak Admin APIs. Notify verified contacts. Never give support a reusable bypass code. This is an operational procedure, not an implemented two-person approval application.

## Version-specific references

The implementation was checked against the pinned Keycloak 26.4.7 source:
- [UpdateTotp](https://github.com/keycloak/keycloak/blob/26.4.7/services/src/main/java/org/keycloak/authentication/requiredactions/UpdateTotp.java)
- [OTPCredentialProvider](https://github.com/keycloak/keycloak/blob/26.4.7/services/src/main/java/org/keycloak/credential/OTPCredentialProvider.java)
- [RecoveryAuthnCodesCredentialProvider](https://github.com/keycloak/keycloak/blob/26.4.7/services/src/main/java/org/keycloak/credential/RecoveryAuthnCodesCredentialProvider.java)
- [OIDCIdentityProvider](https://github.com/keycloak/keycloak/blob/26.4.7/services/src/main/java/org/keycloak/broker/oidc/OIDCIdentityProvider.java)
- [Keycloak administration guide](https://www.keycloak.org/docs/latest/server_admin/)
- [Spring Security OAuth2 login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/index.html)

The extensions use internal Keycloak SPIs. Pin the exact version and rerun all credential, browser, broker, concurrency and upgrade tests before any version change. Pinned does not mean vulnerability-free; scan the final image and dependencies before release.
