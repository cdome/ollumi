import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {CommonModule} from '@angular/common';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoDirective, TranslocoPipe} from '@jsverse/transloco';
import {LibraryCreatorComponent} from './library-creator.component';
import {commonComponentTestProviders} from '../../../testing';

describe('LibraryCreatorComponent', () => {
  let fixture: ComponentFixture<LibraryCreatorComponent>;
  let component: LibraryCreatorComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders
      ]
    }).overrideComponent(LibraryCreatorComponent, {
      set: {
        template: '',
        imports: [CommonModule, FormsModule, ReactiveFormsModule, TranslocoDirective, TranslocoPipe],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(LibraryCreatorComponent);
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

  it('should initialize with default metadata source and organization mode', () => {
    expect(component.metadataSource).toBe('EMBEDDED');
    expect(component.organizationMode).toBe('BOOK_PER_FILE');
  });

  it('should invalidate an empty library name', () => {
    component.chosenLibraryName = '   ';
    expect(component.isLibraryDetailsValid()).toBe(false);
  });
});
