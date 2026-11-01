import { defineConfig } from "@playwright/test";
// Error-context DOM snapshots can contain recovery codes; disable them as well as traces.
process.env.PLAYWRIGHT_NO_COPY_PROMPT = "1";
export default defineConfig({
  testDir: "./tests",
  timeout: 180000,
  expect: { timeout: 15000 },
  workers: 1,
  retries: 0,
  reporter: "list",
  use: {
    baseURL: "http://localhost:18080",
    headless: true,
    trace: "off",
    screenshot: "off",
    video: "off",
  },
});
