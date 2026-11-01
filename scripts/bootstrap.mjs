// Provision a fresh environment using supported Keycloak Admin REST APIs. Never edits its DB.
const internal=process.env.IDP_INTERNAL||'http://localhost:18180';
const publicUrl=process.env.IDP_PUBLIC||'http://localhost:18180';
const app=process.env.APP_ORIGIN||'http://localhost:18080';
const required=k=>{if(!process.env[k])throw new Error(`${k} required`);return process.env[k];};
const production=process.env.PLATFORM_ENV==='production';
if(production && (!app.startsWith('https://')||!publicUrl.startsWith('https://')))throw new Error('Production public origins must use HTTPS');
const tokenResponse=await fetch(`${internal}/realms/master/protocol/openid-connect/token`,{method:'POST',body:new URLSearchParams({client_id:'admin-cli',grant_type:'password',username:'bootstrap',password:required('KEYCLOAK_BOOTSTRAP_PASSWORD')})});
if(!tokenResponse.ok)throw new Error('Bootstrap administrator authentication failed');
const {access_token:token}=await tokenResponse.json();
async function api(path,method='GET',body) {
 const response=await fetch(`${internal}/admin${path}`,{method,headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});
 if(!response.ok)throw new Error(`Provisioning ${method} ${path} failed (${response.status}): ${(await response.text()).slice(0,500)}`);
 return response.status===204||response.headers.get('content-length')==='0'?null:response.json().catch(()=>null);
}
const execution=(authenticator,requirement='REQUIRED',priority=10)=>({authenticator,requirement,priority,authenticatorFlow:false,userSetupAllowed:true});
const subflow=(flowAlias,requirement='REQUIRED',priority=10)=>({flowAlias,requirement,priority,authenticatorFlow:true,userSetupAllowed:false});
const flow=(alias,topLevel,authenticationExecutions,providerId='basic-flow')=>({alias,topLevel,builtIn:false,providerId,authenticationExecutions});
const sessionMapper=(name,note,claim)=>({name,protocol:'openid-connect',protocolMapper:'oidc-usersessionmodel-note-mapper',config:{'user.session.note':note,'claim.name':claim,'jsonType.label':'String','id.token.claim':'true','access.token.claim':'true','userinfo.token.claim':'true'}});
const attributeMapper=(name,attribute,claim)=>({name,protocol:'openid-connect',protocolMapper:'oidc-usermodel-attribute-mapper',config:{'user.attribute':attribute,'claim.name':claim,'jsonType.label':'String','id.token.claim':'true','access.token.claim':'true','userinfo.token.claim':'true'}});
const known=await api('/realms');
if(!production && !known.some(r=>r.realm==='company')) await api('/realms','POST',{
 realm:'company',enabled:true,displayName:'Acme Company Test Identity',sslRequired:'none',registrationAllowed:false,
 clients:[{clientId:'network-broker',secret:required('TEST_BROKER_SECRET'),enabled:true,protocol:'openid-connect',publicClient:false,standardFlowEnabled:true,directAccessGrantsEnabled:false,redirectUris:[`${publicUrl}/realms/network/broker/acme/endpoint`],attributes:{'pkce.code.challenge.method':'S256'}}]
});
const existing=known.some(r=>r.realm==='network')?await api('/realms/network'):null;
if(existing?.attributes?.['network.bootstrap.complete']==='true') {console.log('Network realm already provisioned; preserving identity data and operator configuration.');process.exit(0);}
const broker=(alias,config)=>({alias,providerId:'oidc',enabled:true,trustEmail:false,storeToken:false,linkOnly:false,firstBrokerLoginFlowAlias:'first broker login',postBrokerLoginFlowAlias:'network-post-broker',config:{syncMode:'IMPORT',validateSignature:'true',useJwksUrl:'true',pkceEnabled:'true',pkceMethod:'S256',defaultScope:'openid profile email',...config}});
const providers=production?[]:[broker('acme',{clientId:'network-broker',clientSecret:required('TEST_BROKER_SECRET'),issuer:`${publicUrl}/realms/company`,authorizationUrl:`${publicUrl}/realms/company/protocol/openid-connect/auth`,tokenUrl:`${internal}/realms/company/protocol/openid-connect/token`,userInfoUrl:`${internal}/realms/company/protocol/openid-connect/userinfo`,jwksUrl:`${internal}/realms/company/protocol/openid-connect/certs`})];
if(process.env.GOOGLE_CLIENT_ID && process.env.GOOGLE_CLIENT_SECRET) providers.push(broker('google',{clientId:process.env.GOOGLE_CLIENT_ID,clientSecret:process.env.GOOGLE_CLIENT_SECRET,issuer:'https://accounts.google.com',authorizationUrl:'https://accounts.google.com/o/oauth2/v2/auth',tokenUrl:'https://oauth2.googleapis.com/token',userInfoUrl:'https://openidconnect.googleapis.com/v1/userinfo',jwksUrl:'https://www.googleapis.com/oauth2/v3/certs'}));
if(process.env.MICROSOFT_CLIENT_ID && process.env.MICROSOFT_CLIENT_SECRET) {
 const tenant=required('MICROSOFT_TENANT_ID');
 if(!/^[a-f0-9-]{36}$/i.test(tenant))throw new Error('Use a pinned Microsoft tenant UUID, never common or organizations');
 providers.push(broker('microsoft',{clientId:process.env.MICROSOFT_CLIENT_ID,clientSecret:process.env.MICROSOFT_CLIENT_SECRET,issuer:`https://login.microsoftonline.com/${tenant}/v2.0`,authorizationUrl:`https://login.microsoftonline.com/${tenant}/oauth2/v2.0/authorize`,tokenUrl:`https://login.microsoftonline.com/${tenant}/oauth2/v2.0/token`,userInfoUrl:'https://graph.microsoft.com/oidc/userinfo',jwksUrl:`https://login.microsoftonline.com/${tenant}/discovery/v2.0/keys`}));
}
if(!existing) await api('/realms','POST',{
 realm:'network',displayName:'Network',enabled:true,sslRequired:production?'external':'none',loginTheme:'network',
 registrationAllowed:true,registrationEmailAsUsername:false,loginWithEmailAllowed:true,duplicateEmailsAllowed:false,editUsernameAllowed:false,resetPasswordAllowed:true,rememberMe:false,verifyEmail:false,
 bruteForceProtected:true,permanentLockout:false,failureFactor:5,waitIncrementSeconds:60,maxFailureWaitSeconds:900,maxDeltaTimeSeconds:43200,
 accessTokenLifespan:300,ssoSessionIdleTimeout:900,ssoSessionMaxLifespan:28800,accessCodeLifespan:60,accessCodeLifespanLogin:300,accessCodeLifespanUserAction:300,
 revokeRefreshToken:true,refreshTokenMaxReuse:0,
 passwordPolicy:'length(14) and notUsername(undefined) and notEmail(undefined) and passwordHistory(5)',
 otpPolicyType:'totp',otpPolicyAlgorithm:'HmacSHA1',otpPolicyDigits:6,otpPolicyPeriod:30,otpPolicyLookAheadWindow:1,otpPolicyCodeReusable:false,
 eventsEnabled:true,eventsExpiration:2592000,adminEventsEnabled:true,adminEventsDetailsEnabled:false,eventsListeners:['jboss-logging','email'],
 smtpServer:production?{host:required('SMTP_HOST'),port:process.env.SMTP_PORT||'587',from:required('SMTP_FROM'),fromDisplayName:'Network Security',starttls:'true',auth:'true',user:required('SMTP_USER'),password:required('SMTP_PASSWORD')}:{host:'mailpit',port:'1025',from:'security@network.test',fromDisplayName:'Network Security'},
 browserSecurityHeaders:{contentSecurityPolicy:"frame-src 'self'; frame-ancestors 'self'; object-src 'none'; base-uri 'self';",xContentTypeOptions:'nosniff',xFrameOptions:'SAMEORIGIN',referrerPolicy:'no-referrer',strictTransportSecurity:'max-age=31536000; includeSubDomains'},
 requiredActions:[
  {alias:'VERIFY_CONTACT',providerId:'VERIFY_CONTACT',name:'Verify contact',enabled:true,defaultAction:true,priority:40},
  {alias:'VERIFY_EMAIL',providerId:'VERIFY_EMAIL',name:'Verify email',enabled:true,defaultAction:false,priority:50},
  {alias:'CONFIGURE_TOTP',providerId:'CONFIGURE_TOTP',name:'Configure authenticator',enabled:true,defaultAction:true,priority:60,config:{'max_auth_age':'0','add-recovery-codes':'true'}},
  {alias:'CONFIGURE_RECOVERY_AUTHN_CODES',providerId:'CONFIGURE_RECOVERY_AUTHN_CODES',name:'Save recovery codes',enabled:true,defaultAction:true,priority:70,config:{'max_auth_age':'0'}},
  {alias:'TERMS_AND_CONDITIONS',providerId:'TERMS_AND_CONDITIONS',name:'Accept Terms and Privacy',enabled:true,defaultAction:true,priority:10},
  {alias:'UPDATE_PROFILE',providerId:'UPDATE_PROFILE',name:'Update profile',enabled:true,defaultAction:false,priority:30},
  {alias:'UPDATE_PASSWORD',providerId:'UPDATE_PASSWORD',name:'Update password',enabled:true,defaultAction:false,priority:20,config:{'max_auth_age':'0'}}
 ],
 authenticationFlows:[
  flow('network-browser',true,[execution('auth-cookie','ALTERNATIVE',10),execution('identity-provider-redirector','ALTERNATIVE',20),subflow('network-forms','ALTERNATIVE',30)]),
  flow('network-registration',true,[{...subflow('network-registration-form','REQUIRED',10),authenticator:'registration-page-form'}]),
  flow('network-registration-form',false,[execution('network-registration-guard','REQUIRED',5),execution('registration-user-creation','REQUIRED',10),execution('registration-password-action','REQUIRED',30)],'form-flow'),
  flow('network-forms',false,[execution('auth-username-password-form','REQUIRED',10),subflow('network-mfa','REQUIRED',20),execution('platform-assurance-stamp','REQUIRED',30)]),
  flow('network-mfa',false,[execution('network-local-otp','ALTERNATIVE',10),execution('auth-recovery-authn-code-form','ALTERNATIVE',20)]),
  flow('network-post-broker',true,[subflow('network-broker-mfa','REQUIRED',10),execution('platform-assurance-stamp','REQUIRED',20)]),
  flow('network-broker-mfa',false,[execution('network-broker-otp','ALTERNATIVE',10),execution('auth-recovery-authn-code-form','ALTERNATIVE',20)])
 ],
 browserFlow:'network-browser',registrationFlow:'network-registration',
 clients:[
  {clientId:'network-bff',name:'Network',secret:required('OIDC_CLIENT_SECRET'),enabled:true,protocol:'openid-connect',publicClient:false,standardFlowEnabled:true,directAccessGrantsEnabled:false,serviceAccountsEnabled:false,redirectUris:[`${app}/login/oauth2/code/keycloak`],webOrigins:[],fullScopeAllowed:false,
   attributes:{'pkce.code.challenge.method':'S256','post.logout.redirect.uris':app,'backchannel.logout.session.required':'true'},
   defaultClientScopes:['profile','email'],optionalClientScopes:[],protocolMappers:[sessionMapper('Authentication assurance','platform_mfa','platform_mfa'),sessionMapper('Broker alias','identity_provider','idp'),attributeMapper('Verified phone','verified_phone','phone_number'),attributeMapper('Phone verification','phone_verified','phone_verified')]},
  {clientId:'network-admin',secret:required('IDP_ADMIN_CLIENT_SECRET'),enabled:true,protocol:'openid-connect',publicClient:false,standardFlowEnabled:false,directAccessGrantsEnabled:false,serviceAccountsEnabled:true,fullScopeAllowed:false}
 ],identityProviders:providers
});
await api('/realms/network/users/profile','PUT',{
 attributes:[
  {name:'username',displayName:'${username}',validations:{length:{min:3,max:254}},permissions:{view:['admin','user'],edit:['admin','user']},required:{roles:['user']}},
  {name:'email',displayName:'${email}',validations:{email:{},length:{max:254}},permissions:{view:['admin','user'],edit:['admin','user']}},
  ...['firstName','lastName'].map(name=>({name,displayName:`\u0024{${name}}`,validations:{length:{min:1,max:100}},required:{roles:['user']},permissions:{view:['admin','user'],edit:['admin','user']}})),
  {name:'phone',displayName:'Mobile number (optional, +country code)',validations:{pattern:{pattern:'^\\+[1-9][0-9]{7,14}$','error-message':'Use international format, for example +14155550123'}},permissions:{view:['admin','user'],edit:['admin','user']}},
  ...['verified_phone','phone_verified'].map(name=>({name,permissions:{view:['admin'],edit:['admin']}}))
 ]
});
const clients=await api('/realms/network/clients');
// Security mutations are exposed through recent-authentication AIA and the scoped BFF only.
for(const account of clients.filter(c=>['account','account-console'].includes(c.clientId)))await api(`/realms/network/clients/${account.id}`,'PUT',{...account,enabled:false});
const admin=clients.find(c=>c.clientId==='network-admin'),realmMgmt=clients.find(c=>c.clientId==='realm-management');
const service=await api(`/realms/network/clients/${admin.id}/service-account-user`);
const roles=await api(`/realms/network/clients/${realmMgmt.id}/roles`);
const assigned=roles.filter(r=>['view-users','manage-users'].includes(r.name));
await api(`/realms/network/users/${service.id}/role-mappings/clients/${realmMgmt.id}`,'POST',assigned);
await api(`/realms/network/clients/${admin.id}/scope-mappings/clients/${realmMgmt.id}`,'POST',assigned);
const configured=await api('/realms/network');
await api('/realms/network','PUT',{attributes:{...configured.attributes,'network.bootstrap.complete':'true'}});
console.log('Provisioned Network and isolated Company test realms. No test passwords were created.');
