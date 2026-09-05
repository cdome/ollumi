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
import {AuditLogsComponent} from './audit-logs.component';
import {commonComponentTestProviders} from '../../../../testing';
import {ActivatedRoute} from '@angular/router';
import {AuditLogService} from './audit-log.service';
import {of} from 'rxjs';

const mockAuditLogService = {
  getAuditLogs: vi.fn(() => of({content: [], page: {totalElements: 0, totalPages: 0, number: 0, size: 0}})),
  getDistinctUsernames: vi.fn(() => of([]))
};

describe('AuditLogsComponent', () => {
  let fixture: ComponentFixture<AuditLogsComponent>;
  let component: AuditLogsComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders,
        {
  provide: ActivatedRoute,
  useValue: {
    params: of({}),
    queryParams: of({}),
    snapshot: {
      paramMap: {get: vi.fn(() => null)},
      queryParamMap: {get: vi.fn(() => null)},
      queryParams: {}
    }
  }
},
        {provide: AuditLogService, useValue: mockAuditLogService}
      ]
    }).overrideComponent(AuditLogsComponent, {
      set: {
        imports: [CommonModule, FormsModule, ReactiveFormsModule, TranslocoPipe, TranslocoDirective, Button, Checkbox, DatePicker, InputText, MultiSelect, Password, RadioButton, Select, Slider, SplitButton, ToggleSwitch],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(AuditLogsComponent);
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
