import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {BookCardComponent} from './book-card.component';
import {getTranslocoModule} from '../../../../../core/testing/transloco-testing';
import {Router} from '@angular/router';
import {UrlHelperService} from '../../../../../shared/service/url-helper.service';
import {BookService} from '../../../service/book.service';
import {BookFileService} from '../../../service/book-file.service';
import {BookMetadataManageService} from '../../../service/book-metadata-manage.service';
import {TaskHelperService} from '../../../../settings/task-management/task-helper.service';
import {UserService} from '../../../../settings/user-management/user.service';
import {EmailService} from '../../../../settings/email-v2/email.service';
import {MessageService, ConfirmationService} from 'primeng/api';
import {BookDialogHelperService} from '../book-dialog-helper.service';
import {BookNavigationService} from '../../../service/book-navigation.service';
import {BookCardOverlayPreferenceService} from '../book-card-overlay-preference.service';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';
import {ReadStatusHelper} from '../../../helpers/read-status.helper';
import {
  createMockBookService,
  createMockUserService,
  mockBook,
  mockBookDialogHelperService,
  mockBookFileService,
  mockBookMetadataManageService,
  mockBookNavigationService,
  mockBookCardOverlayPreferenceService,
  mockAppSettingsService,
  mockConfirmationService,
  mockEmailService,
  mockReadStatusHelper,
  mockTaskHelperService,
  mockUrlHelperService,
  setupJsdomMocks,
} from '../../../../../../testing/book-component-mocks';
import {mockMessageServiceProvider} from '../../../../../../testing/providers';

describe('BookCardComponent', () => {
  let fixture: ComponentFixture<BookCardComponent>;
  let component: BookCardComponent;
  let router: Router;
  let bookService: BookService;

  afterEach(() => {
    vi.restoreAllMocks();
    vi.clearAllMocks();
  });

  beforeEach(() => {
    setupJsdomMocks();
    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      providers: [
        {provide: Router, useValue: {navigate: vi.fn(() => Promise.resolve(true))}},
        {provide: UrlHelperService, useValue: mockUrlHelperService},
        {provide: BookService, useValue: createMockBookService()},
        {provide: BookFileService, useValue: mockBookFileService},
        {provide: BookMetadataManageService, useValue: mockBookMetadataManageService},
        {provide: TaskHelperService, useValue: mockTaskHelperService},
        {provide: UserService, useValue: createMockUserService()},
        {provide: EmailService, useValue: mockEmailService},
        mockMessageServiceProvider,
        {provide: ConfirmationService, useValue: mockConfirmationService},
        {provide: BookDialogHelperService, useValue: mockBookDialogHelperService},
        {provide: BookNavigationService, useValue: mockBookNavigationService},
        {provide: BookCardOverlayPreferenceService, useValue: mockBookCardOverlayPreferenceService},
        {provide: AppSettingsService, useValue: mockAppSettingsService},
        {provide: ReadStatusHelper, useValue: mockReadStatusHelper},
      ]
    });

    fixture = TestBed.createComponent(BookCardComponent);
    component = fixture.componentInstance;
    component.book = mockBook;
    component.index = 0;
    fixture.detectChanges();
    router = TestBed.inject(Router);
    bookService = TestBed.inject(BookService);
  });

  it('should create and render cover without error', () => {
    expect(component).toBeTruthy();
    expect(fixture.nativeElement.querySelector('img')).toBeTruthy();
  });

  it('should emit checkboxClick when toggling selection', () => {
    const emitSpy = vi.spyOn(component.checkboxClick, 'emit');
    component.isCheckboxEnabled = true;
    component.toggleCardSelection(true);
    expect(emitSpy).toHaveBeenCalledWith({index: 0, book: mockBook, selected: true, shiftKey: false});
  });

  it('should call BookService.readBook when readBook is invoked', () => {
    component.readBook(mockBook);
    expect(bookService.readBook).toHaveBeenCalledWith(mockBook.id);
  });

  it('should navigate to book details when openBookInfo is invoked', () => {
    component.openBookInfo(mockBook);
    expect(router.navigate).toHaveBeenCalledWith(['/book', mockBook.id], {queryParams: {tab: 'view'}});
  });

  it('should emit menuToggled events', () => {
    const emitSpy = vi.spyOn(component.menuToggled, 'emit');
    component.onMenuShow();
    expect(emitSpy).toHaveBeenCalledWith(true);
    component.onMenuHide();
    expect(emitSpy).toHaveBeenCalledWith(false);
  });
});
