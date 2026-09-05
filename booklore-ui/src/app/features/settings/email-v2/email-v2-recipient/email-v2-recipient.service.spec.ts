import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {catchError, firstValueFrom, of, throwError} from 'rxjs';
import {EmailV2RecipientService} from './email-v2-recipient.service';
import {EmailRecipient} from '../email-recipient.model';
import {mockHttpClientProvider} from '../../../../../testing/providers';

function createMockRecipient(overrides: Partial<EmailRecipient> = {}): EmailRecipient {
  return {
    id: 1,
    email: 'reader@example.com',
    name: 'Reader One',
    defaultRecipient: false,
    isEditing: false,
    ...overrides
  };
}

describe('EmailV2RecipientService', () => {
  let service: EmailV2RecipientService;
  let httpClient: {get: ReturnType<typeof vi.fn>; post: ReturnType<typeof vi.fn>; put: ReturnType<typeof vi.fn>; delete: ReturnType<typeof vi.fn>; patch: ReturnType<typeof vi.fn>};

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [EmailV2RecipientService, mockHttpClientProvider]
    });

    service = TestBed.inject(EmailV2RecipientService);
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

  it('should fetch all recipients', async () => {
    const recipients = [createMockRecipient({id: 1}), createMockRecipient({id: 2, email: 'other@example.com'})];
    httpClient.get.mockReturnValue(of(recipients));

    const result = await firstValueFrom(service.getRecipients());

    expect(httpClient.get).toHaveBeenCalledWith('http://localhost:6060/api/v1/email/recipients');
    expect(result).toEqual(recipients);
  });

  it('should create a recipient', async () => {
    const recipient = createMockRecipient({id: undefined as unknown as number});
    const created = createMockRecipient({id: 5});
    httpClient.post.mockReturnValue(of(created));

    const result = await firstValueFrom(service.createRecipient(recipient));

    expect(httpClient.post).toHaveBeenCalledWith(
      'http://localhost:6060/api/v1/email/recipients',
      recipient
    );
    expect(result).toEqual(created);
  });

  it('should update a recipient', async () => {
    const recipient = createMockRecipient({id: 3, name: 'Updated'});
    httpClient.put.mockReturnValue(of(recipient));

    const result = await firstValueFrom(service.updateRecipient(recipient));

    expect(httpClient.put).toHaveBeenCalledWith(
      'http://localhost:6060/api/v1/email/recipients/3',
      recipient
    );
    expect(result).toEqual(recipient);
  });

  it('should delete a recipient', async () => {
    await firstValueFrom(service.deleteRecipient(7));

    expect(httpClient.delete).toHaveBeenCalledWith(
      'http://localhost:6060/api/v1/email/recipients/7'
    );
  });

  it('should set the default recipient', async () => {
    await firstValueFrom(service.setDefaultRecipient(4));

    expect(httpClient.patch).toHaveBeenCalledWith(
      'http://localhost:6060/api/v1/email/recipients/4/set-default',
      {}
    );
  });

  it('should surface recipient fetch errors', async () => {
    const error = new Error('network error');
    httpClient.get.mockReturnValue(throwError(() => error));

    const result = await firstValueFrom(
      service.getRecipients().pipe(catchError(err => of(err)))
    );

    expect(result).toBe(error);
  });
});
