import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {catchError, firstValueFrom, of, throwError} from 'rxjs';
import {EmailV2ProviderService} from './email-v2-provider.service';
import {EmailProvider} from '../email-provider.model';
import {mockHttpClientProvider} from '../../../../../testing/providers';

function createMockProvider(overrides: Partial<EmailProvider> = {}): EmailProvider {
  return {
    id: 1,
    userId: 2,
    name: 'Test Provider',
    host: 'smtp.example.com',
    port: 587,
    username: 'user',
    password: 'secret',
    fromAddress: 'from@example.com',
    auth: true,
    startTls: true,
    defaultProvider: false,
    shared: false,
    isEditing: false,
    ...overrides
  };
}

describe('EmailV2ProviderService', () => {
  let service: EmailV2ProviderService;
  let httpClient: {get: ReturnType<typeof vi.fn>; post: ReturnType<typeof vi.fn>; put: ReturnType<typeof vi.fn>; delete: ReturnType<typeof vi.fn>; patch: ReturnType<typeof vi.fn>};

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [EmailV2ProviderService, mockHttpClientProvider]
    });

    service = TestBed.inject(EmailV2ProviderService);
    httpClient = TestBed.inject(HttpClient) as unknown as typeof httpClient;

    vi.resetAllMocks();
    httpClient.get.mockReturnValue(of([]));
    httpClient.post.mockReturnValue(of({}));
    httpClient.put.mockReturnValue(of({}));
    httpClient.delete.mockReturnValue(of(undefined));
    httpClient.patch.mockReturnValue(of(undefined));
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch all email providers', async () => {
    const providers = [createMockProvider({id: 1}), createMockProvider({id: 2, name: 'Other'})];
    httpClient.get.mockReturnValue(of(providers));

    const result = await firstValueFrom(service.getEmailProviders());

    expect(httpClient.get).toHaveBeenCalledWith('http://localhost:6060/api/v1/email/providers');
    expect(result).toEqual(providers);
  });

  it('should create an email provider', async () => {
    const provider = createMockProvider({id: undefined as unknown as number});
    const created = createMockProvider({id: 5});
    httpClient.post.mockReturnValue(of(created));

    const result = await firstValueFrom(service.createEmailProvider(provider));

    expect(httpClient.post).toHaveBeenCalledWith(
      'http://localhost:6060/api/v1/email/providers',
      provider
    );
    expect(result).toEqual(created);
  });

  it('should update an email provider', async () => {
    const provider = createMockProvider({id: 3, name: 'Updated'});
    httpClient.put.mockReturnValue(of(provider));

    const result = await firstValueFrom(service.updateProvider(provider));

    expect(httpClient.put).toHaveBeenCalledWith(
      'http://localhost:6060/api/v1/email/providers/3',
      provider
    );
    expect(result).toEqual(provider);
  });

  it('should delete an email provider', async () => {
    await firstValueFrom(service.deleteProvider(7));

    expect(httpClient.delete).toHaveBeenCalledWith(
      'http://localhost:6060/api/v1/email/providers/7'
    );
  });

  it('should set the default provider', async () => {
    await firstValueFrom(service.setDefaultProvider(4));

    expect(httpClient.patch).toHaveBeenCalledWith(
      'http://localhost:6060/api/v1/email/providers/4/set-default',
      {}
    );
  });

  it('should surface provider fetch errors', async () => {
    const error = new Error('network error');
    httpClient.get.mockReturnValue(throwError(() => error));

    const result = await firstValueFrom(
      service.getEmailProviders().pipe(catchError(err => of(err)))
    );

    expect(result).toBe(error);
  });
});
