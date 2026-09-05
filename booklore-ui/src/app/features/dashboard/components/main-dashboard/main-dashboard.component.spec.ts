import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA, signal} from '@angular/core';
import {BehaviorSubject, of, firstValueFrom} from 'rxjs';
import {MainDashboardComponent} from './main-dashboard.component';
import {DashboardScrollerComponent} from '../dashboard-scroller/dashboard-scroller.component';
import {BookService} from '../../../book/service/book.service';
import {LibraryService} from '../../../book/service/library.service';
import {UserService} from '../../../settings/user-management/user.service';
import {DashboardConfigService} from '../../services/dashboard-config.service';
import {MagicShelfService} from '../../../magic-shelf/service/magic-shelf.service';
import {BookRuleEvaluatorService} from '../../../magic-shelf/service/book-rule-evaluator.service';
import {DialogLauncherService} from '../../../../shared/services/dialog-launcher.service';
import {SortService} from '../../../book/service/sort.service';
import {PageTitleService} from '../../../../shared/service/page-title.service';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {BookState} from '../../../book/model/state/book-state.model';
import {Book, ReadStatus} from '../../../book/model/book.model';
import {LibraryState} from '../../../book/model/state/library-state.model';
import {UserState} from '../../../settings/user-management/user.service';
import {MagicShelfState} from '../../../magic-shelf/service/magic-shelf.service';
import {DEFAULT_DASHBOARD_CONFIG, ScrollerConfig, ScrollerType} from '../../models/dashboard-config.model';

describe('MainDashboardComponent', () => {
  let fixture: ComponentFixture<MainDashboardComponent>;
  let component: MainDashboardComponent;
  let bookStateSubject: BehaviorSubject<BookState>;
  let libraryStateSubject: BehaviorSubject<LibraryState>;
  let userStateSubject: BehaviorSubject<UserState>;
  let dashboardConfigSubject: BehaviorSubject<typeof DEFAULT_DASHBOARD_CONFIG>;
  let magicShelfStateSubject: BehaviorSubject<MagicShelfState>;
  let pageTitleServiceMock: { setPageTitle: ReturnType<typeof vi.fn> };
  let dialogLauncherMock: { openDashboardSettingsDialog: ReturnType<typeof vi.fn>; openLibraryCreateDialog: ReturnType<typeof vi.fn> };

  const baseBook: Book = {
    id: 1,
    metadata: {title: 'Reading Book'},
    readStatus: ReadStatus.READING,
    lastReadTime: '2024-01-01T00:00:00Z',
    epubProgress: {percentage: 50},
    addedOn: '2024-01-01T00:00:00Z'
  } as Book;

  beforeEach(() => {
    bookStateSubject = new BehaviorSubject<BookState>({books: [], loaded: true, error: null});
    libraryStateSubject = new BehaviorSubject<LibraryState>({libraries: [], loaded: true, error: null});
    userStateSubject = new BehaviorSubject<UserState>({user: null, loaded: true, error: null});
    dashboardConfigSubject = new BehaviorSubject(DEFAULT_DASHBOARD_CONFIG);
    magicShelfStateSubject = new BehaviorSubject<MagicShelfState>({shelves: [], loaded: true, error: null});
    pageTitleServiceMock = {setPageTitle: vi.fn()};
    dialogLauncherMock = {openDashboardSettingsDialog: vi.fn(), openLibraryCreateDialog: vi.fn()};

    TestBed.configureTestingModule({
      imports: [MainDashboardComponent, DashboardScrollerComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        {provide: BookService, useValue: {bookState$: bookStateSubject.asObservable()}},
        {provide: LibraryService, useValue: {libraryState$: libraryStateSubject.asObservable()}},
        {provide: UserService, useValue: {userState$: userStateSubject.asObservable(), userStateSubject}},
        {provide: DashboardConfigService, useValue: {config$: dashboardConfigSubject.asObservable()}},
        {provide: MagicShelfService, useValue: {shelvesState$: magicShelfStateSubject.asObservable()}},
        {provide: BookRuleEvaluatorService, useValue: {evaluateGroup: vi.fn(() => true)}},
        {provide: DialogLauncherService, useValue: dialogLauncherMock},
        {provide: SortService, useValue: {applySort: vi.fn((books: Book[]) => books)}},
        {provide: PageTitleService, useValue: pageTitleServiceMock}
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    TestBed.overrideComponent(DashboardScrollerComponent, {
      set: {template: '', imports: []}
    });

    fixture = TestBed.createComponent(MainDashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture?.destroy();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should set the page title on init', () => {
    expect(pageTitleServiceMock.setPageTitle).toHaveBeenCalled();
  });

  it('should open the dashboard settings dialog', () => {
    component.openDashboardSettings();
    expect(dialogLauncherMock.openDashboardSettingsDialog).toHaveBeenCalled();
  });

  it('should open the library create dialog', () => {
    component.createNewLibrary();
    expect(dialogLauncherMock.openLibraryCreateDialog).toHaveBeenCalled();
  });

  it('should return last-read books for the last read scroller', async () => {
    bookStateSubject.next({books: [baseBook], loaded: true, error: null});

    const config: ScrollerConfig = {
      id: 'last-read',
      type: ScrollerType.LAST_READ,
      title: 'Continue Reading',
      enabled: true,
      order: 1,
      maxItems: 5
    };

    const books = await firstValueFrom(component.getBooksForScroller(config));
    expect(books.length).toBe(1);
    expect(books[0].id).toBe(1);
  });
});
