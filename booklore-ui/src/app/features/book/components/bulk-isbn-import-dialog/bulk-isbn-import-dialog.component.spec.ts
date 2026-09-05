import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {BulkIsbnImportDialogComponent} from './bulk-isbn-import-dialog.component';
import {BookService} from '../../service/book.service';
import {BookMetadataService} from '../../service/book-metadata.service';
import {LibraryService} from '../../service/library.service';
import {commonComponentTestProviders} from '../../../../../testing';
import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {LibraryState} from '../../../model/state/library-state.model';
import {BehaviorSubject, of} from 'rxjs';

describe('BulkIsbnImportDialogComponent', () => {
  let fixture: ComponentFixture<BulkIsbnImportDialogComponent>;
  let component: BulkIsbnImportDialogComponent;
  const libraryStateSubject = new BehaviorSubject<LibraryState>({loaded: true, libraries: [{id: 1, name: 'Test Library'}], error: null} as LibraryState);

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders.filter(p => p.provide !== TranslocoService),
        {provide: DynamicDialogRef, useValue: {close: vi.fn()}},
        {provide: DynamicDialogConfig, useValue: {data: {libraryId: 1}}},
        {
          provide: BookService,
          useValue: {
            bookState$: of({loaded: true, books: [], error: null}),
            getCurrentBookState: vi.fn(() => ({loaded: true, books: [], error: null})),
            createPhysicalBook: vi.fn(() => of({}))
          }
        },
        {
          provide: BookMetadataService,
          useValue: {
            lookupByIsbn: vi.fn(() => of(null))
          }
        },
        {
          provide: LibraryService,
          useValue: {
            libraryState$: libraryStateSubject.asObservable(),
            getLibrariesFromState: vi.fn(() => []),
            findLibraryById: vi.fn(),
            doesLibraryExistByName: vi.fn(() => false)
          }
        }
      ]
    });

    fixture = TestBed.createComponent(BulkIsbnImportDialogComponent);
    component = fixture.componentInstance;
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
