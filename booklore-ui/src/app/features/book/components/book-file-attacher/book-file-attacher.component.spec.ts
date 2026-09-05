import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {BookFileAttacherComponent} from './book-file-attacher.component';
import {BookService} from '../../service/book.service';
import {BookFileService} from '../../service/book-file.service';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {commonComponentTestProviders} from '../../../../../testing';
import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {createMockBook} from '../../../../../testing/factories';
import {AppSettings} from '../../../../shared/model/app-settings.model';
import {BehaviorSubject, of} from 'rxjs';

describe('BookFileAttacherComponent', () => {
  let fixture: ComponentFixture<BookFileAttacherComponent>;
  let component: BookFileAttacherComponent;
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
        {provide: DynamicDialogConfig, useValue: {data: {sourceBook: createMockBook()}}},
        {
          provide: BookService,
          useValue: {
            bookState$: of({loaded: true, books: [createMockBook({id: 2})], error: null}),
            getCurrentBookState: vi.fn(() => ({loaded: true, books: [createMockBook({id: 2})], error: null}))
          }
        },
        {
          provide: BookFileService,
          useValue: {
            attachBookFiles: vi.fn(() => of({updatedBook: createMockBook({id: 2}), deletedSourceBookIds: []}))
          }
        },
        {
          provide: AppSettingsService,
          useValue: {appSettings$: appSettingsSubject.asObservable()}
        }
      ]
    });

    fixture = TestBed.createComponent(BookFileAttacherComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should create and render without error', () => {
    expect(component).toBeTruthy();
    expect(component.sourceBooks.length).toBe(1);
    expect(fixture.nativeElement).toBeTruthy();
  });
});
