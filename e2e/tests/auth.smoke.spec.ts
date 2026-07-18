import { test, expect } from '@playwright/test';

// All tests in this file start unauthenticated — we are testing the auth flows themselves.
// The file-level override clears any project-level storageState (e.g. user-a.json).
test.use({ storageState: { cookies: [], origins: [] } });

test('register new user and redirects to shelf', async ({ page }) => {
  await page.goto('/register');
  await page.getByLabel('Display Name').fill('E2E Reg User');
  await page.getByLabel('Email').fill(`reg-${Date.now()}@example.com`);
  await page.getByLabel('Password').fill('securepassword');
  await page.getByRole('button', { name: 'Create Account' }).click();
  await expect(page).toHaveURL('/shelf');
  await expect(page.getByRole('heading', { name: 'My Shelf' })).toBeVisible();
});

test('login with valid credentials redirects to shelf', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill('e2e-user-a@example.com');
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Sign In' }).click();
  await expect(page).toHaveURL('/shelf');
});

test('login with invalid credentials shows error', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill('nobody@nowhere.example.com');
  await page.getByLabel('Password').fill('wrongpassword');
  await page.getByRole('button', { name: 'Sign In' }).click();
  await expect(page.getByText('Invalid email or password.')).toBeVisible();
  await expect(page).toHaveURL('/login');
});

test('logout clears token and redirects to login', async ({ page }) => {
  // File-level storageState is unauthenticated, so we log in manually first.
  await page.goto('/login');
  await page.getByLabel('Email').fill('e2e-user-a@example.com');
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Sign In' }).click();
  await expect(page).toHaveURL('/shelf');

  // Navigate to Profile and trigger logout.
  await page.goto('/profile');
  await page.getByTestId('logout-button').click();

  // Assert redirect to /login and token cleared from localStorage.
  await expect(page).toHaveURL('/login');
  const token = await page.evaluate(() => localStorage.getItem('booktracker_token'));
  expect(token).toBeNull();
});
