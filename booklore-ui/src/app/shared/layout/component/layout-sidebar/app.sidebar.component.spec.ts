import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';

import { AppSidebarComponent } from './app.sidebar.component';
import { LayoutService } from '../layout-main/service/app.layout.service';
import { createLayoutComponentTestProviders } from '../../../../../testing/layout-component-test-helper';

describe('AppSidebarComponent', () => {
  let fixture: ComponentFixture<AppSidebarComponent>;
  let component: AppSidebarComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AppSidebarComponent],
      providers: createLayoutComponentTestProviders(),
      schemas: [NO_ERRORS_SCHEMA]
    }).overrideComponent(AppSidebarComponent, {
      set: {
        imports: [],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(AppSidebarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture?.destroy();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should expose the layout service', () => {
    expect(component.layoutService).toBe(TestBed.inject(LayoutService));
  });

  it('should render the sidebar element', () => {
    expect(fixture.nativeElement.querySelector('app-menu')).toBeTruthy();
  });
});
