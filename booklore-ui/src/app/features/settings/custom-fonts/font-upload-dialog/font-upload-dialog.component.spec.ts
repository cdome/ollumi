import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {CommonModule} from '@angular/common';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoDirective, TranslocoPipe} from '@jsverse/transloco';
import {Button} from 'primeng/button';
import {Checkbox} from 'primeng/checkbox';
import {DatePicker} from 'primeng/datepicker';
import {InputText} from 'primeng/inputtext';
import {MultiSelect} from 'primeng/multiselect';
import {Password} from 'primeng/password';
import {RadioButton} from 'primeng/radiobutton';
import {Select} from 'primeng/select';
import {Slider} from 'primeng/slider';
import {SplitButton} from 'primeng/splitbutton';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {FontUploadDialogComponent} from './font-upload-dialog.component';
import {commonComponentTestProviders} from '../../../../../testing';
import {CustomFontService} from '../../../../shared/service/custom-font.service';
import {of} from 'rxjs';

const mockCustomFontService = {
  getUserFonts: vi.fn(() => of([])),
  loadAllFonts: vi.fn(() => Promise.resolve()),
  uploadFont: vi.fn(() => of({id: 1, fontName: 'Test Font'})),
  deleteFont: vi.fn(() => of(undefined))
};

describe('FontUploadDialogComponent', () => {
  let fixture: ComponentFixture<FontUploadDialogComponent>;
  let component: FontUploadDialogComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders,
        {provide: CustomFontService, useValue: mockCustomFontService}
      ]
    }).overrideComponent(FontUploadDialogComponent, {
      set: {
        imports: [CommonModule, FormsModule, ReactiveFormsModule, TranslocoPipe, TranslocoDirective, Button, Checkbox, DatePicker, InputText, MultiSelect, Password, RadioButton, Select, Slider, SplitButton, ToggleSwitch],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(FontUploadDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
