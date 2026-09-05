import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {BookCardLiteComponent} from './book-card-lite-component';
import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {Router} from '@angular/router';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {UserService} from '../../../settings/user-management/user.service';
import {BookMetadataHostService} from '../../../../shared/service/book-metadata-host.service';
import {
  createMockActivatedRoute,
  createMockUserService,
  mockBook,
  mockBookMetadataHostService,
  mockUrlHelperService,
  setupJsdomMocks,
} from '../../../../../testing/book-component-mocks';

describe('BookCardLiteComponent', () => {
  let fixture: ComponentFixture<BookCardLiteComponent>;
  let component: BookCardLiteComponent;
  let router: Router;
  let bookMetadataHostService: BookMetadataHostService;

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
        createMockActivatedRoute(),
        {provide: Router, useValue: mockRouter},
        {provide: UrlHelperService, useValue: mockUrlHelperService},
        {provide: UserService, useValue: createMockUserService()},
        {provide: BookMetadataHostService, useValue: mockBookMetadataHostService},
      ]
    });

    fixture = TestBed.createComponent(BookCardLiteComponent);
    component = fixture.componentInstance;
    component.book = mockBook;
    router = TestBed.inject(Router);
    bookMetadataHostService = TestBed.inject(BookMetadataHostService);
    fixture.detectChanges();
  });

  it('should create and render without error', () => {
    expect(component).toBeTruthy();
    expect(fixture.nativeElement.querySelector('img')).toBeTruthy();
  });

  it('should navigate to book route in route view mode', () => {
    component.openBookInfo(mockBook);
    expect(router.navigate).toHaveBeenCalledWith(['/book', mockBook.id], {queryParams: {tab: 'view'}});
    expect(bookMetadataHostService.requestBookSwitch).not.toHaveBeenCalled();
  });

  it('should request book switch in dialog view mode', () => {
    const userService = TestBed.inject(UserService) as any;
    userService.userStateSubject.next({
      user: {userSettings: {metadataCenterViewMode: 'dialog'}},
      loaded: true,
    });

    expect((component as any).metadataCenterViewMode).toBe('dialog');
    component.openBookInfo(mockBook);
    expect(bookMetadataHostService.requestBookSwitch).toHaveBeenCalledWith(mockBook.id);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('should detect audiobook-only books', () => {
    const audiobook = {
      ...mockBook,
      primaryFile: {bookType: 'AUDIOBOOK'},
      alternativeFormats: [],
    };
    component.book = audiobook as any;
    expect(component.isAudiobookOnly()).toBe(true);

    const withEbookAlt = {
      ...mockBook,
      primaryFile: {bookType: 'AUDIOBOOK'},
      alternativeFormats: [{bookType: 'EPUB'}],
    };
    component.book = withEbookAlt as any;
    expect(component.isAudiobookOnly()).toBe(false);
  });
});
