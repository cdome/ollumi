import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {firstValueFrom, of, throwError} from 'rxjs';
import {PostLoginInitializerService} from './post-login-initializer.service';
import {IconService} from '../../shared/services/icon.service';

describe('PostLoginInitializerService', () => {
  let service: PostLoginInitializerService;
  let iconService: {
    preloadAllIcons: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    iconService = {
      preloadAllIcons: vi.fn(() => of(undefined))
    };

    TestBed.configureTestingModule({
      providers: [
        PostLoginInitializerService,
        {provide: IconService, useValue: iconService}
      ]
    });

    service = TestBed.inject(PostLoginInitializerService);
  });

  it('should initialize by preloading icons', async () => {
    const result = await firstValueFrom(service.initialize());

    expect(result).toBeUndefined();
    expect(iconService.preloadAllIcons).toHaveBeenCalledTimes(1);
  });

  it('should complete initialization even if icon preloading fails', async () => {
    iconService.preloadAllIcons.mockReturnValue(throwError(() => new Error('preload failed')));
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {
    });

    const result = await firstValueFrom(service.initialize());

    expect(result).toBeUndefined();
    expect(consoleSpy).toHaveBeenCalledWith('Failed to preload icons:', expect.any(Error));

    consoleSpy.mockRestore();
  });
});
