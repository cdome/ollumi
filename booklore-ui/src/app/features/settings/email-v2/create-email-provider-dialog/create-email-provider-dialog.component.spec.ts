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
import {CreateEmailProviderDialogComponent} from './create-email-provider-dialog.component';
import {commonComponentTestProviders} from '../../../../../testing';
import {EmailV2ProviderService} from '../email-v2-provider/email-v2-provider.service';
import {of} from 'rxjs';

const mockEmailV2ProviderService = {
  getEmailProviders: vi.fn(() => of([])),
  createEmailProvider: vi.fn(() => of({})),
  updateProvider: vi.fn(() => of({})),
  deleteProvider: vi.fn(() => of(undefined)),
  setDefaultProvider: vi.fn(() => of(undefined))
};

describe('CreateEmailProviderDialogComponent', () => {
  let fixture: ComponentFixture<CreateEmailProviderDialogComponent>;
  let component: CreateEmailProviderDialogComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders,
        {provide: EmailV2ProviderService, useValue: mockEmailV2ProviderService}
      ]
    }).overrideComponent(CreateEmailProviderDialogComponent, {
      set: {
        imports: [CommonModule, FormsModule, ReactiveFormsModule, TranslocoPipe, TranslocoDirective, Button, Checkbox, DatePicker, InputText, MultiSelect, Password, RadioButton, Select, Slider, SplitButton, ToggleSwitch],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(CreateEmailProviderDialogComponent);
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
