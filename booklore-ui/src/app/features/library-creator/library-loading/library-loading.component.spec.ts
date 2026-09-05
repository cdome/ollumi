import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {CommonModule} from '@angular/common';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoDirective, TranslocoPipe} from '@jsverse/transloco';
import {LibraryLoadingComponent} from './library-loading.component';
import {commonComponentTestProviders} from '../../../../testing';

describe('LibraryLoadingComponent', () => {
  let fixture: ComponentFixture<LibraryLoadingComponent>;
  let component: LibraryLoadingComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders
      ]
    }).overrideComponent(LibraryLoadingComponent, {
      set: {
        imports: [CommonModule, TranslocoDirective, TranslocoPipe],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(LibraryLoadingComponent);
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

  it('should calculate percentage correctly', () => {
    component.updateProgress('Test Book', 25, 100);
    expect(component.percentage).toBe(25);
  });

  it('should mark complete when current reaches total', () => {
    component.updateProgress('Test Book', 10, 10);
    expect(component.isComplete).toBe(true);
  });
});
