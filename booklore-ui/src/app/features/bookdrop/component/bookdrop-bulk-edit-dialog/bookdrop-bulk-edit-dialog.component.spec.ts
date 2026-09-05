import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {CommonModule} from '@angular/common';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoDirective, TranslocoPipe} from '@jsverse/transloco';
import {BookdropBulkEditDialogComponent} from './bookdrop-bulk-edit-dialog.component';
import {commonComponentTestProviders} from '../../../../../testing';

describe('BookdropBulkEditDialogComponent', () => {
  let fixture: ComponentFixture<BookdropBulkEditDialogComponent>;
  let component: BookdropBulkEditDialogComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders
      ]
    }).overrideComponent(BookdropBulkEditDialogComponent, {
      set: {
        template: '',
        imports: [CommonModule, FormsModule, ReactiveFormsModule, TranslocoDirective, TranslocoPipe],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(BookdropBulkEditDialogComponent);
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

  it('should start with no enabled fields', () => {
    expect(component.hasEnabledFields).toBe(false);
  });

  it('should toggle a field on and off', () => {
    component.toggleField('seriesName');
    expect(component.isFieldEnabled('seriesName')).toBe(true);
    component.toggleField('seriesName');
    expect(component.isFieldEnabled('seriesName')).toBe(false);
  });
});
