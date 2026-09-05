import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {CommonModule} from '@angular/common';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoDirective, TranslocoPipe} from '@jsverse/transloco';
import {BookdropFileMetadataPickerComponent} from './bookdrop-file-metadata-picker.component';
import {commonComponentTestProviders} from '../../../../../testing';

describe('BookdropFileMetadataPickerComponent', () => {
  let fixture: ComponentFixture<BookdropFileMetadataPickerComponent>;
  let component: BookdropFileMetadataPickerComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders
      ]
    }).overrideComponent(BookdropFileMetadataPickerComponent, {
      set: {
        template: '',
        imports: [CommonModule, FormsModule, ReactiveFormsModule, TranslocoDirective, TranslocoPipe],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(BookdropFileMetadataPickerComponent);
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

  it('should expose metadata field groups', () => {
    expect(component.metadataFieldsTop).toBeTruthy();
    expect(component.metadataChips).toBeTruthy();
    expect(component.metadataDescription).toBeTruthy();
  });
});
