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
import {LibraryMetadataSettingsComponent} from './library-metadata-settings.component';
import {commonComponentTestProviders} from '../../../../testing';
import {SidecarService} from '../../metadata/service/sidecar.service';
import {of} from 'rxjs';

const mockSidecarService = {
  bulkExport: vi.fn(() => of({exported: 0})),
  bulkImport: vi.fn(() => of({imported: 0}))
};

describe('LibraryMetadataSettingsComponent', () => {
  let fixture: ComponentFixture<LibraryMetadataSettingsComponent>;
  let component: LibraryMetadataSettingsComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders,
        {provide: SidecarService, useValue: mockSidecarService}
      ]
    }).overrideComponent(LibraryMetadataSettingsComponent, {
      set: {
        imports: [CommonModule, FormsModule, ReactiveFormsModule, TranslocoPipe, TranslocoDirective, Button, Checkbox, DatePicker, InputText, MultiSelect, Password, RadioButton, Select, Slider, SplitButton, ToggleSwitch],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(LibraryMetadataSettingsComponent);
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
