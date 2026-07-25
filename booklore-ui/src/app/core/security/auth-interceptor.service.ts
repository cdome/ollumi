import {HttpErrorResponse, HttpInterceptorFn, HttpRequest} from '@angular/common/http';
import {inject} from '@angular/core';
import {catchError, map, switchMap} from 'rxjs/operators';
import {finalize, Observable, share, throwError} from 'rxjs';
import {AuthService} from '../../shared/service/auth.service';
import {API_CONFIG} from '../config/api-config';

export const AuthInterceptorService: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);

  const token = authService.getInternalAccessToken();
  const isApiRequest = req.url.startsWith(`${API_CONFIG.BASE_URL}/api/`);
  const isAuthRequest = req.url.startsWith(`${API_CONFIG.BASE_URL}/api/v1/auth/`);

  const authReq = (token && isApiRequest) ? withBearer(req, token) : req;

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && isApiRequest && !isAuthRequest) {
        return refreshAccessToken(authService).pipe(
          switchMap(newToken => next(withBearer(req, newToken)))
        );
      }
      return throwError(() => error);
    })
  );
};

function withBearer(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({setHeaders: {Authorization: `Bearer ${token}`}});
}

// Every request that hits a 401 while a refresh is in flight must share the same
// refresh observable: a failed refresh then errors all queued requests instead of
// leaving them waiting forever, and logout runs exactly once.
let refreshInFlight$: Observable<string> | null = null;

function refreshAccessToken(authService: AuthService): Observable<string> {
  if (!refreshInFlight$) {
    refreshInFlight$ = authService.internalRefreshToken().pipe(
      map(({accessToken, refreshToken}) => {
        if (!accessToken || !refreshToken) {
          throw new Error('Token refresh response contained no tokens');
        }
        authService.saveInternalTokens(accessToken, refreshToken);
        return accessToken;
      }),
      catchError(error => {
        authService.logout();
        return throwError(() => error);
      }),
      finalize(() => (refreshInFlight$ = null)),
      share()
    );
  }
  return refreshInFlight$;
}

export function resetAuthInterceptorForTesting(): void {
  refreshInFlight$ = null;
}
