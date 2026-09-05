import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {of} from 'rxjs';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {CoverSearchComponent} from './cover-search.component';
import {BookCoverService} from '../../../../shared/services/book-cover.service';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {BookService} from '../../../book/service/book.service';
import {createMockBook, commonComponentTestProviders} from '../../../../../testing';

const mockImages = [
  {url: 'http://example.com/cover.jpg', source: 'test', index: 0}
];

describe('CoverSearchComponent', () => {
  let fixture: ComponentFixture<CoverSearchComponent>;
  let component: CoverSearchComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CoverSearchComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        ...commonComponentTestProviders,
        {provide: BookCoverService, useValue: {
          fetchBookCovers: vi.fn(() => of(mockImages))
        }},
        {provide: DynamicDialogConfig, useValue: {data: {bookId: 42}}},
        {provide: DynamicDialogRef, useValue: {close: vi.fn()}},
        {provide: BookService, useValue: {
          bookState$: of({loaded: true, books: [], error: null}),
          getCurrentBookState: vi.fn(() => ({loaded: true, books: [], error: null})),
          getBookByIdFromState: vi.fn(() => createMockBook({id: 42, metadata: {title: 'Title', authors: ['Author']}} as any))
        }}
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    fixture = TestBed.createComponent(CoverSearchComponent);
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

  it('should initialize cover search with book title and author', () => {
    expect(component.searchForm.get('title')?.value).toBe('Title');
    expect(component.searchForm.get('author')?.value).toBe('Author');
  });
});
