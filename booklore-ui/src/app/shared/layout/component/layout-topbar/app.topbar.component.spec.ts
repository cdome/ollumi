import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AsyncPipe, NgClass, NgStyle } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Button } from 'primeng/button';
import { Divider } from 'primeng/divider';
import { InputTextModule } from 'primeng/inputtext';
import { Menu } from 'primeng/menu';
import { Popover } from 'primeng/popover';
import { StyleClass } from 'primeng/styleclass';
import { TooltipModule } from 'primeng/tooltip';

import { AppTopBarComponent } from './app.topbar.component';
import { LayoutService } from '../layout-main/service/app.layout.service';
import { AuthService } from '../../../service/auth.service';
import { createLayoutComponentTestProviders } from '../../../../../testing/layout-component-test-helper';

describe('AppTopBarComponent', () => {
  let fixture: ComponentFixture<AppTopBarComponent>;
  let component: AppTopBarComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AppTopBarComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: createLayoutComponentTestProviders(),
      schemas: [NO_ERRORS_SCHEMA]
    }).overrideComponent(AppTopBarComponent, {
      set: {
        imports: [
          RouterLink,
          TooltipModule,
          FormsModule,
          InputTextModule,
          Button,
          StyleClass,
          NgClass,
          Divider,
          AsyncPipe,
          Popover,
          Menu,
          NgStyle,
          TranslocoDirective,
        ],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(AppTopBarComponent);
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

  it('should render the topbar container', () => {
    expect(fixture.nativeElement.querySelector('.layout-topbar')).toBeTruthy();
  });

  it('should toggle menu visibility and notify the layout service', () => {
    const layoutService = TestBed.inject(LayoutService) as ReturnType<typeof vi.fn> & LayoutService;
    component.isMenuVisible = true;

    component.toggleMenu();

    expect(component.isMenuVisible).toBe(false);
    expect(layoutService.onMenuToggle).toHaveBeenCalled();
  });

  it('should navigate to settings', () => {
    const router = TestBed.inject(Router) as ReturnType<typeof vi.fn> & Router;
    component.navigateToSettings();
    expect(router.navigate).toHaveBeenCalledWith(['/settings']);
  });

  it('should logout via the auth service', () => {
    const authService = TestBed.inject(AuthService) as ReturnType<typeof vi.fn> & AuthService;
    component.logout();
    expect(authService.logout).toHaveBeenCalled();
  });

  it('should build language menu items for every available language', () => {
    expect(component.langMenuItems.length).toBeGreaterThan(0);
    expect(component.langMenuItems[0]).toEqual(expect.objectContaining({ label: expect.any(String), command: expect.any(Function) }));
  });
});
