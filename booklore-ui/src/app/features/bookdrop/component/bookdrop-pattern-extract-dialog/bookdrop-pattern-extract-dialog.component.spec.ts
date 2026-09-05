import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {CommonModule} from '@angular/common';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoDirective, TranslocoPipe} from '@jsverse/transloco';
import {BookdropPatternExtractDialogComponent} from './bookdrop-pattern-extract-dialog.component';
import {commonComponentTestProviders} from '../../../../../testing';

describe('BookdropPatternExtractDialogComponent', () => {
  let fixture: ComponentFixture<BookdropPatternExtractDialogComponent>;
  let component: BookdropPatternExtractDialogComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders
      ]
    }).overrideComponent(BookdropPatternExtractDialogComponent, {
      set: {
        imports: [CommonModule, FormsModule, ReactiveFormsModule, TranslocoDirective, TranslocoPipe],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(BookdropPatternExtractDialogComponent);
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

  it('should initialize with zero file count', () => {
    expect(component.fileCount).toBe(0);
  });

  it('should recognize a valid pattern with a placeholder', () => {
    component.patternForm.get('pattern')?.setValue('{Title}');
    expect(component.hasValidPattern).toBe(true);
  });

  it('should reject an empty pattern', () => {
    component.patternForm.get('pattern')?.setValue('');
    expect(component.hasValidPattern).toBe(false);
  });
});
