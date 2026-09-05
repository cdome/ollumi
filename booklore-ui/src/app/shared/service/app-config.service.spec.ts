import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {DOCUMENT, PLATFORM_ID} from '@angular/common';
import {AppConfigService} from './app-config.service';
import {AppState} from '../model/app-state.model';

describe('AppConfigService', () => {
  let service: AppConfigService;
  let store: Record<string, string>;
  let storage: {getItem: ReturnType<typeof vi.fn>; setItem: ReturnType<typeof vi.fn>; removeItem: ReturnType<typeof vi.fn>};
  let classListAdd: ReturnType<typeof vi.fn>;

  function createDocumentMock() {
    classListAdd = vi.fn();
    return {
      documentElement: {
        classList: {
          add: classListAdd,
          remove: vi.fn()
        }
      }
    };
  }

  function configureService() {
    TestBed.configureTestingModule({
      providers: [
        AppConfigService,
        {provide: DOCUMENT, useValue: createDocumentMock()},
        {provide: PLATFORM_ID, useValue: 'browser'}
      ]
    });
  }

  beforeEach(() => {
    store = {};
    storage = {
      getItem: vi.fn((key: string) => store[key] ?? null),
      setItem: vi.fn((key: string, value: string) => { store[key] = value; }),
      removeItem: vi.fn((key: string) => { delete store[key]; })
    };
    vi.stubGlobal('localStorage', storage);

    configureService();
    service = TestBed.inject(AppConfigService);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('should be created with default theme state', () => {
    expect(service).toBeTruthy();
    expect(service.appState()).toEqual(expect.objectContaining<AppState>({
      preset: 'Aura',
      primary: 'green',
      surface: 'ash'
    }));
  });

  it('should add the dark mode class to the document element', () => {
    expect(classListAdd).toHaveBeenCalledWith('p-dark');
  });

  it('should load persisted state from localStorage', () => {
    store['appConfigState'] = JSON.stringify({preset: 'Noir', primary: 'emerald', surface: 'slate'});

    TestBed.resetTestingModule();
    configureService();
    const freshService = TestBed.inject(AppConfigService);

    expect(freshService.appState()).toEqual(expect.objectContaining<AppState>({
      preset: 'Noir',
      primary: 'emerald',
      surface: 'slate'
    }));
  });

  it('should build a preset extension for a non-noir primary color', () => {
    const preset = service.getPresetExt();

    expect(preset).toHaveProperty('semantic.primary');
    expect(preset).toHaveProperty('semantic.colorScheme.dark.primary.color');
    expect(preset).toHaveProperty('semantic.colorScheme.dark.highlight.background');
  });

  it('should build a noir preset extension', () => {
    service.appState.set({preset: 'Aura', primary: 'noir', surface: 'neutral'});

    const preset = service.getPresetExt();

    expect(preset).toHaveProperty('semantic.primary');
    expect(preset).toHaveProperty('semantic.colorScheme.dark.primary.color');
  });

  it('should expose surface palettes', () => {
    expect((service as any).surfaces.length).toBeGreaterThan(0);
    expect((service as any).getSurfacePalette('ash')).toBeTruthy();
    expect((service as any).getSurfacePalette('unknown')).toEqual({});
  });

  it('should apply the current preset without throwing', () => {
    expect(() => service.onPresetChange()).not.toThrow();
  });
});
