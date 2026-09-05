import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {Subscription} from 'rxjs';
import {LayoutService, type AppConfig} from './app.layout.service';

function createThemeLinkMock() {
  const insertBefore = vi.fn();
  const parentNode = {insertBefore};
  const remove = vi.fn();
  const setAttribute = vi.fn();
  const cloneSetAttribute = vi.fn();
  const cloneLink = {
    setAttribute: cloneSetAttribute,
    addEventListener: vi.fn((event: string, handler: () => void) => handler())
  };
  const link = {
    getAttribute: vi.fn(),
    setAttribute,
    cloneNode: vi.fn(() => cloneLink),
    addEventListener: vi.fn((event: string, handler: () => void) => handler()),
    remove,
    parentNode
  } as unknown as HTMLLinkElement;
  return {link, cloneLink, parentNode, cloneSetAttribute};
}

describe('LayoutService', () => {
  let service: LayoutService;
  let subscriptions: Subscription[];
  let originalInnerWidth: number;

  beforeEach(() => {
    subscriptions = [];
    originalInnerWidth = window.innerWidth;
    window.innerWidth = 1024;
    document.documentElement.style.fontSize = '';

    TestBed.configureTestingModule({
      providers: [LayoutService]
    });

    service = TestBed.inject(LayoutService);
    flushEffects();
  });

  afterEach(() => {
    subscriptions.forEach(sub => sub.unsubscribe());
    window.innerWidth = originalInnerWidth;
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  function flushEffects() {
    TestBed.flushEffects();
  }

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should expose the default configuration', () => {
    expect(service.config()).toEqual(
      expect.objectContaining<AppConfig>({
        ripple: false,
        inputStyle: 'outlined',
        menuMode: 'static',
        colorScheme: 'light',
        theme: 'lara-light-indigo',
        scale: 14
      })
    );
  });

  it('should apply the initial scale to the document element', () => {
    expect(document.documentElement.style.fontSize).toBe('14px');
  });

  it('should detect desktop viewports', () => {
    window.innerWidth = 1024;
    expect(service.isDesktop()).toBe(true);

    window.innerWidth = 991;
    expect(service.isDesktop()).toBe(false);
  });

  it('should report overlay mode based on config', () => {
    expect(service.isOverlay()).toBe(false);

    service.config.set({...service.config(), menuMode: 'overlay'});
    flushEffects();

    expect(service.isOverlay()).toBe(true);
  });

  it('should toggle the static menu state on desktop', () => {
    expect(service.state.staticMenuDesktopInactive).toBe(false);

    service.onMenuToggle();
    expect(service.state.staticMenuDesktopInactive).toBe(true);

    service.onMenuToggle();
    expect(service.state.staticMenuDesktopInactive).toBe(false);
  });

  it('should toggle the mobile menu state on narrow viewports', () => {
    window.innerWidth = 600;

    service.onMenuToggle();
    expect(service.state.staticMenuMobileActive).toBe(true);

    service.onMenuToggle();
    expect(service.state.staticMenuMobileActive).toBe(false);
  });

  it('should emit when overlay menu becomes active', () => {
    const emissions: unknown[] = [];
    const sub = service.overlayOpen$.subscribe(() => emissions.push(null));
    subscriptions.push(sub);

    service.config.set({...service.config(), menuMode: 'overlay'});
    flushEffects();
    service.onMenuToggle();

    expect(emissions.length).toBe(1);
  });

  it('should emit when mobile menu becomes active', () => {
    window.innerWidth = 600;
    const emissions: unknown[] = [];
    const sub = service.overlayOpen$.subscribe(() => emissions.push(null));
    subscriptions.push(sub);

    service.onMenuToggle();

    expect(emissions.length).toBe(1);
  });

  it('should detect theme or color-scheme changes', () => {
    expect(service.updateStyle(service.config())).toBe(false);

    const updated: AppConfig = {...service.config(), theme: 'md-light-indigo'};
    expect(service.updateStyle(updated)).toBe(true);

    const colorChanged: AppConfig = {...service.config(), colorScheme: 'dark'};
    expect(service.updateStyle(colorChanged)).toBe(true);
  });

  it('should apply a new theme by replacing the theme link', () => {
    const themeLink = createThemeLinkMock();
    themeLink.link.getAttribute.mockReturnValue('/assets/theme/lara-light-indigo/theme-light.css');

    vi.spyOn(document, 'getElementById').mockImplementation((id: string) =>
      id === 'theme-css' ? themeLink.link : null
    );

    service.config.set({...service.config(), theme: 'md-light-indigo'});
    flushEffects();

    expect(themeLink.cloneSetAttribute).toHaveBeenCalledWith(
      'href',
      expect.stringContaining('md-light-indigo')
    );
    expect(themeLink.cloneSetAttribute).toHaveBeenCalledWith('id', 'theme-css-clone');
    expect(themeLink.parentNode.insertBefore).toHaveBeenCalled();
    expect(themeLink.link.remove).toHaveBeenCalled();
  });

  it('should update the internal config reference on config changes', () => {
    const updated: AppConfig = {...service.config(), scale: 16};
    service.config.set(updated);
    flushEffects();

    expect(service._config.scale).toBe(16);
  });

  it('should update the document font size via changeScale', () => {
    service.changeScale(18);
    expect(document.documentElement.style.fontSize).toBe('18px');
  });
});
