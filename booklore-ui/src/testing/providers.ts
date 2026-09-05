import {HttpClient} from '@angular/common/http';
import {Router, ActivatedRoute} from '@angular/router';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from 'primeng/api';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {of} from 'rxjs';
import {vi} from 'vitest';
import {RxStompService} from '../app/shared/websocket/rx-stomp.service';
import {AuthService} from '../app/shared/service/auth.service';
import {IconPickerService} from '../app/shared/service/icon-picker.service';

export const mockHttpClientProvider = {
  provide: HttpClient,
  useValue: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn(),
    request: vi.fn()
  }
};

export const mockRouterProvider = {
  provide: Router,
  useValue: {
    navigate: vi.fn(() => Promise.resolve(true)),
    navigateByUrl: vi.fn(() => Promise.resolve(true)),
    url: '/',
    events: of(),
    createUrlTree: vi.fn(),
    serializeUrl: vi.fn()
  }
};

export const mockActivatedRouteProvider = {
  provide: ActivatedRoute,
  useValue: {
    params: of({}),
    queryParams: of({}),
    snapshot: {
      paramMap: {get: vi.fn(() => null)},
      queryParamMap: {get: vi.fn(() => null)}
    }
  }
};

export const mockTranslocoServiceProvider = {
  provide: TranslocoService,
  useValue: {
    translate: vi.fn((key: string) => key),
    selectTranslation: vi.fn(() => of({})),
    langChanges$: of('en'),
    getActiveLang: vi.fn(() => 'en'),
    setActiveLang: vi.fn()
  }
};

export const mockMessageServiceProvider = {
  provide: MessageService,
  useValue: {
    add: vi.fn(),
    clear: vi.fn()
  }
};

export const mockRxStompServiceProvider = {
  provide: RxStompService,
  useValue: {
    watch: vi.fn(() => of()),
    publish: vi.fn(),
    activate: vi.fn(),
    deactivate: vi.fn()
  }
};

export const mockAuthServiceProvider = {
  provide: AuthService,
  useValue: {
    token$: of(null),
    tokenSubject: {next: vi.fn(), value: null},
    internalLogin: vi.fn(),
    internalRefreshToken: vi.fn(),
    remoteLogin: vi.fn(),
    logout: vi.fn(),
    forceLogout: vi.fn(),
    getInternalAccessToken: vi.fn(() => null),
    getInternalRefreshToken: vi.fn(() => null),
    saveInternalTokens: vi.fn()
  }
};

export const mockDynamicDialogRefProvider = {
  provide: DynamicDialogRef,
  useValue: {
    close: vi.fn()
  }
};

export const mockDynamicDialogConfigProvider = {
  provide: DynamicDialogConfig,
  useValue: {
    data: null
  }
};

export const mockIconPickerServiceProvider = {
  provide: IconPickerService,
  useValue: {
    open: vi.fn(() => of(null))
  }
};
