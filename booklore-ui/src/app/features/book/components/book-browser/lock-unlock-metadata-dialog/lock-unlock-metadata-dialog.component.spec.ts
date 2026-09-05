import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {LockUnlockMetadataDialogComponent} from './lock-unlock-metadata-dialog.component';
import {BookMetadataManageService} from '../../../service/book-metadata-manage.service';
import {LoadingService} from '../../../../../core/services/loading.service';
import {commonComponentTestProviders} from '../../../../../../testing';
import {getTranslocoModule} from '../../../../../core/testing/transloco-testing';
import {of} from 'rxjs';

describe('LockUnlockMetadataDialogComponent', () => {
  let fixture: ComponentFixture<LockUnlockMetadataDialogComponent>;
  let component: LockUnlockMetadataDialogComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders.filter(p => p.provide !== TranslocoService),
        {provide: DynamicDialogRef, useValue: {close: vi.fn()}},
        {provide: DynamicDialogConfig, useValue: {data: {bookIds: new Set<number>([1])}}},
        {
          provide: BookMetadataManageService,
          useValue: {
            toggleFieldLocks: vi.fn(() => of(void 0))
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

    fixture = TestBed.createComponent(LockUnlockMetadataDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should create and render without error', () => {
    expect(component).toBeTruthy();
    expect(component.bookIds.size).toBe(1);
    expect(fixture.nativeElement).toBeTruthy();
  });
});
