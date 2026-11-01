import { randomBytes } from 'node:crypto';
import { writeFileSync, existsSync } from 'node:fs';
if (existsSync('.env')) throw new Error('.env already exists; refusing to overwrite credentials');
const keys=['DATABASE_PASSWORD','REDIS_PASSWORD','KEYCLOAK_BOOTSTRAP_PASSWORD','OIDC_CLIENT_SECRET','IDP_ADMIN_CLIENT_SECRET','SMS_TEST_TOKEN','TEST_BROKER_SECRET'];
writeFileSync('.env',keys.map(k=>`${k}=${randomBytes(32).toString('hex')}`).join('\n')+'\n',{mode:0o600,flag:'wx'});
console.log('Created local .env with independently generated credentials. Values are not printed.');
