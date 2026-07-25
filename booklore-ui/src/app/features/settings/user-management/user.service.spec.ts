import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideHttpClient} from '@angular/common/http';
import {provideTanStackQuery, QueryClient} from '@tanstack/angular-query-experimental';

import {User, UserService} from './user.service';
import {CURRENT_USER_QUERY_KEY} from './user-query-keys';
import {AuthService} from '../../../shared/service/auth.service';
import {API_CONFIG} from '../../../core/config/api-config';

const USERS_URL = `${API_CONFIG.BASE_URL}/api/v1/users`;

function makeUser(permissions: Partial<User['permissions']> = {}): User {
  return {
    id: 1,
    username: 'reader',
    name: 'Reader',
    email: 'r@example.com',
    assignedLibraries: [],
    permissions: {admin: false, ...permissions} as User['permissions'],
    userSettings: {} as User['userSettings'],
  };
}

describe('UserService', () => {
  let service: UserService;
  let httpMock: HttpTestingController;
  let qc: QueryClient;

  beforeEach(() => {
    qc = new QueryClient();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTanStackQuery(qc),
        // token = null keeps the injectQuery-backed currentUser query disabled
        {provide: AuthService, useValue: {token: signal(null)}},
        UserService,
      ],
    });
    service = TestBed.inject(UserService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  describe('normalizeUser (via getMyself)', () => {
    it('back-fills the Grimmory permission from the legacy Booklore alias', () => {
      let received: User | undefined;
      service.getMyself().subscribe(u => (received = u));

      httpMock.expectOne(`${USERS_URL}/me`).flush(
        makeUser({canBulkResetBookloreReadProgress: true}),
      );

      expect(received!.permissions.canBulkResetGrimmoryReadProgress).toBe(true);
      expect(received!.permissions.canBulkResetBookloreReadProgress).toBe(true);
    });

    it('back-fills the legacy Booklore permission from the Grimmory name', () => {
      let received: User | undefined;
      service.getMyself().subscribe(u => (received = u));

      httpMock.expectOne(`${USERS_URL}/me`).flush(
        makeUser({canBulkResetGrimmoryReadProgress: true}),
      );

      expect(received!.permissions.canBulkResetBookloreReadProgress).toBe(true);
    });

    it('caches the normalized user under the current-user key', () => {
      service.getMyself().subscribe();
      httpMock.expectOne(`${USERS_URL}/me`).flush(makeUser({canBulkResetGrimmoryReadProgress: true}));

      const cached = qc.getQueryData(CURRENT_USER_QUERY_KEY) as User;
      expect(cached.permissions.canBulkResetBookloreReadProgress).toBe(true);
    });
  });

  describe('getUsers', () => {
    it('normalizes every user in the list', () => {
      let received: User[] | undefined;
      service.getUsers().subscribe(u => (received = u));

      httpMock.expectOne(USERS_URL).flush([makeUser({canBulkResetBookloreReadProgress: true})]);

      expect(received![0].permissions.canBulkResetGrimmoryReadProgress).toBe(true);
    });
  });

  describe('serializeUserPayload (via createUser)', () => {
    it('mirrors nested permission aliases before POSTing', () => {
      service.createUser({permissions: {canBulkResetGrimmoryReadProgress: true}}).subscribe();

      const req = httpMock.expectOne(`${API_CONFIG.BASE_URL}/api/v1/auth/register`);
      expect(req.request.body.permissions).toMatchObject({
        canBulkResetGrimmoryReadProgress: true,
        canBulkResetBookloreReadProgress: true,
      });
      req.flush(null);
    });

    it('mirrors the flat form-field alias before POSTing', () => {
      service.createUser({permissionBulkResetBookloreReadProgress: true}).subscribe();

      const req = httpMock.expectOne(`${API_CONFIG.BASE_URL}/api/v1/auth/register`);
      expect(req.request.body).toMatchObject({
        permissionBulkResetGrimmoryReadProgress: true,
        permissionBulkResetBookloreReadProgress: true,
      });
      req.flush(null);
    });
  });

  describe('changePassword error mapping', () => {
    it('surfaces the server error message', () => {
      let error: Error | undefined;
      service.changePassword('old', 'new').subscribe({error: e => (error = e)});

      httpMock.expectOne(`${USERS_URL}/change-password`).flush(
        {message: 'Password too weak'},
        {status: 400, statusText: 'Bad Request'},
      );

      expect(error?.message).toBe('Password too weak');
    });

    it('falls back to a generic message when the server gives none', () => {
      let error: Error | undefined;
      service.changePassword('old', 'new').subscribe({error: e => (error = e)});

      httpMock.expectOne(`${USERS_URL}/change-password`).flush(null, {status: 500, statusText: 'Server Error'});

      expect(error?.message).toContain('unexpected error');
    });
  });
});
