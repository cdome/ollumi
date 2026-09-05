import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, of, throwError} from 'rxjs';
import {AuthService} from '../../../shared/service/auth.service';
import {UserService, type User, type UserUpdateRequest} from './user.service';
import {mockAuthServiceProvider, mockHttpClientProvider} from '../../../../testing/providers';
import {createMockUser} from '../../../../testing/factories';

describe('UserService', () => {
  let service: UserService;
  let httpClient: {get: Mock; post: Mock; put: Mock; delete: Mock; patch: Mock; request: Mock};

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        UserService,
        mockHttpClientProvider,
        mockAuthServiceProvider
      ]
    });

    service = TestBed.inject(UserService);
    httpClient = TestBed.inject(HttpClient) as any;

    vi.resetAllMocks();
    httpClient.get.mockReturnValue(of(createMockUser()));
    httpClient.post.mockReturnValue(of(undefined));
    httpClient.put.mockReturnValue(of(undefined));
    httpClient.delete.mockReturnValue(of(undefined));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should fetch the current user via getMyself', () => {
    const user = createMockUser({id: 42, username: 'me'});
    httpClient.get.mockReturnValue(of(user));

    let result: User | undefined;
    service.getMyself().subscribe(u => result = u);

    expect(httpClient.get).toHaveBeenCalledWith('http://localhost:6060/api/v1/users/me');
    expect(result).toEqual(user);
  });

  it('should create a user', () => {
    const payload = createMockUser({id: undefined as never}) as Omit<User, 'id'>;
    service.createUser(payload).subscribe();

    expect(httpClient.post).toHaveBeenCalledWith('http://localhost:6060/api/v1/auth/register', payload);
  });

  it('should get all users', () => {
    const users = [createMockUser({id: 1}), createMockUser({id: 2, username: 'u2'})];
    httpClient.get.mockReturnValue(of(users));

    let result: User[] | undefined;
    service.getUsers().subscribe(u => result = u);

    expect(httpClient.get).toHaveBeenCalledWith('http://localhost:6060/api/v1/users');
    expect(result).toEqual(users);
  });

  it('should update a user', () => {
    const updated = createMockUser({name: 'Updated'});
    httpClient.put.mockReturnValue(of(updated));

    const request: UserUpdateRequest = {name: 'Updated'};
    let result: User | undefined;
    service.updateUser(5, request).subscribe(u => result = u);

    expect(httpClient.put).toHaveBeenCalledWith('http://localhost:6060/api/v1/users/5', request);
    expect(result).toEqual(updated);
  });

  it('should delete a user', () => {
    service.deleteUser(7).subscribe();

    expect(httpClient.delete).toHaveBeenCalledWith('http://localhost:6060/api/v1/users/7');
  });

  it('should change a user password and map backend error messages', () => {
    httpClient.put.mockReturnValue(throwError(() => ({error: {message: 'Weak password'}})));

    let errorMessage: string | undefined;
    service.changeUserPassword(3, 'newpass').subscribe({
      error: err => errorMessage = err.message
    });

    expect(httpClient.put).toHaveBeenCalledWith(
      'http://localhost:6060/api/v1/users/change-user-password',
      {userId: 3, newPassword: 'newpass'}
    );
    expect(errorMessage).toBe('Weak password');
  });

  it('should change own password and map fallback error messages', () => {
    httpClient.put.mockReturnValue(throwError(() => ({})));

    let errorMessage: string | undefined;
    service.changePassword('old', 'new').subscribe({
      error: err => errorMessage = err.message
    });

    expect(httpClient.put).toHaveBeenCalledWith(
      'http://localhost:6060/api/v1/users/change-password',
      {currentPassword: 'old', newPassword: 'new'}
    );
    expect(errorMessage).toBe('An unexpected error occurred. Please try again.');
  });

  it('should update a user setting locally and call the backend', () => {
    const user = createMockUser({id: 9});
    service.setInitialUser(user);

    service.updateUserSetting(9, 'enableSeriesView', false);

    expect(httpClient.put).toHaveBeenCalledWith(
      'http://localhost:6060/api/v1/users/9/settings',
      {key: 'enableSeriesView', value: false},
      expect.objectContaining({
        headers: {'Content-Type': 'application/json'},
        responseType: 'text'
      })
    );
    expect(service.getCurrentUser()?.userSettings.enableSeriesView).toBe(false);
  });

  it('should set and get the initial user', () => {
    const user = createMockUser();
    service.setInitialUser(user);

    expect(service.getCurrentUser()).toEqual(user);
    expect(service.userStateSubject.value.loaded).toBe(true);
  });

  it('should fetch myself via userState$ when the state is not loaded', () => {
    service.userStateSubject.next({user: null, loaded: false, error: null});

    const user = createMockUser();
    httpClient.get.mockReturnValue(of(user));

    const states: {user: User | null; loaded: boolean; error: string | null}[] = [];
    service.userState$.subscribe(s => states.push(s));

    expect(httpClient.get).toHaveBeenCalledWith('http://localhost:6060/api/v1/users/me');
    expect(states.some(s => s.user)).toBe(true);
    expect(service.getCurrentUser()).toEqual(user);
  });

  describe('auth token changes', () => {
    let tokenSubject: BehaviorSubject<string | null>;

    beforeEach(() => {
      tokenSubject = new BehaviorSubject<string | null>('token');

      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          UserService,
          mockHttpClientProvider,
          {provide: AuthService, useValue: {token$: tokenSubject.asObservable()}}
        ]
      });

      service = TestBed.inject(UserService);
      httpClient = TestBed.inject(HttpClient) as any;

      vi.resetAllMocks();
      httpClient.get.mockReturnValue(of(createMockUser()));
    });

    it('should reset state when the token is cleared', () => {
      tokenSubject.next(null);

      expect(service.getCurrentUser()).toBeNull();
      expect(service.userStateSubject.value.loaded).toBe(true);
    });

    it('should invalidate the loaded state when a new token arrives without a cached user', () => {
      service.userStateSubject.next({user: null, loaded: true, error: null});

      tokenSubject.next('fresh');

      expect(service.userStateSubject.value.loaded).toBe(false);
    });
  });
});
