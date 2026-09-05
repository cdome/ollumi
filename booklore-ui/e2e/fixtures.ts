import { test as base, expect, Page } from '@playwright/test';

export interface BookloreTestFixtures {
  /** Set up default API mocks for public settings, setup status, websocket, and icons. */
  mockBaseApi: (page: Page, options?: { setupComplete?: boolean }) => Promise<void>;
  /** Fill the local-login form and submit. Assumes you are on /login. */
  login: (page: Page, username: string, password: string) => Promise<void>;
  /** Create the first admin account. Assumes you are on /setup. */
  setupAccount: (page: Page, username: string, password: string) => Promise<void>;
  /** Return a syntactically valid JWT-like access token for the given username. */
  createAccessToken: (username: string) => string;
  /** Mock the current user returned by /api/v1/users/me. */
  seedUser: (page: Page, user?: Record<string, unknown>) => Promise<void>;
  /** Mock the library list returned by /api/v1/libraries. */
  seedLibrary: (page: Page, library?: { id: number; name: string; folderPath: string }) => Promise<void>;
  /** Mock the shelf list returned by /api/v1/shelves. */
  seedShelves: (page: Page, shelves?: Array<Record<string, unknown>>) => Promise<void>;
  /** Mock the paginated book list returned by /api/v1/books. */
  seedBooks: (page: Page, books?: Array<Record<string, unknown>>) => Promise<void>;
}

const API_BASE = 'http://localhost:6060/api/v1';

function defaultPublicSettings(): Record<string, unknown> {
  return {
    oidcEnabled: false,
    remoteAuthEnabled: false,
    oidcForceOnlyMode: false,
    oidcProviderDetails: null,
  };
}

function defaultUserSettings(): Record<string, unknown> {
  return {
    perBookSetting: { pdf: '', epub: '', cbx: '' },
    pdfReaderSetting: {},
    epubReaderSetting: {},
    ebookReaderSetting: {},
    cbxReaderSetting: {},
    newPdfReaderSetting: {},
    sidebarLibrarySorting: { field: 'name', order: 'asc' },
    sidebarShelfSorting: { field: 'name', order: 'asc' },
    sidebarMagicShelfSorting: { field: 'name', order: 'asc' },
    filterMode: 'and',
    visibleFilters: ['AUTHOR', 'GENRE', 'SERIES', 'READ_STATUS', 'FORMAT', 'LANGUAGE'],
    visibleSortFields: ['title', 'author', 'addedOn'],
    metadataCenterViewMode: 'route',
    enableSeriesView: false,
    entityViewPreferences: {
      global: {
        sortKey: 'addedOn',
        sortDir: 'DESC',
        view: 'GRID',
        coverSize: 1.0,
        seriesCollapsed: false,
        overlayBookType: false,
      },
      overrides: [],
    },
    koReaderEnabled: false,
    autoSaveMetadata: true,
  };
}

function defaultUser(): Record<string, unknown> {
  return {
    id: 1,
    username: 'admin',
    name: 'Test User',
    email: 'admin@example.com',
    assignedLibraries: [],
    permissions: {
      admin: true,
      canUpload: true,
      canDownload: true,
      canEmailBook: true,
      canDeleteBook: true,
      canEditMetadata: true,
      canManageLibrary: true,
      canManageMetadataConfig: true,
      canSyncKoReader: true,
      canSyncKobo: true,
      canAccessOpds: true,
      canAccessBookdrop: true,
      canAccessLibraryStats: true,
      canAccessUserStats: true,
      canAccessTaskManager: true,
      canManageEmailConfig: true,
      canManageGlobalPreferences: true,
      canManageIcons: true,
      canManageFonts: true,
      demoUser: false,
      canBulkAutoFetchMetadata: true,
      canBulkCustomFetchMetadata: true,
      canBulkEditMetadata: true,
      canBulkRegenerateCover: true,
      canMoveOrganizeFiles: true,
      canBulkLockUnlockMetadata: true,
      canBulkResetBookloreReadProgress: true,
      canBulkResetKoReaderReadProgress: true,
      canBulkResetBookReadStatus: true,
    },
    userSettings: defaultUserSettings(),
  };
}

function defaultLibrary(): { id: number; name: string; folderPath: string } {
  return { id: 1, name: 'Test Library', folderPath: '/books' };
}

function base64Url(value: string): string {
  return Buffer.from(value)
    .toString('base64url')
    .replace(/=+$/, '');
}

export const test = base.extend<BookloreTestFixtures>({
  createAccessToken: async ({}, use) => {
    await use((username: string) => {
      const header = base64Url(JSON.stringify({ alg: 'none', typ: 'JWT' }));
      const payload = base64Url(JSON.stringify({ sub: username, username, roles: ['USER'] }));
      return `${header}.${payload}.signature`;
    });
  },

  mockBaseApi: async ({}, use) => {
    await use(async (page, options = {}) => {
      const setupComplete = options.setupComplete ?? true;

      await page.route(`${API_BASE}/public-settings`, async route => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(defaultPublicSettings()),
        });
      });

      await page.route(`${API_BASE}/setup/status`, async route => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ data: setupComplete }),
        });
      });

      await page.route(`${API_BASE}/icons/all/content`, async route => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({}),
        });
      });

      await page.route(`${API_BASE}/shelves`, async route => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([]),
        });
      });

      await page.route(`${API_BASE}/libraries/health`, async route => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({}),
        });
      });

      await page.route('**/api/magic-shelves', async route => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([]),
        });
      });

      await page.route('ws://localhost:6060/ws**', async route => {
        await route.abort('timedout');
      });
    });
  },

  login: async ({ createAccessToken }, use) => {
    await use(async (page, username, password) => {
      await page.goto('/login');
      await expect(page.locator('.login-container')).toBeVisible();
      await page.fill('#username', username);
      await page.locator('p-password input[type="password"]').fill(password);

      await page.route(`${API_BASE}/auth/login`, async route => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            accessToken: createAccessToken(username),
            refreshToken: 'refresh-token',
            isDefaultPassword: 'false',
          }),
        });
      });

      await page.click('button[type="submit"]');
    });
  },

  setupAccount: async ({}, use) => {
    await use(async (page, username, password) => {
      await expect(page.locator('.setup-container')).toBeVisible();
      await page.fill('#username', username);
      await page.fill('#name', 'Test User');
      await page.fill('#email', `${username}@example.com`);
      await page.fill('#password', password);
      await page.fill('#confirmPassword', password);

      await page.route(`${API_BASE}/setup`, async route => {
        await route.fulfill({ status: 200 });
      });

      await page.click('button[type="submit"]');
    });
  },

  seedUser: async ({}, use) => {
    await use(async (page, user = {}) => {
      await page.route(`${API_BASE}/users/me`, async route => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ ...defaultUser(), ...user }),
        });
      });
    });
  },

  seedLibrary: async ({}, use) => {
    await use(async (page, library = defaultLibrary()) => {
      await page.route(`${API_BASE}/libraries`, async route => {
        if (route.request().method() === 'GET') {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify([library]),
          });
        } else {
          await route.continue();
        }
      });
    });
  },

  seedShelves: async ({}, use) => {
    await use(async (page, shelves = []) => {
      await page.route(`${API_BASE}/shelves`, async route => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(shelves),
        });
      });
    });
  },

  seedBooks: async ({}, use) => {
    await use(async (page, books = []) => {
      await page.route(`${API_BASE}/books`, async route => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(books),
        });
      });

      await page.route(`${API_BASE}/books?**`, async route => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            content: books,
            totalElements: books.length,
            totalPages: 1,
            size: books.length,
            number: 0,
          }),
        });
      });
    });
  },
});

export { expect };
