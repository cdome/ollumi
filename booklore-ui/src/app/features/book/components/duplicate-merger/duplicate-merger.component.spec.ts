import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {DuplicateMergerComponent} from './duplicate-merger.component';
import {BookFileService} from '../../service/book-file.service';
import {BookService} from '../../service/book.service';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {commonComponentTestProviders} from '../../../../../testing';
import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {createMockBook} from '../../../../../testing/factories';
import {AppSettings} from '../../../../shared/model/app-settings.model';
import {BehaviorSubject, of} from 'rxjs';

describe('DuplicateMergerComponent', () => {
  let fixture: ComponentFixture<DuplicateMergerComponent>;
  let component: DuplicateMergerComponent;
  const appSettingsSubject = new BehaviorSubject<AppSettings | null>({
    metadataPersistenceSettings: {moveFilesToLibraryPattern: false}
  } as AppSettings);

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders.filter(p => p.provide !== TranslocoService),
        {provide: DynamicDialogRef, useValue: {close: vi.fn()}},
        {provide: DynamicDialogConfig, useValue: {data: {libraryId: 1}}},
        {
          provide: BookFileService,
          useValue: {
            findDuplicates: vi.fn(() => of([])),
            attachBookFiles: vi.fn(() => of({updatedBook: createMockBook(), deletedSourceBookIds: []}))
          }
        },
        {
          provide: BookService,
          useValue: {
            bookState$: of({loaded: true, books: [], error: null}),
            getCurrentBookState: vi.fn(() => ({loaded: true, books: [], error: null})),
            deleteBooks: vi.fn(() => of({}))
          }
        },
        {
          provide: AppSettingsService,
          useValue: {appSettings$: appSettingsSubject.asObservable()}
        }
      ]
    });

    fixture = TestBed.createComponent(DuplicateMergerComponent);
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
