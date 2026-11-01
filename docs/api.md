# API contracts

The live OpenAPI 3 document is generated from Spring request/response DTOs at authenticated `GET /api/openapi`. Application endpoints are prefixed `/api/v1`. Mutations require the session-bound token from `GET /auth/csrf` in the returned header name (normally X-CSRF-TOKEN). Credentials, TOTP and delivery codes never pass through these APIs.

| Route | Authorization / contract |
|---|---|
| GET /auth/csrf | Anonymous allowed; creates CSRF/session context; returns token, headerName |
| GET /auth/providers | Anonymous allowed; login/registration initiation links |
| POST /auth/sso/discovery | CSRF; {identifier}; 20 requests/minute/source; returns authorizationUrl for an approved provider |
| GET /me | Fully authenticated, verified contact and MFA; minimal profile and memberships |
| POST /auth/logout-current | Revokes current Keycloak session and invalidates BFF session |
| POST /auth/logout-all | Recent authentication; revokes every identity session |
| GET /security/mfa | Credential metadata only, enrollment/recovery initiation links |
| DELETE /security/mfa/{credential} | Recent authentication; own OTP credential; replacement must exist |
| GET /security/sessions | Own sessions only; no tokens |
| DELETE /security/sessions/{id} | Recent authentication; explicit ownership check |
| GET /security/events | Own last 50 application security events |
| POST /organizations | Recent authentication; {name,slug}; creates OWNER; 3/hour/user |
| GET /organizations/{id} | Active membership and current policy |
| GET/PUT /organizations/{id}/security-policy | ADMIN/OWNER; PUT recent authentication and {ssoOnly,mfaMode,jitEnabled,version}; stale version → 409 |
| GET/PUT /organizations/{id}/sso | ADMIN/OWNER; PUT recent authentication and {enabled,version}; operator approval required |
| GET /organizations/{id}/members | ADMIN/OWNER |
| PUT /organizations/{id}/members/{user} | ADMIN/OWNER, recent authentication; {role,status,version}; cannot modify self or OWNER |
| POST /organizations/{id}/invitations | ADMIN/OWNER, recent auth; {email,role}; 10/minute; returns a one-time-displayed invitation token/link |
| POST /invitations/accept | Verified matching email; {token}; transaction locks invitation; same-user retries are idempotent |
| GET /organizations/{id}/events | ADMIN/OWNER; last 100 organization audit events |

Policy modes: OPTIONAL, MANDATORY, TRUSTED_EXTERNAL. The realm baseline may be stricter than an organization's policy. An approved issuer/alias mapping is required before enabling SSO-only or JIT.

Registration, reset password, verify email, phone verification, QR confirmation, TOTP challenge and recovery-code challenge are hosted Keycloak flows. Initiate at `/oauth2/authorization/keycloak`; use `mode=register`, allowlisted `provider=google|microsoft`, or allowlisted `action=CONFIGURE_TOTP|CONFIGURE_RECOVERY_AUTHN_CODES|UPDATE_PASSWORD|UPDATE_PROFILE|VERIFY_CONTACT`. Actions force fresh primary and MFA authentication. State, nonce, code exchange and PKCE are managed by Spring Security.

Domain errors use Problem Details: 400 validation, 401 authentication/expired session, 403 authorization/step-up, 404 unavailable discovery/session, 409 stale version or policy conflict, 429 throttled, 503 dependency failure. Security-filter errors may use an empty status response. Clients must handle status without assuming an error body.

POST organization/invitation creation is not automatically retried after an ambiguous network failure. Slug uniqueness prevents duplicate organizations. Invitations have independently random 256-bit tokens, only SHA-256 hashes are stored, expire in 48 hours, and bind to verified recipient email. Accepting a previously consumed invitation does not reactivate revoked membership. Enrollment/recovery transaction semantics are managed at the identity boundary.
