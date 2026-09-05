import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {ShelfEditDialogComponent} from './shelf-edit-dialog.component';
import {ShelfService} from '../../service/shelf.service';
import {commonComponentTestProviders} from '../../../../../testing';
import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {createMockShelf} from '../../../../../testing/factories';
import {of} from 'rxjs';

describe('ShelfEditDialogComponent', () => {
  let fixture: ComponentFixture<ShelfEditDialogComponent>;
  let component: ShelfEditDialogComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders.filter(p => p.provide !== TranslocoService),
        {provide: DynamicDialogRef, useValue: {close: vi.fn()}},
        {provide: DynamicDialogConfig, useValue: {data: {shelfId: 1}}},
        {
          provide: ShelfService,
          useValue: {
            shelfState$: of({loaded: true, shelves: [createMockShelf()], error: null}),
            getShelfById: vi.fn(() => createMockShelf()),
            updateShelf: vi.fn(() => of(void 0)),
            getShelvesFromState: vi.fn(() => []),
            reloadShelves: vi.fn()
          }
        }
      ]
    });

    fixture = TestBed.createComponent(ShelfEditDialogComponent);
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
