import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {firstValueFrom, of} from 'rxjs';
import {UserStatsService} from './user-stats.service';
import {mockHttpClientProvider} from '../../../../testing/providers';

const BASE_URL = 'http://localhost:6060/api/v1/user-stats';

describe('UserStatsService', () => {
  let service: UserStatsService;
  let httpClient: {get: Mock};

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        UserStatsService,
        mockHttpClientProvider
      ]
    });

    service = TestBed.inject(UserStatsService);
    httpClient = TestBed.inject(HttpClient) as any;

    vi.resetAllMocks();
    httpClient.get.mockReturnValue(of([]));
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch the reading heatmap for a year', async () => {
    const data = [{date: '2026-01-01', count: 3}];
    httpClient.get.mockReturnValue(of(data));

    const result = await firstValueFrom(service.getHeatmapForYear(2026));

    expect(httpClient.get).toHaveBeenCalledWith(
      `${BASE_URL}/reading/heatmap`,
      expect.objectContaining({params: {year: '2026'}})
    );
    expect(result).toEqual(data);
  });

  it('should fetch the reading timeline for a week', async () => {
    const data = [{bookId: 1, bookTitle: 'Test', startDate: '2026-01-01', bookType: 'EPUB', endDate: '2026-01-07', totalSessions: 5, totalDurationSeconds: 3600}];
    httpClient.get.mockReturnValue(of(data));

    const result = await firstValueFrom(service.getTimelineForWeek(2026, 1));

    expect(httpClient.get).toHaveBeenCalledWith(
      `${BASE_URL}/reading/timeline`,
      expect.objectContaining({params: {year: '2026', week: '1'}})
    );
    expect(result).toEqual(data);
  });

  it('should fetch genre stats', async () => {
    const data = [{genre: 'Sci-Fi', bookCount: 10, totalSessions: 20, totalDurationSeconds: 7200, averageSessionsPerBook: 2}];
    httpClient.get.mockReturnValue(of(data));

    const result = await firstValueFrom(service.getGenreStats());

    expect(httpClient.get).toHaveBeenCalledWith(`${BASE_URL}/reading/genres`);
    expect(result).toEqual(data);
  });

  it('should fetch the completion timeline for a year', async () => {
    const data = [{year: 2026, month: 1, totalBooks: 5, statusBreakdown: {}, finishedBooks: 3, completionRate: 0.6}];
    httpClient.get.mockReturnValue(of(data));

    const result = await firstValueFrom(service.getCompletionTimelineForYear(2026));

    expect(httpClient.get).toHaveBeenCalledWith(
      `${BASE_URL}/reading/completion-timeline`,
      expect.objectContaining({params: {year: '2026'}})
    );
    expect(result).toEqual(data);
  });

  it('should fetch favorite days without optional filters', async () => {
    const data = [{dayOfWeek: 1, dayName: 'Monday', sessionCount: 5, totalDurationSeconds: 3600}];
    httpClient.get.mockReturnValue(of(data));

    const result = await firstValueFrom(service.getFavoriteDays());

    expect(httpClient.get).toHaveBeenCalledWith(
      `${BASE_URL}/reading/favorite-days`,
      expect.objectContaining({params: {}})
    );
    expect(result).toEqual(data);
  });

  it('should fetch favorite days with year and month filters', async () => {
    const data = [{dayOfWeek: 2, dayName: 'Tuesday', sessionCount: 2, totalDurationSeconds: 1800}];
    httpClient.get.mockReturnValue(of(data));

    const result = await firstValueFrom(service.getFavoriteDays(2026, 2));

    expect(httpClient.get).toHaveBeenCalledWith(
      `${BASE_URL}/reading/favorite-days`,
      expect.objectContaining({params: {year: '2026', month: '2'}})
    );
    expect(result).toEqual(data);
  });

  it('should fetch peak hours without optional filters', async () => {
    const data = [{hourOfDay: 20, sessionCount: 3, totalDurationSeconds: 5400}];
    httpClient.get.mockReturnValue(of(data));

    const result = await firstValueFrom(service.getPeakHours());

    expect(httpClient.get).toHaveBeenCalledWith(
      `${BASE_URL}/reading/peak-hours`,
      expect.objectContaining({params: {}})
    );
    expect(result).toEqual(data);
  });

  it('should fetch peak hours with year and month filters', async () => {
    const data = [{hourOfDay: 21, sessionCount: 1, totalDurationSeconds: 1200}];
    httpClient.get.mockReturnValue(of(data));

    const result = await firstValueFrom(service.getPeakHours(2026, 3));

    expect(httpClient.get).toHaveBeenCalledWith(
      `${BASE_URL}/reading/peak-hours`,
      expect.objectContaining({params: {year: '2026', month: '3'}})
    );
    expect(result).toEqual(data);
  });

  it('should fetch page turner scores', async () => {
    const data = [{bookId: 1, bookTitle: 'Test', categories: [], pageCount: 300, personalRating: 5, gripScore: 10, totalSessions: 4, avgSessionDurationSeconds: 900, sessionAcceleration: 1, gapReduction: 0, finishBurst: true}];
    httpClient.get.mockReturnValue(of(data));

    const result = await firstValueFrom(service.getPageTurnerScores());

    expect(httpClient.get).toHaveBeenCalledWith(`${BASE_URL}/reading/page-turner-scores`);
    expect(result).toEqual(data);
  });

  it('should fetch the completion race for a year', async () => {
    const data = [{bookId: 1, bookTitle: 'Test', sessionDate: '2026-01-01', endProgress: 100}];
    httpClient.get.mockReturnValue(of(data));

    const result = await firstValueFrom(service.getCompletionRace(2026));

    expect(httpClient.get).toHaveBeenCalledWith(
      `${BASE_URL}/reading/completion-race`,
      expect.objectContaining({params: {year: '2026'}})
    );
    expect(result).toEqual(data);
  });

  it('should fetch all reading dates', async () => {
    const data = [{date: '2026-01-01', count: 1}];
    httpClient.get.mockReturnValue(of(data));

    const result = await firstValueFrom(service.getReadingDates());

    expect(httpClient.get).toHaveBeenCalledWith(`${BASE_URL}/reading/dates`);
    expect(result).toEqual(data);
  });

  it('should fetch the session scatter for a year', async () => {
    const data = [{hourOfDay: 20, durationMinutes: 30, dayOfWeek: 1}];
    httpClient.get.mockReturnValue(of(data));

    const result = await firstValueFrom(service.getSessionScatter(2026));

    expect(httpClient.get).toHaveBeenCalledWith(
      `${BASE_URL}/reading/session-scatter`,
      expect.objectContaining({params: {year: '2026'}})
    );
    expect(result).toEqual(data);
  });
});
