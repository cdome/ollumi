import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';

import { ThemeConfiguratorComponent } from './theme-configurator.component';
import { AppConfigService } from '../../../service/app-config.service';
import { FaviconService } from './favicon-service';

describe('ThemeConfiguratorComponent', () => {
  let fixture: ComponentFixture<ThemeConfiguratorComponent>;
  let component: ThemeConfiguratorComponent;

  const appState = signal({ primary: 'green', surface: 'ash' });

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ThemeConfiguratorComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        {
          provide: AppConfigService,
          useValue: {
            appState,
            surfaces: [{ name: 'ash', palette: { '500': '#999999' } }],
          }
        },
        { provide: FaviconService, useValue: { updateFavicon: vi.fn() } },
      ]
    });

    fixture = TestBed.createComponent(ThemeConfiguratorComponent);
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

  it('should expose the reactive color list', () => {
    expect(component.primaryColors().length).toBeGreaterThan(0);
  });

  it('should read the current primary and surface colors from the config service', () => {
    expect(component.selectedPrimaryColor()).toBe('green');
    expect(component.selectedSurfaceColor()).toBe('ash');
  });

  it('should update the app state when a color is selected', () => {
    const configService = TestBed.inject(AppConfigService) as any;
    const event = new MouseEvent('click');
    vi.spyOn(event, 'stopPropagation');

    component.updateColors(event, 'primary', { name: 'blue', palette: {} });

    expect(configService.appState()).toEqual(expect.objectContaining({ primary: 'blue' }));
    expect(event.stopPropagation).toHaveBeenCalled();
  });
});
