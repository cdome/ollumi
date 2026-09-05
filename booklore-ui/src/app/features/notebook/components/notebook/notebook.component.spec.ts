import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {CommonModule} from '@angular/common';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';
import {NotebookComponent} from './notebook.component';
import {commonComponentTestProviders} from '../../../../../testing';

describe('NotebookComponent', () => {
  let fixture: ComponentFixture<NotebookComponent>;
  let component: NotebookComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders
      ]
    }).overrideComponent(NotebookComponent, {
      set: {
        imports: [CommonModule, FormsModule, ReactiveFormsModule, TranslocoPipe],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(NotebookComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should default to showing all entry types', () => {
    expect(component.showHighlights).toBe(true);
    expect(component.showNotes).toBe(true);
    expect(component.showBookmarks).toBe(true);
  });
});
