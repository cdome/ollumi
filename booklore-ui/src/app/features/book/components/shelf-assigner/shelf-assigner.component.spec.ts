import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {ShelfAssignerComponent} from './shelf-assigner.component';
import {BookService} from '../../service/book.service';
import {ShelfService} from '../../service/shelf.service';
import {BookDialogHelperService} from '../book-browser/book-dialog-helper.service';
import {LoadingService} from '../../../../core/services/loading.service';
import {commonComponentTestProviders} from '../../../../../testing';
import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {createMockBookService} from '../../../../../testing/book-component-mocks';
import {createMockBook} from '../../../../../testing/factories';
import {of} from 'rxjs';

describe('ShelfAssignerComponent', () => {
  let fixture: ComponentFixture<ShelfAssignerComponent>;
  let component: ShelfAssignerComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders.filter(p => p.provide !== TranslocoService),
        {provide: DynamicDialogRef, useValue: {close: vi.fn()}},
        {
          provide: DynamicDialogConfig,
          useValue: {
            data: {
              book: createMockBook(),
              bookIds: new Set<number>(),
              isMultiBooks: false
            }
          }
        },
        {provide: BookService, useValue: createMockBookService({updateBookShelves: vi.fn(() => of({}))})},
        {
          provide: ShelfService,
          useValue: {
            shelfState$: of({loaded: true, shelves: [], error: null}),
            getShelvesFromState: vi.fn(() => []),
            getShelfById: vi.fn(),
            reloadShelves: vi.fn()
          }
        },
        {
          provide: BookDialogHelperService,
          useValue: {
            openShelfCreatorDialog: vi.fn(() => ({onClose: of(true)})),
            openShelfAssignerDialog: vi.fn(),
            openLockUnlockMetadataDialog: vi.fn(),
            openBulkMetadataEditDialog: vi.fn(() => ({onClose: of(null)})),
            openMultibookMetadataEditorDialog: vi.fn(() => ({onClose: of(null)})),
            openMetadataRefreshDialog: vi.fn(),
            openFileMoverDialog: vi.fn(),
            openCustomSendDialog: vi.fn(),
            openBookDetailsDialog: vi.fn()
          }
        },
        {
          provide: LoadingService,
          useValue: {
            show: vi.fn(() => document.createElement('div')),
            hide: vi.fn()
          }
        }
      ]
    });

    fixture = TestBed.createComponent(ShelfAssignerComponent);
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
