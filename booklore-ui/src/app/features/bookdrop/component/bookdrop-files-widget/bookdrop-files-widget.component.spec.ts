import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {CommonModule} from '@angular/common';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoDirective, TranslocoPipe} from '@jsverse/transloco';
import {BookdropFilesWidgetComponent} from './bookdrop-files-widget.component';
import {commonComponentTestProviders} from '../../../../../testing';

describe('BookdropFilesWidgetComponent', () => {
  let fixture: ComponentFixture<BookdropFilesWidgetComponent>;
  let component: BookdropFilesWidgetComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders
      ]
    }).overrideComponent(BookdropFilesWidgetComponent, {
      set: {
        imports: [CommonModule, TranslocoDirective, TranslocoPipe],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(BookdropFilesWidgetComponent);
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

  it('should render the staging widget', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.staging-border')).toBeTruthy();
  });
});
