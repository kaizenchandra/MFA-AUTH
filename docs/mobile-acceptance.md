# Real-device acceptance: not yet executed

Use two dedicated test identities and current versions of the real mobile applications. Do not capture screenshots containing QR codes, manual secrets, recovery codes or live OTPs in general-purpose bug reports.

For each Microsoft Authenticator and Google Authenticator:
1. Register and verify an email or international mobile number.
2. Reach mandatory setup, or open Settings → Security → Enable / replace authenticator.
3. Confirm fresh authentication is required for an existing account.
4. Microsoft: Add Account (+) → Other Account → scan. Google: Add a Code (+) → Scan a QR Code.
5. Confirm the displayed account/issuer is correct and a six-digit code rotates every 30 seconds.
6. Enter a code beginning with zero when available; paste must work without losing the zero.
7. Confirm enrollment completes only after valid proof and recovery codes are acknowledged.
8. Sign out; sign in again and prove the app is inaccessible before the second factor.
9. Try an incorrect code, an old code and the same accepted code again. Validate rejection and eventual throttling.
10. Confirm automatic clock synchronization resolves a deliberately skewed device clock.
11. Test the manual-key fallback with a separate disposable enrollment.
12. Save recovery codes securely, recover with the next code, reject its reuse and rotate the remaining set.
13. Enroll a replacement device, remove the original, revoke another session and verify loss of access.
14. Reset the password and confirm MFA remains required.
15. Test cancellation, refresh, back navigation, five-minute enrollment expiry, duplicate tabs, mobile layout, keyboard focus, and screen-reader labels.

Record application/version, OS/version, UTC date, tester, identity service version and outcome for each step. Automated QR decoding and TOTP generation prove standards compatibility, not camera scanning, mobile application behavior or user acceptance.
