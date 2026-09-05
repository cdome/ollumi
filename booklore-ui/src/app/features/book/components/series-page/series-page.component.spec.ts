import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {SeriesPageComponent} from './series-page.component';
import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {Router} from '@angular/router';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {BookService} from '../../service/book.service';
import {BookMetadataManageService} from '../../service/book-metadata-manage.service';
import {CoverScalePreferenceService} from '../book-browser/cover-scale-preference.service';
import {UserService} from '../../../settings/user-management/user.service';
import {BookMenuService} from '../../service/book-menu.service';
import {ConfirmationService, MessageService} from 'primeng/api';
import {LoadingService} from '../../../../core/services/loading.service';
import {BookDialogHelperService} from '../book-browser/book-dialog-helper.service';
import {TaskHelperService} from '../../../settings/task-management/task-helper.service';
import {BookCardOverlayPreferenceService} from '../book-browser/book-card-overlay-preference.service';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {ReadStatusHelper} from '../../helpers/read-status.helper';
import {EmailService} from '../../../settings/email-v2/email.service';
import {BookFileService} from '../../service/book-file.service';
import {BookNavigationService} from '../../service/book-navigation.service';
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
  mockCoverScalePreferenceService,
  mockEmailService,
  mockLoadingService,
  mockReadStatusHelper,
  mockTaskHelperService,
  mockUrlHelperService,
  setupJsdomMocks,
} from '../../../../../testing/book-component-mocks';
import {mockMessageServiceProvider} from '../../../../../testing/providers';
import {firstValueFrom} from 'rxjs';

describe('SeriesPageComponent', () => {
  let fixture: ComponentFixture<SeriesPageComponent>;
  let component: SeriesPageComponent;
  let router: Router;
  let bookService: BookService;

  const mockRouter = {
    navigate: vi.fn(() => Promise.resolve(true)),
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
        createMockActivatedRoute({path: 'series/:seriesName', params: {seriesName: encodeURIComponent('Test Series')}}),
        {provide: Router, useValue: mockRouter},
        {provide: UrlHelperService, useValue: mockUrlHelperService},
        {provide: BookService, useValue: createMockBookService()},
        {provide: BookMetadataManageService, useValue: mockBookMetadataManageService},
        {provide: CoverScalePreferenceService, useValue: mockCoverScalePreferenceService},
        {provide: UserService, useValue: createMockUserService()},
        {provide: BookMenuService, useValue: mockBookMenuService},
        mockMessageServiceProvider,
        {provide: ConfirmationService, useValue: mockConfirmationService},
        {provide: LoadingService, useValue: mockLoadingService},
        {provide: BookDialogHelperService, useValue: mockBookDialogHelperService},
        {provide: TaskHelperService, useValue: mockTaskHelperService},
        {provide: BookCardOverlayPreferenceService, useValue: mockBookCardOverlayPreferenceService},
        {provide: AppSettingsService, useValue: mockAppSettingsService},
        {provide: ReadStatusHelper, useValue: mockReadStatusHelper},
        {provide: EmailService, useValue: mockEmailService},
        {provide: BookFileService, useValue: mockBookFileService},
        {provide: BookNavigationService, useValue: mockBookNavigationService},
      ]
    });

    fixture = TestBed.createComponent(SeriesPageComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    bookService = TestBed.inject(BookService);
    fixture.detectChanges();
  });

  it('should create and render series page without error', () => {
    expect(component).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.series-wrapper')).toBeTruthy();
  });

  it('should decode seriesName from route params', async () => {
    const seriesName = await firstValueFrom(component.seriesParam$);
    expect(seriesName).toBe('Test Series');
  });

  it('should navigate to filtered author books', () => {
    component.goToAuthorBooks('Jane Doe');
    expect(router.navigate).toHaveBeenCalledWith(['/all-books'], {
      queryParams: {
        view: 'grid',
        sort: 'title',
        direction: 'asc',
        sidebar: true,
        filter: `author:${encodeURIComponent('Jane Doe')}`,
      },
    });
  });

  it('should call BookService.readBook from readBook', () => {
    component.readBook(mockBook.id);
    expect(bookService.readBook).toHaveBeenCalledWith(mockBook.id);
  });

  it('should navigate to book details from viewBookDetails', () => {
    component.viewBookDetails(mockBook.id);
    expect(router.navigate).toHaveBeenCalledWith(['/book', mockBook.id], {queryParams: {tab: 'view'}});
  });
});
