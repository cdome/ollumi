import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AsyncPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { MenuModule } from 'primeng/menu';
import { Popover } from 'primeng/popover';
import { Slider } from 'primeng/slider';

import { AppMenuComponent } from './app.menu.component';
import { VersionService } from '../../../service/version.service';
import { LocalStorageService } from '../../../service/local-storage.service';
import { createLayoutComponentTestProviders } from '../../../../../testing/layout-component-test-helper';

describe('AppMenuComponent', () => {
  let fixture: ComponentFixture<AppMenuComponent>;
  let component: AppMenuComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AppMenuComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: createLayoutComponentTestProviders(),
      schemas: [NO_ERRORS_SCHEMA]
    }).overrideComponent(AppMenuComponent, {
      set: {
        imports: [MenuModule, AsyncPipe, TranslocoDirective, Slider, FormsModule, Popover],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(AppMenuComponent);
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

  it('should load version information on init', () => {
    const versionService = TestBed.inject(VersionService) as any;
    expect(versionService.getVersion).toHaveBeenCalled();
    expect(component.versionInfo).toEqual({ current: 'v1.0.0', latest: 'v1.0.0' });
  });

  it('should initialize the sidebar width from local storage', () => {
    const localStorageService = TestBed.inject(LocalStorageService) as any;
    expect(localStorageService.get).toHaveBeenCalledWith('sidebarWidth');
    expect(component.sidebarWidth).toBe(225);
  });

  it('should produce a home menu observable', () => {
    expect(component.homeMenu$).toBeTruthy();
  });

  it('should calculate semantic version urls', () => {
    expect(component.getVersionUrl('v1.2.3')).toBe(
      'https://github.com/booklore-app/booklore/releases/tag/v1.2.3'
    );
  });

  it('should identify semantic versions', () => {
    expect(component.isSemanticVersion('v1.2.3')).toBe(true);
    expect(component.isSemanticVersion('abc123')).toBe(false);
  });
});
