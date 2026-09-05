import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {CommonModule} from '@angular/common';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoDirective, TranslocoPipe} from '@jsverse/transloco';
import {DynamicDialogConfig} from 'primeng/dynamicdialog';
import {BookdropFinalizeResultDialogComponent} from './bookdrop-finalize-result-dialog.component';
import {commonComponentTestProviders} from '../../../../../testing';

describe('BookdropFinalizeResultDialogComponent', () => {
  let fixture: ComponentFixture<BookdropFinalizeResultDialogComponent>;
  let component: BookdropFinalizeResultDialogComponent;

  const mockResult = {
    totalFiles: 3,
    successfullyImported: 2,
    failed: 1,
    processedAt: '2024-01-01T00:00:00Z',
    results: []
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders,
        {provide: DynamicDialogConfig, useValue: {data: {result: mockResult}}}
      ]
    }).overrideComponent(BookdropFinalizeResultDialogComponent, {
      set: {
        imports: [CommonModule, TranslocoDirective, TranslocoPipe],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(BookdropFinalizeResultDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture?.destroy();
    TestBed.resetTestingModule();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display the result totals', () => {
    expect(component.result.totalFiles).toBe(3);
    expect(component.result.successfullyImported).toBe(2);
    expect(component.result.failed).toBe(1);
  });
});
