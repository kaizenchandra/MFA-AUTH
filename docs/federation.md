# Federation configuration

## Google and Microsoft account login

Configure credentials before first local realm provisioning. Restarting provisioning deliberately does not overwrite existing providers.

Google:
1. Create a Google Cloud OAuth consent configuration and Web application client.
2. Add the exact redirect `http://localhost:18180/realms/network/broker/google/endpoint` for development; use your HTTPS identity host in production.
3. Set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET.
4. The broker pins issuer `https://accounts.google.com`, validates signing keys and uses Code + PKCE.

Microsoft:
1. Create an Entra application registration for the approved tenant.
2. Add redirect `http://localhost:18180/realms/network/broker/microsoft/endpoint`.
3. Set MICROSOFT_CLIENT_ID, MICROSOFT_CLIENT_SECRET and the exact MICROSOFT_TENANT_ID UUID.
4. The broker pins that tenant's v2 issuer and discovery endpoints. The `common` and `organizations` endpoints are intentionally rejected by bootstrap.

Microsoft social OIDC login and Microsoft Authenticator TOTP are separate capabilities. Google OIDC login and Google Authenticator are also independent. No push approvals or number matching are implemented.

The default first-broker-login flow requires secure confirmation/reauthentication for existing identities; email equality is never an automatic merge. Upstream email is not trusted by default. Missing contact information leads to profile completion and contact verification.

## Organization OIDC / Entra / Google Workspace

An organization owner creates the organization in the application. An identity operator reviews the issuer, tenant restrictions, redirect URI, signing keys, user eligibility and assurance claims. Use [the onboarding script](../scripts/onboard-provider.mjs) with a JSON specification and an ephemeral KEYCLOAK_OPERATOR_TOKEN.

Example specification (no secrets in this file):

```json
{
  "organizationId": "REPLACE_WITH_ORGANIZATION_UUID",
  "alias": "org-example",
  "displayName": "Example company",
  "protocol": "OIDC",
  "issuer": "https://login.microsoftonline.com/REPLACE_TENANT_UUID/v2.0",
  "authorizationUrl": "https://login.microsoftonline.com/REPLACE_TENANT_UUID/oauth2/v2.0/authorize",
  "tokenUrl": "https://login.microsoftonline.com/REPLACE_TENANT_UUID/oauth2/v2.0/token",
  "jwksUrl": "https://login.microsoftonline.com/REPLACE_TENANT_UUID/discovery/v2.0/keys",
  "clientId": "REPLACE_CLIENT_ID",
  "clientSecretEnv": "EXAMPLE_ORG_CLIENT_SECRET",
  "tenantId": "REPLACE_TENANT_UUID"
}
```

Register the exact broker callback `https://IDENTITY_HOST/realms/network/broker/org-example/endpoint`. Restrict enterprise-app assignment to approved members. Only turn on JIT when the upstream tenant/client mapping itself proves eligibility. For Google Workspace, use a dedicated approved federation client and upstream eligibility restriction; a public Google account with a matching email suffix is not proof of Workspace membership. Domain discovery requires a verified DNS TXT record `_network-verification.example.com = network-org=ORGANIZATION_UUID`.

The local script uses Docker Compose's application PostgreSQL for the approval mapping. Production operators must apply the same parameterized mapping through the protected migration/administration process; the local onboarding script is not a remote cloud deployment tool.

## SAML

The onboarding script accepts protocol SAML with a pinned `issuer`, `entityId`, HTTPS `singleSignOnServiceUrl`, and signing certificate. It requires assertion and request signatures, uses persistent NameID and exact broker callback registration. Import/signing-certificate rotation must be reviewed; never enable unsigned assertions or trust arbitrary metadata URLs. SAML MFA assurance currently falls back to application TOTP. Test metadata, NameID stability, signature wrapping defenses, audience, recipient, InResponseTo, replay handling and logout with the actual provider before enabling production.

## External MFA

Set `trustedAcr` only after obtaining and testing a provider-specific assurance contract. The extension accepts an exact ACR from Keycloak's validated OIDC token, with exact issuer and optional tenant match. An AMR string named `mfa` by itself is not accepted. Enable TRUSTED_EXTERNAL policy for the organization. Leave this property absent to require application MFA.

This path requires acceptance against the real provider, including password-only and downgraded sessions. No external provider credentials were supplied, so external Google/Microsoft/SAML and real enterprise assurance are not certified by local test results.

## Local company test provider

The separate `company` realm acts as a controlled OIDC provider for alias `acme`. Run the development SQL fixture after startup. Create test users in that realm through the admin API/console; do not reuse production credentials. The company flow performs upstream login, returns through the network broker, enforces local contact/MFA requirements, and provisions only a MEMBER of the approved Acme organization when JIT is enabled.
