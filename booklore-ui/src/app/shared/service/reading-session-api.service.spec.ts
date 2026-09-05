import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient, HttpParams} from '@angular/common/http';
import {firstValueFrom, of} from 'rxjs';
import {ReadingSessionApiService, CreateReadingSessionDto, ReadingSessionResponse} from './reading-session-api.service';
import {mockHttpClientProvider} from '../../../testing/providers';

const BASE_URL = 'http://localhost:6060/api/v1/reading-sessions';

describe('ReadingSessionApiService', () => {
  let service: ReadingSessionApiService;
  let httpClient: {get: Mock; post: Mock};
  let sendBeacon: ReturnType<typeof vi.fn>;

  function createSessionDto(overrides: Partial<CreateReadingSessionDto> = {}): CreateReadingSessionDto {
    return {
      bookId: 1,
      bookType: 'EPUB',
      startTime: '2026-01-01T10:00:00Z',
      endTime: '2026-01-01T11:00:00Z',
      durationSeconds: 3600,
      durationFormatted: '1h 0m',
      ...overrides
    };
  }

  beforeEach(() => {
    sendBeacon = vi.fn(() => true);
    vi.stubGlobal('navigator', {sendBeacon});

    TestBed.configureTestingModule({
      providers: [
        ReadingSessionApiService,
        mockHttpClientProvider
      ]
    });

    service = TestBed.inject(ReadingSessionApiService);
    httpClient = TestBed.inject(HttpClient) as any;

    vi.resetAllMocks();
    httpClient.post.mockReturnValue(of(undefined));
    httpClient.get.mockReturnValue(of({content: [], page: {totalElements: 0, totalPages: 0, number: 0, size: 5}}));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should create a reading session', async () => {
    const dto = createSessionDto();

    await firstValueFrom(service.createSession(dto));

    expect(httpClient.post).toHaveBeenCalledWith(BASE_URL, dto);
  });

  it('should fetch paginated sessions for a book', async () => {
    const response = {
      content: [
        {id: 1, bookId: 7, bookTitle: 'Test', bookType: 'EPUB'} as ReadingSessionResponse
      ],
      page: {totalElements: 1, totalPages: 1, number: 0, size: 5}
    };
    httpClient.get.mockReturnValue(of(response));

    const result = await firstValueFrom(service.getSessionsByBookId(7, 0, 5));

    expect(httpClient.get).toHaveBeenCalledWith(
      `${BASE_URL}/book/7`,
      expect.objectContaining({params: expect.any(HttpParams)})
    );
    const callArgs = httpClient.get.mock.calls[0];
    const params = (callArgs[1] as {params: HttpParams}).params;
    expect(params.get('page')).toBe('0');
    expect(params.get('size')).toBe('5');
    expect(result).toEqual(response);
  });

  it('should send a session beacon and return true on success', () => {
    const dto = createSessionDto();

    const result = service.sendSessionBeacon(dto);

    expect(result).toBe(true);
    expect(sendBeacon).toHaveBeenCalledTimes(1);
    expect(sendBeacon).toHaveBeenCalledWith(BASE_URL, expect.any(Blob));
  });

  it('should return false when sendBeacon is unsupported or returns false', () => {
    sendBeacon.mockReturnValue(false);

    const result = service.sendSessionBeacon(createSessionDto());

    expect(result).toBe(false);
  });

  it('should return false when beacon payload construction fails', () => {
    vi.stubGlobal('Blob', class {
      constructor() {
        throw new Error('blob failed');
      }
    });

    const result = service.sendSessionBeacon(createSessionDto());

    expect(result).toBe(false);
  });
});
