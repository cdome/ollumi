import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {ShelfCreatorComponent} from './shelf-creator.component';
import {ShelfService} from '../../service/shelf.service';
import {commonComponentTestProviders} from '../../../../../testing';
import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {of} from 'rxjs';

describe('ShelfCreatorComponent', () => {
  let fixture: ComponentFixture<ShelfCreatorComponent>;
  let component: ShelfCreatorComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders.filter(p => p.provide !== TranslocoService),
        {provide: DynamicDialogRef, useValue: {close: vi.fn()}},
        {
          provide: ShelfService,
          useValue: {
            shelfState$: of({loaded: true, shelves: [], error: null}),
            createShelf: vi.fn(() => of(void 0)),
            getShelvesFromState: vi.fn(() => []),
            getShelfById: vi.fn(),
            reloadShelves: vi.fn()
          }
        }
      ]
    });

    fixture = TestBed.createComponent(ShelfCreatorComponent);
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
