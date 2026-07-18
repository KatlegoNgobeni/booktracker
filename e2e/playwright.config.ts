import { defineConfig, devices } from '@playwright/test';
import { fileURLToPath } from 'url';
import path from 'path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'html',
  timeout: 60_000,

  use: {
    baseURL: 'http://localhost:8080',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },

  globalSetup: path.resolve(__dirname, './global-setup.ts'),
  globalTeardown: path.resolve(__dirname, './global-teardown.ts'),

  projects: [
    {
      name: 'setup',
      testMatch: /auth\.setup\.ts/,
    },
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
      dependencies: ['setup'],
    },
  ],

  webServer: [
    {
      command: 'node mock-server.mjs',
      url: 'http://localhost:9999',
      timeout: 10_000,
      reuseExistingServer: !process.env.CI,
    },
    {
      command: [
        'java',
        `-DSPRING_DATASOURCE_URL=${process.env.E2E_DB_URL ?? 'jdbc:postgresql://localhost:5432/booktracker_e2e'}`,
        `-DSPRING_DATASOURCE_USERNAME=${process.env.E2E_DB_USER ?? 'bt_e2e'}`,
        `-DSPRING_DATASOURCE_PASSWORD=${process.env.E2E_DB_PASS ?? 'bt_e2e_pw'}`,
        `-DJWT_SECRET=${process.env.E2E_JWT_SECRET ?? 'dGVzdC1qd3Qtc2lnbmluZy1zZWNyZXQtZm9yLXVuaXQtdGVzdHMtb25seQ=='}`,
        '-Dopenlibrary.base-url=http://localhost:9999',
        '-Dopenlibrary.validate-base-url=false',
        '-jar', '../target/booktracker-0.0.1-SNAPSHOT.jar',
      ].join(' '),
      url: 'http://localhost:8080/api/health',
      timeout: 120_000,
      reuseExistingServer: !process.env.CI,
      stdout: 'pipe',
      stderr: 'pipe',
    },
  ],
});
