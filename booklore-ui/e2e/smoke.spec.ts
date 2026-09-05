import { test, expect } from './fixtures';

const VALID_PASSWORD = 'adminPassword123!';

test.describe('Booklore smoke suite', () => {
  test.beforeEach(async ({ page, mockBaseApi }) => {
    await mockBaseApi(page, { setupComplete: true });
  });

  test('redirects to login when unauthenticated and setup is complete', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.locator('.login-title')).toContainText('Welcome Back');
  });

  test('shows error on invalid credentials', async ({ page }) => {
    await page.goto('/login');
    await page.route('http://localhost:6060/api/v1/auth/login', async route => {
      await route.fulfill({ status: 401 });
    });

    await page.fill('#username', 'admin');
    await page.locator('p-password input[type="password"]').fill('wrong');
    await page.click('button[type="submit"]');

    await expect(page.locator('.error-message')).toBeVisible();
  });

  test('logs in and lands on dashboard', async ({ page, login, seedUser, seedLibrary }) => {
    await seedUser(page, { username: 'admin' });
    await seedLibrary(page, { id: 1, name: 'Test Library', folderPath: '/books' });

    await login(page, 'admin', VALID_PASSWORD);
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.locator('.dashboard-container')).toBeVisible();
  });

  test('navigates to all-books after login', async ({ page, login, seedUser, seedLibrary, seedBooks }) => {
    await seedUser(page, { username: 'admin' });
    await seedLibrary(page, { id: 1, name: 'Test Library', folderPath: '/books' });
    await seedBooks(page, [
      { id: 1, title: 'Smoke Test Book', authors: ['Author One'], readStatus: 'UNREAD' },
    ]);

    await login(page, 'admin', VALID_PASSWORD);
    await expect(page).toHaveURL(/\/dashboard$/);
    await page.goto('/all-books');
    await expect(page.locator('app-book-browser')).toBeVisible({ timeout: 15000 });
  });
});

test.describe('Setup flow', () => {
  test('first-time setup page is reachable when setup is incomplete', async ({ page, mockBaseApi, setupAccount }) => {
    await mockBaseApi(page, { setupComplete: false });

    await page.goto('/');
    await expect(page).toHaveURL(/\/setup$/);
    await expect(page.locator('.setup-title')).toBeVisible();

    await setupAccount(page, 'admin', VALID_PASSWORD);
    await expect(page.locator('.success-message')).toBeVisible();
  });
});
