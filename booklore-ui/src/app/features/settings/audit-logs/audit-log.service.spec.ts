import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient, HttpParams} from '@angular/common/http';
import {firstValueFrom, of} from 'rxjs';
import {AuditLogService, AuditLog} from './audit-log.service';
import {mockHttpClientProvider} from '../../../../testing/providers';

const BASE_URL = 'http://localhost:6060/api/v1/audit-logs';

describe('AuditLogService', () => {
  let service: AuditLogService;
  let httpClient: {get: Mock};

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AuditLogService,
        mockHttpClientProvider
      ]
    });

    service = TestBed.inject(AuditLogService);
    httpClient = TestBed.inject(HttpClient) as any;

    vi.resetAllMocks();
    httpClient.get.mockReturnValue(of({content: [], page: {totalElements: 0, totalPages: 0, number: 0, size: 25}}));
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch audit logs with default pagination', async () => {
    const pageable = {
      content: [{id: 1, userId: 1, username: 'admin', action: 'LOGIN', entityType: null, entityId: null, description: 'User logged in', ipAddress: null, countryCode: null, createdAt: '2026-01-01T00:00:00Z'} as AuditLog],
      page: {totalElements: 1, totalPages: 1, number: 0, size: 25}
    };
    httpClient.get.mockReturnValue(of(pageable));

    const result = await firstValueFrom(service.getAuditLogs());

    expect(httpClient.get).toHaveBeenCalledWith(BASE_URL, expect.objectContaining({params: expect.any(HttpParams)}));
    const params = (httpClient.get.mock.calls[0][1] as {params: HttpParams}).params;
    expect(params.get('page')).toBe('0');
    expect(params.get('size')).toBe('25');
    expect(params.get('action')).toBeNull();
    expect(result).toEqual(pageable);
  });

  it('should fetch audit logs with all filters', async () => {
    httpClient.get.mockReturnValue(of({content: [], page: {totalElements: 0, totalPages: 0, number: 1, size: 10}}));

    await firstValueFrom(service.getAuditLogs(1, 10, 'LOGOUT', 'admin', '2026-01-01', '2026-01-31'));

    const params = (httpClient.get.mock.calls[0][1] as {params: HttpParams}).params;
    expect(params.get('page')).toBe('1');
    expect(params.get('size')).toBe('10');
    expect(params.get('action')).toBe('LOGOUT');
    expect(params.get('username')).toBe('admin');
    expect(params.get('from')).toBe('2026-01-01');
    expect(params.get('to')).toBe('2026-01-31');
  });

  it('should fetch distinct usernames', async () => {
    const usernames = ['admin', 'user'];
    httpClient.get.mockReturnValue(of(usernames));

    const result = await firstValueFrom(service.getDistinctUsernames());

    expect(httpClient.get).toHaveBeenCalledWith(`${BASE_URL}/usernames`);
    expect(result).toEqual(usernames);
  });
});
