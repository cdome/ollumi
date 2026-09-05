import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {CommonModule} from '@angular/common';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoDirective, TranslocoPipe} from '@jsverse/transloco';
import {DashboardScrollerComponent} from './dashboard-scroller.component';
import {commonComponentTestProviders} from '../../../../../testing';

describe('DashboardScrollerComponent', () => {
  let fixture: ComponentFixture<DashboardScrollerComponent>;
  let component: DashboardScrollerComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders
      ]
    }).overrideComponent(DashboardScrollerComponent, {
      set: {
        imports: [CommonModule, FormsModule, ReactiveFormsModule, TranslocoDirective, TranslocoPipe],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(DashboardScrollerComponent);
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

  it('should render the scroller container', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.dashboard-scroller-container')).toBeTruthy();
  });

  it('should force ebook mode for the last read scroller', () => {
    component.bookListType = 'lastRead' as any;
    expect(component.forceEbookMode).toBe(true);
  });
});
