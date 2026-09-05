import {beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {firstValueFrom, of, throwError} from 'rxjs';
import {SettingsHelperService} from './settings-helper.service';
import {AppSettingsService} from './app-settings.service';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';
import {mockMessageServiceProvider, mockTranslocoServiceProvider} from '../../../testing/providers';

describe('SettingsHelperService', () => {
  let service: SettingsHelperService;
  let appSettingsService: {saveSettings: Mock};
  let messageService: {add: Mock; clear: Mock};

  beforeEach(() => {
    appSettingsService = {
      saveSettings: vi.fn().mockReturnValue(of(undefined))
    };

    TestBed.configureTestingModule({
      providers: [
        SettingsHelperService,
        {provide: AppSettingsService, useValue: appSettingsService},
        mockMessageServiceProvider,
        mockTranslocoServiceProvider
      ]
    });

    service = TestBed.inject(SettingsHelperService);
    messageService = TestBed.inject(MessageService) as unknown as {add: Mock; clear: Mock};

    vi.clearAllMocks();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should save a setting and show success', async () => {
    await firstValueFrom(service.saveSetting('THEME', 'dark'));

    expect(appSettingsService.saveSettings).toHaveBeenCalledWith([
      {key: 'THEME', newValue: 'dark'}
    ]);
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
  });

  it('should show an error toast when saving fails', () => {
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    appSettingsService.saveSettings.mockReturnValue(throwError(() => new Error('save failed')));

    service.saveSetting('THEME', 'dark').subscribe({error: () => {}});

    expect(consoleErrorSpy).toHaveBeenCalledWith('Failed to save setting:', expect.any(Error));
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'error'}));
    consoleErrorSpy.mockRestore();
  });

  it('should delegate showMessage to messageService', () => {
    service.showMessage('success', 'Summary', 'Detail');

    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'Summary',
      detail: 'Detail'
    });
  });
});
