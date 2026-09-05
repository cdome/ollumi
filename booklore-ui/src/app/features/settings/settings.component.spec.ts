import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {CommonModule} from '@angular/common';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';
import {SettingsComponent} from './settings.component';
import {commonComponentTestProviders} from '../../../testing';
import {ActivatedRoute} from '@angular/router';
import {BehaviorSubject, of} from 'rxjs';

describe('SettingsComponent', () => {
  let fixture: ComponentFixture<SettingsComponent>;
  let component: SettingsComponent;

  const queryParamsSubject = new BehaviorSubject<{ tab?: string }>({});

  beforeEach(() => {
    TestBed.configureTestingModule({
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders,
        {
          provide: ActivatedRoute,
          useValue: {
            params: of({}),
            queryParams: queryParamsSubject.asObservable(),
            snapshot: {
              paramMap: {get: vi.fn(() => null)},
              queryParamMap: {get: vi.fn(() => null)}
            }
          }
        }
      ]
    }).overrideComponent(SettingsComponent, {
      set: {
        imports: [CommonModule, FormsModule, ReactiveFormsModule, TranslocoPipe],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(SettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    queryParamsSubject.next({});
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should default to reader settings tab', () => {
    expect(component.activeTab).toBe('reader');
  });
});
