import { test, expect, type Page } from "@playwright/test";
import { randomBytes } from "node:crypto";
import { readFileSync } from "node:fs";
import * as OTPAuth from "otpauth";
import { PNG } from "pngjs";
import jsQR from "jsqr";

const env = Object.fromEntries(
  readFileSync("../.env", "utf8")
    .trim()
    .split("\n")
    .map((line) => line.split("=")),
);
const password = "Test-only-" + randomBytes(16).toString("hex");
async function register(page: Page, phone: string, email?: string) {
  await page.goto("/");
  await page.getByRole("link", { name: "Create an account" }).click();
  await page.locator("#username").fill(phone);
  await page.locator("#firstName").fill("Journey");
  await page.locator("#lastName").fill("Test");
  if (email) await page.locator("#email").fill(email);
  await page.locator("#password").fill(password);
  await page.locator("#password-confirm").fill(password);
  await page.getByRole("button", { name: "Register", exact: true }).click();
}
async function terms(page: Page) {
  if (await page.locator("#kc-accept").isVisible())
    await page.locator("#kc-accept").click();
}
async function verifyPhone(page: Page, phone: string) {
  await expect(
    page.getByRole("heading", { name: "Verify your mobile number" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Send verification code" }).click();
  const response = await page.request.get(
    "http://localhost:8026/messages?phone=" + encodeURIComponent(phone),
    { headers: { Authorization: "Bearer " + env.SMS_TEST_TOKEN } },
  );
  expect(response.ok()).toBeTruthy();
  const { code } = await response.json();
  await page.locator("#code").fill(code);
  await page.getByRole("button", { name: "Verify mobile" }).click();
}
async function enroll(page: Page) {
  await expect(page.locator("#kc-totp-secret-qr-code")).toBeVisible();
  const source = await page
    .locator("#kc-totp-secret-qr-code")
    .getAttribute("src");
  const png = PNG.sync.read(
    Buffer.from(source!.split(",")[1].trim(), "base64"),
  );
  const qr = jsQR(new Uint8ClampedArray(png.data), png.width, png.height);
  expect(qr).not.toBeNull();
  const uri = new URL(qr!.data);
  expect(uri.protocol).toBe("otpauth:");
  expect(uri.hostname).toBe("totp");
  const totp = OTPAuth.URI.parse(qr!.data) as OTPAuth.TOTP;
  expect(totp.digits).toBe(6);
  expect(totp.period).toBe(30);
  expect(totp.algorithm).toBe("SHA1");
  await page.locator("#totp").fill(totp.generate());
  await page.locator("#userLabel").fill("Automated test authenticator");
  await page.locator("#saveTOTPBtn").click();
  return totp;
}
async function recovery(page: Page) {
  await expect(
    page.locator("input[name=generatedRecoveryAuthnCodes]"),
  ).toBeAttached();
  const codes = await page
    .locator("input[name=generatedRecoveryAuthnCodes]")
    .inputValue();
  const check = page.locator("input[type=checkbox]");
  for (let i = 0; i < (await check.count()); i++) await check.nth(i).check();
  await page.locator("input[type=submit],button[type=submit]").first().click();
  return codes.split(",");
}
test("phone registration → verified phone → QR/TOTP → recovery → login → revocation", async ({
  page,
}) => {
  const phone = "+1555" + Date.now().toString().slice(-7);
  await register(page, phone);
  await terms(page);
  await verifyPhone(page, phone);
  expect((await page.request.get("/api/v1/me")).status()).toBe(401);
  const totp = await enroll(page);
  const codes = await recovery(page);
  expect(codes.length).toBeGreaterThan(5);
  await expect(page).toHaveURL(/\/app$/);
  await expect(
    page.getByRole("heading", { name: /Welcome, Journey/ }),
  ).toBeVisible();
  expect((await page.request.get("/api/v1/me")).status()).toBe(200);
  await page.getByRole("link", { name: "Security", exact: true }).click();
  await expect(
    page.getByText("Automated test authenticator", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await page
    .getByRole("link", { name: "Sign in with email or mobile" })
    .click();
  await page.locator("#username").fill(phone);
  await page.locator("#password").fill(password);
  await page.locator("#kc-login").click();
  await expect(page.locator("#otp")).toBeVisible();
  expect((await page.request.get("/api/v1/me")).status()).toBe(401);
  await page
    .locator("#otp")
    .fill(totp.generate() === "000000" ? "000001" : "000000");
  await page.locator("#kc-login").click();
  await expect(page.locator("#otp")).toBeVisible();
  // Advance to an unused interval. This is an explicit interoperability timing boundary.
  await page.waitForTimeout(31000 - (Date.now() % 30000));
  const acceptedCode = totp.generate();
  await page.locator("#otp").fill(acceptedCode);
  await page.locator("#kc-login").click();
  await expect(page).toHaveURL(/\/app$/);
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await page
    .getByRole("link", { name: "Sign in with email or mobile" })
    .click();
  await page.locator("#username").fill(phone);
  await page.locator("#password").fill(password);
  await page.locator("#kc-login").click();
  await page.locator("#otp").fill(acceptedCode);
  await page.locator("#kc-login").click();
  await expect(page.locator("#otp")).toBeVisible();
  await page.getByText("Try another way", { exact: false }).click();
  await page
    .getByText("Recovery authentication code", { exact: false })
    .first()
    .click();
  await page.locator("input[name=recoveryCodeInput]").fill(codes[0]);
  await page.locator("input[type=submit],button[type=submit]").first().click();
  await expect(page).toHaveURL(/\/app$/);
  const csrf = await (await page.request.get("/api/v1/auth/csrf")).json();
  const logout = await page.request.post("/api/v1/auth/logout-all", {
    headers: { [csrf.headerName]: csrf.token },
  });
  expect(logout.status(), await logout.text()).toBe(200);
  expect((await page.request.get("/api/v1/me")).status()).toBe(401);
  await page.goto("/");
  await page
    .getByRole("link", { name: "Sign in with email or mobile" })
    .click();
  await page.locator("#username").fill(phone);
  await page.locator("#password").fill(password);
  await page.locator("#kc-login").click();
  await page.getByText("Try another way", { exact: false }).click();
  await page
    .getByText("Recovery authentication code", { exact: false })
    .first()
    .click();
  await page.locator("input[name=recoveryCodeInput]").fill(codes[0]);
  await page.locator("#kc-login").click();
  await expect(page.locator("input[name=recoveryCodeInput]")).toBeVisible();
  await page.locator("input[name=recoveryCodeInput]").fill(codes[1]);
  await page.locator("#kc-login").click();
  await expect(page).toHaveURL(/\/app$/);
});
test("anonymous APIs and cross-site mutations fail closed", async ({
  request,
}) => {
  expect((await request.get("/api/v1/me")).status()).toBe(401);
  expect((await request.get("/api/v1/security/mfa")).status()).toBe(401);
  expect(
    (
      await request.post("/api/v1/organizations", {
        data: { name: "Injected", slug: "injected" },
      })
    ).status(),
  ).toBe(403);
  expect(
    (
      await request.post("/api/v1/auth/sso/discovery", {
        data: { identifier: "acme" },
      })
    ).status(),
  ).toBe(403);
});

async function verifyEmail(page: Page, email: string) {
  let id = "";
  await expect
    .poll(
      async () => {
        const r = await page.request.get(
          "http://localhost:18025/api/v1/search?query=" +
            encodeURIComponent("to:" + email),
        );
        const data = await r.json();
        id = data.messages?.[0]?.ID || "";
        return id;
      },
      { timeout: 30000 },
    )
    .not.toBe("");
  const message = await (
    await page.request.get("http://localhost:18025/api/v1/message/" + id)
  ).json();
  const link = message.Text.match(
    /http:\/\/localhost:18180\/realms\/network\/login-actions\/action-token[^\s<]*/,
  )?.[0];
  expect(link).toBeTruthy();
  await page.goto(link.replaceAll("&amp;", "&"));
}
test("email registration and verification gate application access", async ({
  page,
}) => {
  const email = "email-" + Date.now() + "@example.test";
  await register(page, email, email);
  await terms(page);
  expect((await page.request.get("/api/v1/me")).status()).toBe(401);
  await verifyEmail(page, email);
  await enroll(page);
  await recovery(page);
  await expect(page).toHaveURL(/\/app$/);
  const me = await (await page.request.get("/api/v1/me")).json();
  expect(me.email).toBe(email);
  const csrf = await (await page.request.get("/api/v1/auth/csrf")).json();
  expect(
    (
      await page.request.post("/api/v1/organizations", {
        headers: { [csrf.headerName]: csrf.token },
        data: { name: "Journey Company", slug: "journey-" + Date.now() },
      })
    ).ok(),
  ).toBeTruthy();
  expect(
    (
      await page.request.get(
        "/api/v1/organizations/c67a22a3-7c08-46b0-ad24-cab3e1eae852",
      )
    ).status(),
  ).toBe(403);
});
test("company OIDC broker, local MFA fallback and tenant-scoped JIT", async ({
  page,
}) => {
  const username = "company-" + Date.now(),
    email = username + "@example.test";
  const tokenResponse = await page.request.post(
    "http://localhost:18180/realms/master/protocol/openid-connect/token",
    {
      form: {
        grant_type: "password",
        client_id: "admin-cli",
        username: "bootstrap",
        password: env.KEYCLOAK_BOOTSTRAP_PASSWORD,
      },
    },
  );
  const { access_token } = await tokenResponse.json();
  const created = await page.request.post(
    "http://localhost:18180/admin/realms/company/users",
    {
      headers: { Authorization: "Bearer " + access_token },
      data: {
        username,
        email,
        firstName: "Company",
        lastName: "Tester",
        enabled: true,
        emailVerified: true,
        credentials: [{ type: "password", value: password, temporary: false }],
      },
    },
  );
  expect(created.status()).toBe(201);
  await page.goto("/");
  await page.locator("#company").fill("acme");
  await page.getByRole("button", { name: "Continue with Company SSO" }).click();
  await expect(page).toHaveURL(/\/realms\/company\//);
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);
  await page.locator("#kc-login").click();
  await terms(page);
  // Upstream email is not automatically trusted by the broker.
  if (!(await page.locator("#kc-totp-secret-qr-code").isVisible()))
    await verifyEmail(page, email);
  await enroll(page);
  await recovery(page);
  await expect(page).toHaveURL(/\/app$/);
  const me = await (await page.request.get("/api/v1/me")).json();
  expect(
    me.organizations.some(
      (o: { slug: string; role: string }) =>
        o.slug === "acme" && o.role === "MEMBER",
    ),
  ).toBeTruthy();
  expect(
    (
      await page.request.get(
        "/api/v1/organizations/c67a22a3-7c08-46b0-ad24-cab3e1eae852",
      )
    ).status(),
  ).toBe(200);
  expect(
    (
      await page.request.get(
        "/api/v1/organizations/c67a22a3-7c08-46b0-ad24-cab3e1eae852/security-policy",
      )
    ).status(),
  ).toBe(403);
  expect(
    (
      await page.request.get(
        "/api/v1/organizations/00000000-0000-0000-0000-000000000000",
      )
    ).status(),
  ).toBe(403);
});
test("OIDC starts with PKCE and rejects unsolicited callback state", async ({
  page,
}) => {
  await page.goto("/");
  await page
    .getByRole("link", { name: "Sign in with email or mobile" })
    .click();
  const url = new URL(page.url());
  expect(url.searchParams.get("code_challenge_method")).toBe("S256");
  expect(url.searchParams.get("code_challenge")).toBeTruthy();
  expect(url.searchParams.get("nonce")).toBeTruthy();
  await page.goto("/login/oauth2/code/keycloak?code=invalid&state=attacker");
  await expect(page).toHaveURL(/error=authentication_failed/);
  expect((await page.request.get("/api/v1/me")).status()).toBe(401);
});
