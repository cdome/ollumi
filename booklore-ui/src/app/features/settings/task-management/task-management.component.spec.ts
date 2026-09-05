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
import {TaskManagementComponent} from './task-management.component';
import {commonComponentTestProviders} from '../../../../testing';
import {TaskService} from './task.service';
import {BehaviorSubject, of} from 'rxjs';

const mockTaskService = {
  getAvailableTasks: vi.fn(() => of([])),
  getLatestTasksForEachType: vi.fn(() => of({taskHistories: []})),
  startTask: vi.fn(() => of({status: 'COMPLETED'})),
  cancelTask: vi.fn(() => of({cancelled: true, message: ''})),
  updateCronConfig: vi.fn(() => of({})),
  taskProgress$: new BehaviorSubject(null).asObservable()
};

describe('TaskManagementComponent', () => {
  let fixture: ComponentFixture<TaskManagementComponent>;
  let component: TaskManagementComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders,
        {provide: TaskService, useValue: mockTaskService}
      ]
    }).overrideComponent(TaskManagementComponent, {
      set: {
        imports: [CommonModule, FormsModule, ReactiveFormsModule, TranslocoPipe, TranslocoDirective, Button, Checkbox, DatePicker, InputText, MultiSelect, Password, RadioButton, Select, Slider, SplitButton, ToggleSwitch],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(TaskManagementComponent);
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
