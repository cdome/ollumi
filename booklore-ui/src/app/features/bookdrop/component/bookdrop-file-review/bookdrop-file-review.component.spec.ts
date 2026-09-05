import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {CommonModule} from '@angular/common';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';
import {BookdropFileReviewComponent} from './bookdrop-file-review.component';
import {commonComponentTestProviders} from '../../../../../testing';

describe('BookdropFileReviewComponent', () => {
  let fixture: ComponentFixture<BookdropFileReviewComponent>;
  let component: BookdropFileReviewComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders
      ]
    }).overrideComponent(BookdropFileReviewComponent, {
      set: {
        imports: [CommonModule, FormsModule, ReactiveFormsModule, TranslocoPipe],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(BookdropFileReviewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize file UI list from pending files', () => {
    expect(component.bookdropFileUis).toEqual([]);
  });
});
