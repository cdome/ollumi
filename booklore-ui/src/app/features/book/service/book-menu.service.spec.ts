import {beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {ConfirmationService, MenuItem, MessageService} from 'primeng/api';
import {of, throwError} from 'rxjs';
import {BookMenuService} from './book-menu.service';
import {BookService} from './book.service';
import {BookMetadataManageService} from './book-metadata-manage.service';
import {LoadingService} from '../../../core/services/loading.service';
import {TranslocoService} from '@jsverse/transloco';
import {
  mockMessageServiceProvider,
  mockTranslocoServiceProvider
} from '../../../../testing/providers';
import {createMockBook, createMockUser} from '../../../../testing/factories';
import {ReadStatus} from '../model/book.model';
import {ResetProgressTypes} from '../../../shared/constants/reset-progress-type';

function findMenuItem(items: MenuItem[], label: string): MenuItem | undefined {
  for (const item of items) {
    if (item.label === label) {
      return item;
    }
    if (item.items) {
      const found = findMenuItem(item.items as MenuItem[], label);
      if (found) {
        return found;
      }
    }
  }
  return undefined;
}

describe('BookMenuService', () => {
  let service: BookMenuService;
  let confirmationService: {confirm: Mock};
  let messageService: {add: Mock; clear: Mock};
  let bookService: {
    updateBookReadStatus: Mock;
    getBooksByIdsFromState: Mock;
    updateBookShelves: Mock;
    resetProgress: Mock;
  };
  let bookMetadataManageService: {updateBooksMetadata: Mock};
  let loadingService: {show: Mock; hide: Mock; hideAll: Mock};

  beforeEach(() => {
    confirmationService = {
      confirm: vi.fn(config => {
        if (config.accept) {
          config.accept();
        }
      })
    };

    bookService = {
      updateBookReadStatus: vi.fn().mockReturnValue(of([])),
      getBooksByIdsFromState: vi.fn().mockReturnValue([]),
      updateBookShelves: vi.fn().mockReturnValue(of([])),
      resetProgress: vi.fn().mockReturnValue(of([]))
    };

    bookMetadataManageService = {
      updateBooksMetadata: vi.fn().mockReturnValue(of(undefined))
    };

    loadingService = {
      show: vi.fn(() => ({id: 'loader'}) as unknown as HTMLElement),
      hide: vi.fn(),
      hideAll: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        BookMenuService,
        {provide: ConfirmationService, useValue: confirmationService},
        mockMessageServiceProvider,
        {provide: BookService, useValue: bookService},
        {provide: BookMetadataManageService, useValue: bookMetadataManageService},
        {provide: LoadingService, useValue: loadingService},
        mockTranslocoServiceProvider
      ]
    });

    service = TestBed.inject(BookMenuService);
    messageService = TestBed.inject(MessageService) as unknown as {add: Mock; clear: Mock};

    vi.clearAllMocks();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getMetadataMenuItems', () => {
    it('should return metadata items when the user has permissions', () => {
      const baseUser = createMockUser();
      const user = createMockUser({
        permissions: {
          ...baseUser.permissions,
          canBulkAutoFetchMetadata: true,
          canBulkCustomFetchMetadata: true,
          canBulkEditMetadata: true,
          canBulkRegenerateCover: true
        }
      });

      const handlers = {
        autoFetchMetadata: vi.fn(),
        fetchMetadata: vi.fn(),
        bulkEditMetadata: vi.fn(),
        multiBookEditMetadata: vi.fn(),
        regenerateCovers: vi.fn(),
        generateCustomCovers: vi.fn()
      };

      const items = service.getMetadataMenuItems(
        handlers.autoFetchMetadata,
        handlers.fetchMetadata,
        handlers.bulkEditMetadata,
        handlers.multiBookEditMetadata,
        handlers.regenerateCovers,
        handlers.generateCustomCovers,
        user
      );

      expect(items.length).toBe(6);

      items[0].command?.({});
      expect(handlers.autoFetchMetadata).toHaveBeenCalled();

      items[1].command?.({});
      expect(handlers.fetchMetadata).toHaveBeenCalled();

      items[2].command?.({});
      expect(handlers.bulkEditMetadata).toHaveBeenCalled();

      items[3].command?.({});
      expect(handlers.multiBookEditMetadata).toHaveBeenCalled();

      items[4].command?.({});
      expect(handlers.regenerateCovers).toHaveBeenCalled();

      items[5].command?.({});
      expect(handlers.generateCustomCovers).toHaveBeenCalled();
    });

    it('should return an empty array when the user has no metadata permissions', () => {
      const items = service.getMetadataMenuItems(vi.fn(), vi.fn(), vi.fn(), vi.fn(), vi.fn(), vi.fn(), null);
      expect(items).toEqual([]);
    });
  });

  describe('getMoreActionsMenu', () => {
    it('should update read status when accepted', () => {
      const user = createMockUser({permissions: {...createMockUser().permissions, canBulkResetBookReadStatus: true}});
      const selectedBooks = new Set([1, 2]);

      const items = service.getMoreActionsMenu(selectedBooks, user);
      const updateItem = findMenuItem(items, 'book.menuService.menu.updateReadStatus');
      const readItem = findMenuItem(updateItem?.items as MenuItem[], 'Read');

      readItem?.command?.({});

      expect(confirmationService.confirm).toHaveBeenCalled();
      expect(bookService.updateBookReadStatus).toHaveBeenCalledWith([1, 2], ReadStatus.READ);
      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
    });

    it('should show an error toast when read status update fails', () => {
      bookService.updateBookReadStatus.mockReturnValue(throwError(() => ({error: {message: 'fail'}})));
      const user = createMockUser({permissions: {...createMockUser().permissions, canBulkResetBookReadStatus: true}});

      const items = service.getMoreActionsMenu(new Set([1]), user);
      const readItem = findMenuItem(findMenuItem(items, 'book.menuService.menu.updateReadStatus')?.items as MenuItem[], 'Read');
      readItem?.command?.({});

      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'error'}));
    });

    it('should set age rating when accepted', () => {
      const user = createMockUser({permissions: {...createMockUser().permissions, canBulkEditMetadata: true}});

      const items = service.getMoreActionsMenu(new Set([1]), user);
      const ageRatingItem = findMenuItem(items, 'book.menuService.menu.setAgeRating');
      const teenItem = findMenuItem(ageRatingItem?.items as MenuItem[], '13+');

      teenItem?.command?.({});

      expect(bookMetadataManageService.updateBooksMetadata).toHaveBeenCalledWith(
        expect.objectContaining({bookIds: [1], ageRating: 13})
      );
      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
    });

    it('should clear age rating when accepted', () => {
      const user = createMockUser({permissions: {...createMockUser().permissions, canBulkEditMetadata: true}});

      const items = service.getMoreActionsMenu(new Set([1]), user);
      const clearItem = findMenuItem(items, 'book.menuService.menu.clearAgeRating');
      clearItem?.command?.({});

      expect(bookMetadataManageService.updateBooksMetadata).toHaveBeenCalledWith(
        expect.objectContaining({bookIds: [1], clearAgeRating: true})
      );
      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
    });

    it('should set content rating when accepted', () => {
      const user = createMockUser({permissions: {...createMockUser().permissions, canBulkEditMetadata: true}});

      const items = service.getMoreActionsMenu(new Set([1]), user);
      const contentRatingItem = findMenuItem(items, 'book.menuService.menu.setContentRating');
      const matureItem = findMenuItem(contentRatingItem?.items as MenuItem[], 'Mature');

      matureItem?.command?.({});

      expect(bookMetadataManageService.updateBooksMetadata).toHaveBeenCalledWith(
        expect.objectContaining({bookIds: [1], contentRating: 'MATURE'})
      );
      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
    });

    it('should clear content rating when accepted', () => {
      const user = createMockUser({permissions: {...createMockUser().permissions, canBulkEditMetadata: true}});

      const items = service.getMoreActionsMenu(new Set([1]), user);
      const clearItem = findMenuItem(items, 'book.menuService.menu.clearContentRating');
      clearItem?.command?.({});

      expect(bookMetadataManageService.updateBooksMetadata).toHaveBeenCalledWith(
        expect.objectContaining({bookIds: [1], clearContentRating: true})
      );
      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
    });

    it('should remove books from all shelves and show success', () => {
      const user = createMockUser({permissions: {...createMockUser().permissions, canManageLibrary: true}});
      const book = createMockBook({id: 1, shelves: [{id: 5, name: 'A', icon: '', iconType: 'PRIME_NG' as const, bookCount: 0}]});
      bookService.getBooksByIdsFromState.mockReturnValue([book]);

      const items = service.getMoreActionsMenu(new Set([1]), user);
      const removeItem = findMenuItem(items, 'book.menuService.menu.removeFromAllShelves');
      removeItem?.command?.({});

      expect(bookService.updateBookShelves).toHaveBeenCalledWith(new Set([1]), new Set(), new Set([5]));
      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
    });

    it('should show info toast when no books are on shelves', () => {
      const user = createMockUser({permissions: {...createMockUser().permissions, canManageLibrary: true}});
      const book = createMockBook({id: 1, shelves: []});
      bookService.getBooksByIdsFromState.mockReturnValue([book]);

      const items = service.getMoreActionsMenu(new Set([1]), user);
      const removeItem = findMenuItem(items, 'book.menuService.menu.removeFromAllShelves');
      removeItem?.command?.({});

      expect(bookService.updateBookShelves).not.toHaveBeenCalled();
      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'info'}));
    });

    it('should reset Booklore progress when accepted', () => {
      const user = createMockUser({permissions: {...createMockUser().permissions, canBulkResetBookloreReadProgress: true}});

      const items = service.getMoreActionsMenu(new Set([1, 2]), user);
      const resetItem = findMenuItem(items, 'book.menuService.menu.resetBookloreProgress');
      resetItem?.command?.({});

      expect(bookService.resetProgress).toHaveBeenCalledWith([1, 2], ResetProgressTypes.BOOKLORE);
      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
    });

    it('should reset KOReader progress when accepted', () => {
      const user = createMockUser({permissions: {...createMockUser().permissions, canBulkResetKoReaderReadProgress: true}});

      const items = service.getMoreActionsMenu(new Set([1]), user);
      const resetItem = findMenuItem(items, 'book.menuService.menu.resetKOReaderProgress');
      resetItem?.command?.({});

      expect(bookService.resetProgress).toHaveBeenCalledWith([1], ResetProgressTypes.KOREADER);
      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
    });

    it('should show an error toast when resetting progress fails', () => {
      bookService.resetProgress.mockReturnValue(throwError(() => ({error: {message: 'fail'}})));
      const user = createMockUser({permissions: {...createMockUser().permissions, canBulkResetBookloreReadProgress: true}});

      const items = service.getMoreActionsMenu(new Set([1]), user);
      const resetItem = findMenuItem(items, 'book.menuService.menu.resetBookloreProgress');
      resetItem?.command?.({});

      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'error'}));
    });
  });
});
