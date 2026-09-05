import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {catchError, firstValueFrom, of, throwError} from 'rxjs';
import {EmailService} from './email.service';
import {mockHttpClientProvider} from '../../../../testing/providers';

describe('EmailService', () => {
  let service: EmailService;
  let httpClient: {post: ReturnType<typeof vi.fn>};

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [EmailService, mockHttpClientProvider]
    });

    service = TestBed.inject(EmailService);
    httpClient = TestBed.inject(HttpClient) as unknown as typeof httpClient;

    vi.resetAllMocks();
    httpClient.post.mockReturnValue(of(undefined));
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should email a book with provider and recipient', async () => {
    const request = {bookId: 1, providerId: 2, recipientId: 3};

    await firstValueFrom(service.emailBook(request));

    expect(httpClient.post).toHaveBeenCalledWith(
      'http://localhost:6060/api/v1/email/book',
      request
    );
  });

  it('should email a book quickly by id', async () => {
    await firstValueFrom(service.emailBookQuick(42));

    expect(httpClient.post).toHaveBeenCalledWith(
      'http://localhost:6060/api/v1/email/book/42',
      {}
    );
  });

  it('should pass through an optional book file id', async () => {
    const request = {bookId: 1, providerId: 2, recipientId: 3, bookFileId: 99};

    await firstValueFrom(service.emailBook(request));

    expect(httpClient.post).toHaveBeenCalledWith(
      'http://localhost:6060/api/v1/email/book',
      request
    );
  });

  it('should surface emailBook errors to callers', async () => {
    const error = new Error('send failed');
    httpClient.post.mockReturnValue(throwError(() => error));

    const result = await firstValueFrom(
      service.emailBook({bookId: 1, providerId: 1, recipientId: 1}).pipe(
        catchError(err => of(err))
      )
    );

    expect(result).toBe(error);
  });

  it('should surface emailBookQuick errors to callers', async () => {
    const error = new Error('quick send failed');
    httpClient.post.mockReturnValue(throwError(() => error));

    const result = await firstValueFrom(
      service.emailBookQuick(7).pipe(catchError(err => of(err)))
    );

    expect(result).toBe(error);
  });
});
