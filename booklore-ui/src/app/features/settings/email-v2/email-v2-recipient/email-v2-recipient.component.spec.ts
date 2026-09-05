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
import {EmailV2RecipientComponent} from './email-v2-recipient.component';
import {commonComponentTestProviders} from '../../../../../testing';
import {EmailV2RecipientService} from './email-v2-recipient.service';
import {of} from 'rxjs';

const mockEmailV2RecipientService = {
  getRecipients: vi.fn(() => of([])),
  createRecipient: vi.fn(() => of({})),
  updateRecipient: vi.fn(() => of({})),
  deleteRecipient: vi.fn(() => of(undefined)),
  setDefaultRecipient: vi.fn(() => of(undefined))
};

describe('EmailV2RecipientComponent', () => {
  let fixture: ComponentFixture<EmailV2RecipientComponent>;
  let component: EmailV2RecipientComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders,
        {provide: EmailV2RecipientService, useValue: mockEmailV2RecipientService}
      ]
    }).overrideComponent(EmailV2RecipientComponent, {
      set: {
        imports: [CommonModule, FormsModule, ReactiveFormsModule, TranslocoPipe, TranslocoDirective, Button, Checkbox, DatePicker, InputText, MultiSelect, Password, RadioButton, Select, Slider, SplitButton, ToggleSwitch],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(EmailV2RecipientComponent);
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
