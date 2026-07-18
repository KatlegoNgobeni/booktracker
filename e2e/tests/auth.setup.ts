import { test as setup, expect } from '@playwright/test';
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const AUTH_FILE_A = path.join(__dirname, '../playwright/.auth/user-a.json');
const AUTH_FILE_B = path.join(__dirname, '../playwright/.auth/user-b.json');

// Ensure the auth directory exists before writing storageState files
fs.mkdirSync(path.dirname(AUTH_FILE_A), { recursive: true });

setup('authenticate as User A', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill('e2e-user-a@example.com');
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Sign In' }).click();
  await expect(page).toHaveURL('/shelf');
  await page.context().storageState({ path: AUTH_FILE_A });
});

setup('authenticate as User B', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill('e2e-user-b@example.com');
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Sign In' }).click();
  await expect(page).toHaveURL('/shelf');
  await page.context().storageState({ path: AUTH_FILE_B });
});
