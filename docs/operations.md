# Operations and deployment

## Versions

Java 21; Spring Boot 3.5.7 (its managed Spring Security/Redis/JDBC/Testcontainers dependency set); Keycloak 26.4.7; PostgreSQL 17.6; Redis 7.4.6; React 19.2.0; TypeScript 5.9.3; Vite 7.1.12; Playwright 1.56.1. Frontend transitive versions are locked. Container image tags are pinned; production releases must additionally pin reviewed digests and produce an SBOM. Internal Keycloak SPIs require upgrade testing.

## Environments and secrets

Compose is development only. It permits HTTP on loopback, disables Secure cookies for that loopback environment, uses Mailpit and an authenticated SMS test adapter, and provisions a separate company test realm. Never expose Compose ports publicly.

Production/staging use HTTPS origins, Secure cookies, verified TLS database/Redis connections, private service networking, managed backups and provider credentials. Use PLATFORM_ENV=production for realm provisioning; this suppresses the test provider, requires HTTPS public URLs and real SMTP settings. Replace the development legal text before public registration.

| Variable | Owner/purpose |
|---|---|
| DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD | BFF application PostgreSQL, separate production role/database |
| REDIS_HOST, REDIS_PASSWORD, SPRING_DATA_REDIS_SSL_ENABLED | Private session store; production TLS |
| APP_ORIGIN | Exact public HTTPS frontend origin |
| IDP_ISSUER | Public exact network realm issuer |
| IDP_INTERNAL | Private Keycloak base URL; use a service-mesh TLS channel in production |
| OIDC_CLIENT_SECRET | Confidential BFF client; shared only between BFF and Keycloak configuration |
| IDP_ADMIN_CLIENT_SECRET | Privileged, minimal service integration; rotate independently |
| KC_DB_URL, KC_DB_USERNAME, KC_DB_PASSWORD | Keycloak-only database and role |
| KC_HOSTNAME | Exact public identity-service origin |
| KEYCLOAK_BOOTSTRAP_PASSWORD | Temporary bootstrap administration; retire after operator setup |
| SMTP_HOST, SMTP_PORT, SMTP_FROM, SMTP_USER, SMTP_PASSWORD | Production realm email delivery |
| SMS_MODE=twilio, TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_FROM | Production SMS transport |
| SMS_TEST_TOKEN, TEST_BROKER_SECRET | Local test environment only |
| GOOGLE_CLIENT_ID/SECRET, MICROSOFT_CLIENT_ID/SECRET/TENANT_ID | Optional external social OIDC providers |

Use AWS Secrets Manager encrypted by KMS with an approved External Secrets Operator ClusterSecretStore and workload identity. The optional Helm ExternalSecret maps a structured secret to the named Kubernetes Secret. No AWS integration is required locally, so LocalStack is not introduced. Restrict access to Kubernetes Secrets and disable environment dumps in incident scripts.

## Kubernetes

The Helm chart deploys the BFF, frontend and custom Keycloak image with readiness/liveness checks, non-root containers, dropped capabilities, resource limits, disruption budgets and TLS ingresses. PostgreSQL and Redis are managed external dependencies. Replace every registry/database/Redis placeholder in values, supply TLS certificates and create the existing secret. The identity ingress deliberately excludes the admin endpoint; operate administration through a private tunnel/network.

```sh
helm lint infra/helm
helm template network infra/helm -f production-values.yaml > rendered.yaml
helm upgrade --install network infra/helm -n network --create-namespace -f production-values.yaml --wait
```

Do not run the final command until the production prerequisites and security acceptance checks are satisfied. The chart has been template-validated; no production cluster rollout has been performed. Federation onboarding's supplied database approval adapter is local; adapt it to your private production administrative path.

Before a production rollout: rebuild the frontend gateway configuration for the production identity origin; preserve trusted forwarded HTTPS headers at the ingress/gateway boundary; install default-deny ingress policies allowing only ingress→frontend/Keycloak, frontend→BFF, BFF→Redis/PostgreSQL/Keycloak, Keycloak→its database/SMTP/SMS/providers and Keycloak cluster traffic. Keycloak JDBC_PING uses database discovery and cluster ports 7800/57800. Apply egress restrictions with a CNI that supports the organization's external provider/DNS requirements. The chart does not pretend that public-cloud CIDR rules can be safely guessed.

Provision the realm once with the deployment's actual origins and production SMTP settings. Create permanent least-privileged operator accounts with hardware-backed MFA; disable temporary bootstrap access. Use separate database users for Keycloak and application storage. Enable RDS/Redis encryption, verify-full certificates and encrypted backups. Run the acceptance suite on the staging environment before opening registration.

## Observability

Application HTTP metrics use Micrometer and Actuator; liveness/readiness run on a separate management port. Keycloak metrics/health are built into its dedicated management port. The optional Compose Prometheus profile scrapes Keycloak. Application Prometheus access requires protected monitoring authorization; it is not exposed anonymously through the application ingress. Integrate your platform's authenticated metrics collector or a management-port-only security chain before enabling scraping.

Every BFF request receives a generated correlation ID. Application mutations write bounded, secret-free audit records; identity events and administrator events are retained by Keycloak with admin request bodies disabled. No passwords, OTPs, recovery codes, QR secrets, cookies, bearer tokens or request bodies belong in traces/logs. Reverse proxies must redact authorization callback query strings (which contain authorization codes) before production access-log collection. Configure structured log transport, an OpenTelemetry collector and a Grafana dashboard through the deployment's existing observability platform; this repository does not provision those cloud services.

Alert on elevated authentication failures, throttling, sustained IdP/Redis/database errors, identity readiness failures, unauthorized organization access, unusual MFA resets and privileged provider/policy changes. Monitor SMTP/SMS failures and NTP drift. Audit stores need access restrictions, retention policy and immutable downstream archival; the application table alone is not tamper-proof against database administrators.

## Failure behavior and consistency

- Redis or IdP outage: protected operations fail closed. Do not bypass MFA to restore availability.
- Database outage: identity may still authenticate, but profile/membership resolution fails and no organization access is granted.
- Token expiration: BFF returns 401; the browser starts OIDC again. No concurrent refresh redemption exists in this implementation.
- Revocation: next application API request introspects identity session state; organization policy/membership is read afresh for every scoped operation. Any remaining upstream Keycloak cluster propagation delay is deployment-dependent.
- Ambiguous token redemption or credential submission: no blind retries. Restart the authorization/enrollment flow if needed.
- Delivery timeout: request a new code after the cooldown; previous pending transactions remain bounded by expiry.
- Provider metadata/certificate change: review and update supported provider configuration, then re-run issuer/audience/signature/tenant and assurance tests. Fail closed if validation cannot succeed.
- Large deployments must performance-test uncached introspection and database authorization reads. Scaling and caching must not silently weaken revocation guarantees.

## Backup, rotation and rollback

Back up application and Keycloak databases separately with point-in-time recovery; encrypt both with KMS and test restores. Redis loss should require reauthentication, not restoration of stale identity grants. Preserve Keycloak signing keys and provider configuration using approved database backup/export procedures; never write directly to its schema.

Rotate BFF/admin/provider client secrets through a staged update of Keycloak and the secret manager; restart affected workloads and revoke stale sessions where required. Rotate signing keys with overlap sufficient for unexpired tokens and cached JWKs. Test provider certificate rollover in staging.

Roll back application images using the previous reviewed digest. Flyway migrations are forward-only; do not delete migration history or restore an old application database over current user activity. Keycloak version rollback may require a coordinated database restore; consult the pinned version's upgrade guidance and rehearse it. A backup is not verified until a restore has passed authentication and organization-isolation smoke tests.

Stop only this development stack with `docker compose down`. Volumes persist. Removing volumes destroys the test identities and database; do it only intentionally.
