// Operator-only federation onboarding. Run from repository root with --env-file=.env.
import {readFileSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {resolveTxt} from 'node:dns/promises';
const file=process.argv[2];if(!file)throw new Error('Usage: node --env-file=.env scripts/onboard-provider.mjs provider.json');
const spec=JSON.parse(readFileSync(file,'utf8'));
if(!/^[0-9a-f-]{36}$/i.test(spec.organizationId)||!/^org-[a-z0-9-]{3,60}$/.test(spec.alias))throw new Error('Use an organization UUID and a unique org-* alias');
const https=value=>{const u=new URL(value);if(u.protocol!=='https:'||u.username||u.password||u.hash)throw new Error('Federation endpoints must use HTTPS without URL credentials');return u.href;};
https(spec.issuer);
let config;
if(spec.protocol==='OIDC'){
 for(const name of ['authorizationUrl','tokenUrl','jwksUrl'])https(spec[name]);
 if(spec.userInfoUrl)https(spec.userInfoUrl);
 const secret=process.env[spec.clientSecretEnv];if(!secret)throw new Error('Set the client secret environment variable named in the specification');
 config={issuer:spec.issuer,authorizationUrl:spec.authorizationUrl,tokenUrl:spec.tokenUrl,jwksUrl:spec.jwksUrl,clientId:spec.clientId,clientSecret:secret,validateSignature:'true',useJwksUrl:'true',pkceEnabled:'true',pkceMethod:'S256',defaultScope:'openid profile email',syncMode:'IMPORT',...(spec.userInfoUrl?{userInfoUrl:spec.userInfoUrl}:{})};
 if(spec.trustedAcr){config['network.trusted.acr']=spec.trustedAcr;if(spec.tenantId)config['network.tenant.id']=spec.tenantId;}
}else if(spec.protocol==='SAML'){
 https(spec.singleSignOnServiceUrl);
 if(!spec.signingCertificate||!spec.entityId)throw new Error('Pinned SAML entity ID and signing certificate required');
 config={singleSignOnServiceUrl:spec.singleSignOnServiceUrl,idpEntityId:spec.entityId,entityId:(process.env.IDP_PUBLIC||'http://localhost:18180')+'/realms/network',validateSignature:'true',wantAssertionsSigned:'true',wantAuthnRequestsSigned:'true',postBindingResponse:'true',postBindingAuthnRequest:'true',signingCertificate:spec.signingCertificate,nameIDPolicyFormat:'urn:oasis:names:tc:SAML:2.0:nameid-format:persistent',syncMode:'IMPORT'};
}else throw new Error('Protocol must be OIDC or SAML');
// Administrative users supply a short-lived admin access token; never write it to files or output.
const token=process.env.KEYCLOAK_OPERATOR_TOKEN;if(!token)throw new Error('KEYCLOAK_OPERATOR_TOKEN required');
const base=process.env.IDP_INTERNAL||'http://localhost:18180';
const body={alias:spec.alias,displayName:spec.displayName||spec.alias,providerId:spec.protocol.toLowerCase(),enabled:true,trustEmail:false,storeToken:false,firstBrokerLoginFlowAlias:'first broker login',postBrokerLoginFlowAlias:'network-post-broker',config};
const response=await fetch(base+'/admin/realms/network/identity-provider/instances',{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:JSON.stringify(body)});
if(!response.ok)throw new Error('Provider creation failed: '+response.status+' (existing aliases are never overwritten)');
const quote=value=>"'"+String(value).replaceAll("'","''")+"'";
let sql='BEGIN; INSERT INTO organization_identity_provider(organization_id,alias,protocol,issuer,tenant_id,enabled,approved) VALUES ('+[spec.organizationId,spec.alias,spec.protocol,spec.issuer].map(quote).join(',')+','+(spec.tenantId?quote(spec.tenantId):'NULL')+',true,true);';
if(spec.domain){const domain=spec.domain.toLowerCase();if(!/^[a-z0-9.-]+$/.test(domain))throw new Error('Invalid domain');const records=await resolveTxt('_network-verification.'+domain);if(!records.some(r=>r.join('')==='network-org='+spec.organizationId))throw new Error('Domain verification TXT does not match');sql+='INSERT INTO organization_domain(domain,organization_id,verified_at) VALUES ('+quote(domain)+','+quote(spec.organizationId)+',now());';}
sql+='COMMIT;';
execFileSync('docker',['compose','exec','-T','postgres','psql','-U','platform','-d','platform','-v','ON_ERROR_STOP=1','-c',sql],{stdio:['ignore','ignore','pipe']});
console.log('Provider provisioned and approved. Configure organization JIT and MFA policy in the application.');
