import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {BookReviewsComponent} from './book-reviews.component';
import {BookReviewService} from './book-review-service';
import {BookService} from '../../service/book.service';
import {BookMetadataManageService} from '../../service/book-metadata-manage.service';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {commonComponentTestProviders} from '../../../../../testing';
import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {AppSettings} from '../../../../shared/model/app-settings.model';
import {BehaviorSubject, of} from 'rxjs';

describe('BookReviewsComponent', () => {
  let fixture: ComponentFixture<BookReviewsComponent>;
  let component: BookReviewsComponent;
  const appSettingsSubject = new BehaviorSubject<AppSettings | null>({
    metadataPublicReviewsSettings: {downloadEnabled: true}
  } as AppSettings);

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders.filter(p => p.provide !== TranslocoService),
        {
          provide: BookReviewService,
          useValue: {
            getByBookId: vi.fn(() => of([])),
            refreshReviews: vi.fn(() => of([])),
            delete: vi.fn(() => of(void 0)),
            deleteAllByBookId: vi.fn(() => of(void 0))
          }
        },
        {
          provide: BookService,
          useValue: {
            bookState$: of({loaded: true, books: [], error: null}),
            getCurrentBookState: vi.fn(() => ({loaded: true, books: [], error: null}))
          }
        },
        {
          provide: BookMetadataManageService,
          useValue: {
            toggleFieldLocks: vi.fn(() => of(void 0))
          }
        },
        {
          provide: AppSettingsService,
          useValue: {appSettings$: appSettingsSubject.asObservable()}
        }
      ]
    });

    fixture = TestBed.createComponent(BookReviewsComponent);
    component = fixture.componentInstance;
    component.bookId = 1;
    component.reviews = [];
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should create and render without error', () => {
    expect(component).toBeTruthy();
    expect(fixture.nativeElement).toBeTruthy();
  });
});
