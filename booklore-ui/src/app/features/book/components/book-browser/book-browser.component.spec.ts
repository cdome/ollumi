import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {Router} from '@angular/router';
import {MessageService, ConfirmationService} from 'primeng/api';
import {BookBrowserComponent, EntityType} from './book-browser.component';
import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {BookService} from '../../service/book.service';
import {BookMetadataManageService} from '../../service/book-metadata-manage.service';
import {BookDialogHelperService} from './book-dialog-helper.service';
import {BookMenuService} from '../../service/book-menu.service';
import {LibraryShelfMenuService} from '../../service/library-shelf-menu.service';
import {LoadingService} from '../../../../core/services/loading.service';
import {BookNavigationService} from '../../service/book-navigation.service';
import {BookBrowserQueryParamsService} from './book-browser-query-params.service';
import {BookBrowserEntityService} from './book-browser-entity.service';
import {BookFilterOrchestrationService} from './book-filter-orchestration.service';
import {BookBrowserScrollService} from './book-browser-scroll.service';
import {LocalStorageService} from '../../../../shared/service/local-storage.service';
import {PageTitleService} from '../../../../shared/service/page-title.service';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {SortService} from '../../service/sort.service';
import {CoverScalePreferenceService} from './cover-scale-preference.service';
import {TableColumnPreferenceService} from './table-column-preference.service';
import {SidebarFilterTogglePrefService} from './filters/sidebar-filter-toggle-pref.service';
import {BookCardOverlayPreferenceService} from './book-card-overlay-preference.service';
import {BookSelectionService} from './book-selection.service';
import {UserService} from '../../../settings/user-management/user.service';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {ReadStatusHelper} from '../../helpers/read-status.helper';
import {BookFileService} from '../../service/book-file.service';
import {EmailService} from '../../../settings/email-v2/email.service';
import {TaskHelperService} from '../../../settings/task-management/task-helper.service';
import {
  createMockActivatedRoute,
  createMockBookService,
  createMockUserService,
  mockBook,
  mockBookDialogHelperService,
  mockBookFileService,
  mockBookMetadataManageService,
  mockBookMenuService,
  mockBookNavigationService,
  mockBookCardOverlayPreferenceService,
  mockAppSettingsService,
  mockConfirmationService,
  mockBookBrowserEntityService,
  mockBookBrowserQueryParamsService,
  mockBookBrowserScrollService,
  mockBookFilterOrchestrationService,
  mockCoverScalePreferenceService,
  mockEmailService,
  mockLibraryShelfMenuService,
  mockLoadingService,
  mockLocalStorageService,
  mockPageTitleService,
  mockReadStatusHelper,
  mockSidebarFilterTogglePrefService,
  mockSortService,
  mockTableColumnPreferenceService,
  mockBookSelectionService,
  mockTaskHelperService,
  mockUrlHelperService,
  setupJsdomMocks,
} from '../../../../../testing/book-component-mocks';
import {mockMessageServiceProvider} from '../../../../../testing/providers';
import {firstValueFrom} from 'rxjs';

describe('BookBrowserComponent', () => {
  let fixture: ComponentFixture<BookBrowserComponent>;
  let component: BookBrowserComponent;
  let router: Router;

  const mockRouter = {
    navigate: vi.fn(() => Promise.resolve(true)),
    events: {pipe: vi.fn(() => ({subscribe: vi.fn()}))},
    url: '/all-books',
  };

  afterEach(() => {
    vi.restoreAllMocks();
    vi.clearAllMocks();
  });

  beforeEach(() => {
    setupJsdomMocks();
    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      providers: [
        createMockActivatedRoute({path: 'all-books', queryParams: {}}),
        {provide: Router, useValue: mockRouter},
        mockMessageServiceProvider,
        {provide: ConfirmationService, useValue: mockConfirmationService},
        {provide: BookService, useValue: createMockBookService()},
        {provide: BookMetadataManageService, useValue: mockBookMetadataManageService},
        {provide: BookDialogHelperService, useValue: mockBookDialogHelperService},
        {provide: BookMenuService, useValue: mockBookMenuService},
        {provide: LibraryShelfMenuService, useValue: mockLibraryShelfMenuService},
        {provide: LoadingService, useValue: mockLoadingService},
        {provide: BookNavigationService, useValue: mockBookNavigationService},
        {provide: BookBrowserQueryParamsService, useValue: mockBookBrowserQueryParamsService},
        {provide: BookBrowserEntityService, useValue: mockBookBrowserEntityService},
        {provide: BookFilterOrchestrationService, useValue: mockBookFilterOrchestrationService},
        {provide: BookBrowserScrollService, useValue: mockBookBrowserScrollService},
        {provide: LocalStorageService, useValue: mockLocalStorageService},
        {provide: PageTitleService, useValue: mockPageTitleService},
        {provide: AppSettingsService, useValue: mockAppSettingsService},
        {provide: SortService, useValue: mockSortService},
        {provide: CoverScalePreferenceService, useValue: mockCoverScalePreferenceService},
        {provide: TableColumnPreferenceService, useValue: mockTableColumnPreferenceService},
        {provide: SidebarFilterTogglePrefService, useValue: mockSidebarFilterTogglePrefService},
        {provide: BookCardOverlayPreferenceService, useValue: mockBookCardOverlayPreferenceService},
        {provide: BookSelectionService, useValue: mockBookSelectionService},
        {provide: UserService, useValue: createMockUserService()},
        {provide: UrlHelperService, useValue: mockUrlHelperService},
        {provide: ReadStatusHelper, useValue: mockReadStatusHelper},
        {provide: BookFileService, useValue: mockBookFileService},
        {provide: EmailService, useValue: mockEmailService},
        {provide: TaskHelperService, useValue: mockTaskHelperService},
      ]
    });

    fixture = TestBed.createComponent(BookBrowserComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('should create and render the browser without error', () => {
    expect(component).toBeTruthy();
    expect(component.entityType).toBe(EntityType.ALL_BOOKS);
    expect(fixture.nativeElement.querySelector('.book-browser-container')).toBeTruthy();
  });

  it('should expose the entity type as ALL_BOOKS from the route', async () => {
    const entityType = await firstValueFrom(component.entityType$!);
    expect(entityType).toBe(EntityType.ALL_BOOKS);
  });

  it('should toggle between grid and table view', () => {
    component.currentViewMode = 'grid';
    component.toggleTableGrid();
    expect(component.currentViewMode).toBe('table');
    expect(mockBookBrowserQueryParamsService.updateViewMode).toHaveBeenCalledWith('table');

    component.toggleTableGrid();
    expect(component.currentViewMode).toBe('grid');
  });

  it('should toggle the sidebar filter visibility', () => {
    component.showFilter = false;
    component.toggleSidebar();
    expect(component.showFilter).toBe(true);
  });

  it('should clear the search term and title', () => {
    component.bookTitle = 'query';
    component.searchTerm$.next('query');
    component.clearSearch();
    expect(component.bookTitle).toBe('');
    expect(component.hasSearchTerm).toBe(false);
  });

  it('should call ConfirmationService when confirming delete', () => {
    component.confirmDeleteBooks();
    expect(mockConfirmationService.confirm).toHaveBeenCalled();
  });
});
