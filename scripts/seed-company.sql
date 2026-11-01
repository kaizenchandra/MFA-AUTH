-- Development fixture; run after Flyway migration. Does not create privileged users.
INSERT INTO organization(id,slug,name) VALUES ('c67a22a3-7c08-46b0-ad24-cab3e1eae852','acme','Acme Test Company') ON CONFLICT DO NOTHING;
INSERT INTO organization_security_policy(organization_id,sso_only,mfa_mode,jit_enabled) VALUES ('c67a22a3-7c08-46b0-ad24-cab3e1eae852',true,'MANDATORY',true) ON CONFLICT DO NOTHING;
INSERT INTO organization_identity_provider(organization_id,alias,protocol,issuer,enabled,approved) VALUES ('c67a22a3-7c08-46b0-ad24-cab3e1eae852','acme','OIDC','http://localhost:18180/realms/company',true,true) ON CONFLICT DO NOTHING;
