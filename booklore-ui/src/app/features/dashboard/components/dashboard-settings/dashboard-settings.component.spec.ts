import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {CommonModule} from '@angular/common';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoDirective, TranslocoPipe} from '@jsverse/transloco';
import {DashboardSettingsComponent} from './dashboard-settings.component';
import {commonComponentTestProviders} from '../../../../../testing';

describe('DashboardSettingsComponent', () => {
  let fixture: ComponentFixture<DashboardSettingsComponent>;
  let component: DashboardSettingsComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders
      ]
    }).overrideComponent(DashboardSettingsComponent, {
      set: {
        template: '',
        imports: [CommonModule, FormsModule, ReactiveFormsModule, TranslocoDirective, TranslocoPipe],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(DashboardSettingsComponent);
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

  it('should load the default dashboard config', () => {
    expect(component.config).toBeTruthy();
    expect(component.config.scrollers.length).toBeGreaterThan(0);
  });

  it('should add a scroller when below the max limit', () => {
    const initialCount = component.config.scrollers.length;
    component.addScroller();
    expect(component.config.scrollers.length).toBe(initialCount + 1);
  });
});
