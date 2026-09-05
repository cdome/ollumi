import {beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {ConfirmationService, MenuItem, MessageService} from 'primeng/api';
import {Router} from '@angular/router';
import {of, throwError} from 'rxjs';
import {LibraryShelfMenuService} from './library-shelf-menu.service';
import {LibraryService} from './library.service';
import {ShelfService} from './shelf.service';
import {TaskHelperService} from '../../settings/task-management/task-helper.service';
import {MagicShelfService, type MagicShelf} from '../../magic-shelf/service/magic-shelf.service';
import {UserService} from '../../settings/user-management/user.service';
import {LoadingService} from '../../../core/services/loading.service';
import {DialogLauncherService} from '../../../shared/services/dialog-launcher.service';
import {BookDialogHelperService} from '../components/book-browser/book-dialog-helper.service';
import {TranslocoService} from '@jsverse/transloco';
import {
  mockMessageServiceProvider,
  mockRouterProvider,
  mockTranslocoServiceProvider
} from '../../../../testing/providers';
import {createMockLibrary, createMockShelf, createMockUser} from '../../../../testing/factories';
import {MetadataRefreshType} from '../../metadata/model/request/metadata-refresh-type.enum';

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

describe('LibraryShelfMenuService', () => {
  let service: LibraryShelfMenuService;
  let confirmationService: {confirm: Mock};
  let messageService: {add: Mock; clear: Mock};
  let router: Router;
  let libraryService: {refreshLibrary: Mock; deleteLibrary: Mock};
  let shelfService: {deleteShelf: Mock};
  let taskHelperService: {refreshMetadataTask: Mock};
  let magicShelfService: {deleteShelf: Mock};
  let userService: {getCurrentUser: Mock; userState$: ReturnType<typeof of>};
  let loadingService: {show: Mock; hide: Mock; hideAll: Mock};
  let dialogLauncherService: {
    openLibraryEditDialog: Mock;
    openLibraryMetadataFetchDialog: Mock;
    openShelfEditDialog: Mock;
    openMagicShelfEditDialog: Mock;
  };
  let bookDialogHelperService: {
    openAddPhysicalBookDialog: Mock;
    openBulkIsbnImportDialog: Mock;
    openDuplicateMergerDialog: Mock;
  };

  beforeEach(() => {
    confirmationService = {
      confirm: vi.fn(config => {
        if (config.accept) {
          config.accept();
        }
      })
    };

    libraryService = {
      refreshLibrary: vi.fn().mockReturnValue(of(undefined)),
      deleteLibrary: vi.fn().mockReturnValue(of(undefined))
    };

    shelfService = {
      deleteShelf: vi.fn().mockReturnValue(of(undefined))
    };

    taskHelperService = {
      refreshMetadataTask: vi.fn().mockReturnValue(of({success: true}))
    };

    magicShelfService = {
      deleteShelf: vi.fn().mockReturnValue(of(undefined))
    };

    userService = {
      getCurrentUser: vi.fn().mockReturnValue(createMockUser()),
      userState$: of(null)
    };

    loadingService = {
      show: vi.fn(() => ({id: 'loader'}) as unknown as HTMLElement),
      hide: vi.fn(),
      hideAll: vi.fn()
    };

    dialogLauncherService = {
      openLibraryEditDialog: vi.fn(),
      openLibraryMetadataFetchDialog: vi.fn(),
      openShelfEditDialog: vi.fn(),
      openMagicShelfEditDialog: vi.fn()
    };

    bookDialogHelperService = {
      openAddPhysicalBookDialog: vi.fn(),
      openBulkIsbnImportDialog: vi.fn(),
      openDuplicateMergerDialog: vi.fn()
    };

    Object.assign(navigator, {
      clipboard: {
        writeText: vi.fn().mockResolvedValue(undefined)
      }
    });

    TestBed.configureTestingModule({
      providers: [
        LibraryShelfMenuService,
        {provide: ConfirmationService, useValue: confirmationService},
        mockMessageServiceProvider,
        mockRouterProvider,
        {provide: LibraryService, useValue: libraryService},
        {provide: ShelfService, useValue: shelfService},
        {provide: TaskHelperService, useValue: taskHelperService},
        {provide: MagicShelfService, useValue: magicShelfService},
        {provide: UserService, useValue: userService},
        {provide: LoadingService, useValue: loadingService},
        {provide: DialogLauncherService, useValue: dialogLauncherService},
        {provide: BookDialogHelperService, useValue: bookDialogHelperService},
        mockTranslocoServiceProvider
      ]
    });

    service = TestBed.inject(LibraryShelfMenuService);
    messageService = TestBed.inject(MessageService) as unknown as {add: Mock; clear: Mock};
    router = TestBed.inject(Router);

    vi.clearAllMocks();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('initializeLibraryMenuItems', () => {
    const library = createMockLibrary();

    it('should add a physical book', () => {
      const items = service.initializeLibraryMenuItems(library);
      const addBookItem = findMenuItem(items, 'book.shelfMenuService.library.addPhysicalBook');
      addBookItem?.command?.({});

      expect(bookDialogHelperService.openAddPhysicalBookDialog).toHaveBeenCalledWith(library.id);
    });

    it('should open bulk ISBN import', () => {
      const items = service.initializeLibraryMenuItems(library);
      const importItem = findMenuItem(items, 'book.shelfMenuService.library.bulkIsbnImport');
      importItem?.command?.({});

      expect(bookDialogHelperService.openBulkIsbnImportDialog).toHaveBeenCalledWith(library.id);
    });

    it('should open library edit dialog', () => {
      const items = service.initializeLibraryMenuItems(library);
      const editItem = findMenuItem(items, 'book.shelfMenuService.library.editLibrary');
      editItem?.command?.({});

      expect(dialogLauncherService.openLibraryEditDialog).toHaveBeenCalledWith(library.id);
    });

    it('should refresh library and show success', () => {
      const items = service.initializeLibraryMenuItems(library);
      const rescanItem = findMenuItem(items, 'book.shelfMenuService.library.rescanLibrary');
      rescanItem?.command?.({});

      expect(libraryService.refreshLibrary).toHaveBeenCalledWith(library.id);
      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'info'}));
    });

    it('should show error toast when library refresh fails', () => {
      libraryService.refreshLibrary.mockReturnValue(throwError(() => ({message: 'refresh failed'})));

      const items = service.initializeLibraryMenuItems(library);
      const rescanItem = findMenuItem(items, 'book.shelfMenuService.library.rescanLibrary');
      rescanItem?.command?.({});

      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'error'}));
    });

    it('should open library metadata fetch dialog', () => {
      const items = service.initializeLibraryMenuItems(library);
      const fetchItem = findMenuItem(items, 'book.shelfMenuService.library.customFetchMetadata');
      fetchItem?.command?.({});

      expect(dialogLauncherService.openLibraryMetadataFetchDialog).toHaveBeenCalledWith(library.id);
    });

    it('should schedule auto metadata refresh', () => {
      const items = service.initializeLibraryMenuItems(library);
      const autoFetchItem = findMenuItem(items, 'book.shelfMenuService.library.autoFetchMetadata');
      autoFetchItem?.command?.({});

      expect(taskHelperService.refreshMetadataTask).toHaveBeenCalledWith({
        refreshType: MetadataRefreshType.LIBRARY,
        libraryId: library.id
      });
    });

    it('should open duplicate merger dialog', () => {
      const items = service.initializeLibraryMenuItems(library);
      const duplicatesItem = findMenuItem(items, 'book.shelfMenuService.library.findDuplicates');
      duplicatesItem?.command?.({});

      expect(bookDialogHelperService.openDuplicateMergerDialog).toHaveBeenCalledWith(library.id);
    });

    it('should delete library, navigate and show success', () => {
      const items = service.initializeLibraryMenuItems(library);
      const deleteItem = findMenuItem(items, 'book.shelfMenuService.library.deleteLibrary');
      deleteItem?.command?.({});

      expect(libraryService.deleteLibrary).toHaveBeenCalledWith(library.id);
      expect(router.navigate).toHaveBeenCalledWith(['/']);
      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'info'}));
    });

    it('should show error toast when library deletion fails', () => {
      libraryService.deleteLibrary.mockReturnValue(throwError(() => ({message: 'delete failed'})));

      const items = service.initializeLibraryMenuItems(library);
      const deleteItem = findMenuItem(items, 'book.shelfMenuService.library.deleteLibrary');
      deleteItem?.command?.({});

      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'error'}));
    });
  });

  describe('initializeShelfMenuItems', () => {
    it('should enable options for the shelf owner', () => {
      const owner = createMockUser({id: 7});
      userService.getCurrentUser.mockReturnValue(owner);

      const shelf = createMockShelf({id: 2, userId: 7, publicShelf: false});
      const items = service.initializeShelfMenuItems(shelf);
      const editItem = findMenuItem(items, 'book.shelfMenuService.shelf.editShelf');

      expect(editItem?.disabled).toBe(false);
    });

    it('should disable options for a non-owner public shelf', () => {
      const otherUser = createMockUser({id: 8});
      userService.getCurrentUser.mockReturnValue(otherUser);

      const shelf = createMockShelf({id: 2, userId: 7, publicShelf: true});
      const items = service.initializeShelfMenuItems(shelf);
      const editItem = findMenuItem(items, 'book.shelfMenuService.shelf.editShelf');

      expect(editItem?.disabled).toBe(true);
    });

    it('should open shelf edit dialog', () => {
      const shelf = createMockShelf({id: 3, userId: 1, publicShelf: false});
      const items = service.initializeShelfMenuItems(shelf);
      const editItem = findMenuItem(items, 'book.shelfMenuService.shelf.editShelf');
      editItem?.command?.({});

      expect(dialogLauncherService.openShelfEditDialog).toHaveBeenCalledWith(shelf.id);
    });

    it('should delete shelf, navigate and show success', () => {
      const shelf = createMockShelf({id: 4, userId: 1, publicShelf: false});
      const items = service.initializeShelfMenuItems(shelf);
      const deleteItem = findMenuItem(items, 'book.shelfMenuService.shelf.deleteShelf');
      deleteItem?.command?.({});

      expect(shelfService.deleteShelf).toHaveBeenCalledWith(shelf.id);
      expect(router.navigate).toHaveBeenCalledWith(['/']);
      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'info'}));
    });

    it('should show error toast when shelf deletion fails', () => {
      shelfService.deleteShelf.mockReturnValue(throwError(() => ({message: 'delete failed'})));

      const shelf = createMockShelf({id: 5, userId: 1, publicShelf: false});
      const items = service.initializeShelfMenuItems(shelf);
      const deleteItem = findMenuItem(items, 'book.shelfMenuService.shelf.deleteShelf');
      deleteItem?.command?.({});

      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'error'}));
    });
  });

  describe('initializeMagicShelfMenuItems', () => {
    it('should disable delete for public magic shelf when user is not admin', () => {
      userService.getCurrentUser.mockReturnValue(createMockUser({permissions: {...createMockUser().permissions, admin: false}}));
      const magicShelf: MagicShelf = {id: 10, name: 'Public Magic Shelf', filterJson: '{}', isPublic: true};

      const items = service.initializeMagicShelfMenuItems(magicShelf);
      const deleteItem = findMenuItem(items, 'book.shelfMenuService.magicShelf.deleteMagicShelf');

      expect(deleteItem?.disabled).toBe(true);
    });

    it('should enable delete for public magic shelf when user is admin', () => {
      userService.getCurrentUser.mockReturnValue(createMockUser({permissions: {...createMockUser().permissions, admin: true}}));
      const magicShelf: MagicShelf = {id: 10, name: 'Public Magic Shelf', filterJson: '{}', isPublic: true};

      const items = service.initializeMagicShelfMenuItems(magicShelf);
      const deleteItem = findMenuItem(items, 'book.shelfMenuService.magicShelf.deleteMagicShelf');

      expect(deleteItem?.disabled).toBe(false);
    });

    it('should open magic shelf edit dialog', () => {
      const magicShelf: MagicShelf = {id: 11, name: 'Test Magic Shelf', filterJson: '{}', isPublic: false};
      const items = service.initializeMagicShelfMenuItems(magicShelf);
      const editItem = findMenuItem(items, 'book.shelfMenuService.magicShelf.editMagicShelf');
      editItem?.command?.({});

      expect(dialogLauncherService.openMagicShelfEditDialog).toHaveBeenCalledWith(magicShelf.id);
    });

    it('should copy filter JSON to clipboard', async () => {
      const magicShelf: MagicShelf = {id: 12, name: 'Exportable', filterJson: '{"key":"value"}', isPublic: false};
      const items = service.initializeMagicShelfMenuItems(magicShelf);
      const exportItem = findMenuItem(items, 'book.shelfMenuService.magicShelf.exportJson');
      exportItem?.command?.({});

      expect(navigator.clipboard.writeText).toHaveBeenCalledWith(magicShelf.filterJson);
      await expect(navigator.clipboard.writeText(magicShelf.filterJson)).resolves.toBeUndefined();
    });

    it('should delete magic shelf, navigate and show success', () => {
      const magicShelf: MagicShelf = {id: 13, name: 'Deletable', filterJson: '{}', isPublic: false};
      const items = service.initializeMagicShelfMenuItems(magicShelf);
      const deleteItem = findMenuItem(items, 'book.shelfMenuService.magicShelf.deleteMagicShelf');
      deleteItem?.command?.({});

      expect(magicShelfService.deleteShelf).toHaveBeenCalledWith(magicShelf.id);
      expect(router.navigate).toHaveBeenCalledWith(['/']);
      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'info'}));
    });

    it('should show error toast when magic shelf deletion fails', () => {
      magicShelfService.deleteShelf.mockReturnValue(throwError(() => ({message: 'delete failed'})));
      const magicShelf: MagicShelf = {id: 14, name: 'Deletable', filterJson: '{}', isPublic: false};

      const items = service.initializeMagicShelfMenuItems(magicShelf);
      const deleteItem = findMenuItem(items, 'book.shelfMenuService.magicShelf.deleteMagicShelf');
      deleteItem?.command?.({});

      expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'error'}));
    });
  });
});
