import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {IconDisplayComponent} from './icon-display.component';
import {IconCacheService} from '../../services/icon-cache.service';
import {IconService} from '../../services/icon.service';
import {IconSelection} from '../../service/icon-picker.service';
import {of} from 'rxjs';

describe('IconDisplayComponent', () => {
  let fixture: ComponentFixture<IconDisplayComponent>;
  let component: IconDisplayComponent;

  const iconCacheServiceMock = {
    getCachedSanitized: vi.fn(() => null),
    cacheIcon: vi.fn()
  };

  const iconServiceMock = {
    getSanitizedSvgContent: vi.fn(() => of(null))
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [IconDisplayComponent],
      providers: [
        {provide: IconCacheService, useValue: iconCacheServiceMock},
        {provide: IconService, useValue: iconServiceMock}
      ]
    });

    fixture = TestBed.createComponent(IconDisplayComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    fixture.destroy();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('should create without an icon', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.svg-icon-inline')).toBeFalsy();
  });

  it('should render a PrimeNG icon', () => {
    fixture.componentRef.setInput('icon', {type: 'PRIME_NG', value: 'pi pi-check'} as IconSelection);
    fixture.detectChanges();

    const iconElement = fixture.nativeElement.querySelector('i');
    expect(iconElement).toBeTruthy();
    expect(iconElement.classList.contains('pi-check')).toBe(true);
  });

  it('should render a cached SVG icon without fetching it again', () => {
    const safeSvg = {} as any;
    iconCacheServiceMock.getCachedSanitized.mockReturnValue(safeSvg);

    fixture.componentRef.setInput('icon', {type: 'CUSTOM_SVG', value: 'custom-icon'} as IconSelection);
    fixture.detectChanges();

    expect(iconServiceMock.getSanitizedSvgContent).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.svg-icon-inline')).toBeTruthy();
  });
});
