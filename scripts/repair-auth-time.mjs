// Explicit, additive upgrade for realms provisioned before the basic OIDC scope was enabled.
const base=process.env.IDP_INTERNAL||'http://localhost:18180';
const response=await fetch(base+'/realms/master/protocol/openid-connect/token',{method:'POST',body:new URLSearchParams({grant_type:'password',client_id:'admin-cli',username:'bootstrap',password:process.env.KEYCLOAK_BOOTSTRAP_PASSWORD})});
if(!response.ok)throw new Error('Operator authentication failed');
const {access_token}=await response.json();
const headers={Authorization:'Bearer '+access_token,'Content-Type':'application/json'};
const clients=await (await fetch(base+'/admin/realms/network/clients?clientId=network-bff',{headers})).json();
const scopes=await (await fetch(base+'/admin/realms/network/client-scopes',{headers})).json();
const basic=scopes.find(s=>s.name==='basic');
if(!basic)throw new Error('Native basic scope missing');
const result=await fetch(base+'/admin/realms/network/clients/'+clients[0].id+'/default-client-scopes/'+basic.id,{method:'PUT',headers});
if(!result.ok)throw new Error('Scope update failed '+result.status);
console.log('Enabled native basic claims for the BFF client. Reauthenticate existing sessions.');
