import { defineConfig } from "@playwright/test";
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
