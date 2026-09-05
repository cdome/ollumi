import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA, signal} from '@angular/core';
import {Subject, of, BehaviorSubject} from 'rxjs';
import {AppLayoutComponent} from './app.layout.component';
import {AppSidebarComponent} from '../layout-sidebar/app.sidebar.component';
import {AppTopBarComponent} from '../layout-topbar/app.topbar.component';
import {LayoutService} from './service/app.layout.service';
import {LocalStorageService} from '../../../service/local-storage.service';
import {Router} from '@angular/router';
import {DialogService} from 'primeng/dynamicdialog';
import {NotificationEventService} from '../../../websocket/notification-event.service';
import {MetadataProgressService} from '../../../service/metadata-progress.service';
import {BookdropFileService} from '../../../../features/bookdrop/service/bookdrop-file.service';
import {UserService} from '../../../../features/settings/user-management/user.service';
import {AuthService} from '../../../service/auth.service';
import {TranslocoTestingModule} from '@jsverse/transloco';

describe('AppLayoutComponent', () => {
  let fixture: ComponentFixture<AppLayoutComponent>;
  let component: AppLayoutComponent;
  let overlayOpenSubject: Subject<unknown>;
  let layoutServiceMock: {
    config: ReturnType<typeof signal>;
    state: Record<string, boolean>;
    overlayOpen$: ReturnType<typeof Subject['prototype']['asObservable']>;
  };
  let localStorageGetMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    overlayOpenSubject = new Subject<unknown>();
    localStorageGetMock = vi.fn(() => 225);

    layoutServiceMock = {
      config: signal({menuMode: 'static'} as any),
      state: {
        staticMenuDesktopInactive: false,
        overlayMenuActive: false,
        profileSidebarVisible: false,
        configSidebarVisible: false,
        staticMenuMobileActive: false,
        menuHoverActive: false
      },
      overlayOpen$: overlayOpenSubject.asObservable()
    };

    TestBed.configureTestingModule({
      imports: [
        AppLayoutComponent,
        AppSidebarComponent,
        AppTopBarComponent,
        TranslocoTestingModule.forRoot({langs: {}})
      ],
      providers: [
        {provide: LayoutService, useValue: layoutServiceMock},
        {provide: Router, useValue: {events: of(), navigate: vi.fn(() => Promise.resolve(true)), url: '/'}},
        {provide: LocalStorageService, useValue: {get: localStorageGetMock, set: vi.fn(), remove: vi.fn()}},
        {provide: DialogService, useValue: {open: vi.fn(() => ({onClose: of()}))}},
        {provide: NotificationEventService, useValue: {latestNotification$: of()}},
        {provide: MetadataProgressService, useValue: {activeTasks$: of({}), progressUpdates$: of({})}},
        {provide: BookdropFileService, useValue: {hasPendingFiles$: of(false), summary$: of({pendingCount:0,totalCount:0})}},
        {provide: UserService, useValue: {userState$: of({user:null, loaded:true}), userStateSubject: new BehaviorSubject({user:null, loaded:true})}},
        {provide: AuthService, useValue: {logout: vi.fn(), token$: of(null)}}
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    TestBed.overrideComponent(AppTopBarComponent, {
      set: {template: '', imports: []}
    });
    TestBed.overrideComponent(AppSidebarComponent, {
      set: {template: '', imports: []}
    });

    fixture = TestBed.createComponent(AppLayoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture?.destroy();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should compute container classes from the layout config', () => {
    expect(component.containerClass).toEqual(expect.objectContaining({
      'layout-static': true,
      'layout-overlay': false
    }));
  });

  it('should set the sidebar width CSS variable on init', () => {
    expect(localStorageGetMock).toHaveBeenCalledWith('sidebarWidth');
    expect(document.documentElement.style.getPropertyValue('--sidebar-width')).toBe('225px');
  });

  it('should hide the menu and reset layout state', () => {
    layoutServiceMock.state.overlayMenuActive = true;
    layoutServiceMock.state.staticMenuMobileActive = true;
    layoutServiceMock.state.menuHoverActive = true;

    component.hideMenu();

    expect(layoutServiceMock.state.overlayMenuActive).toBe(false);
    expect(layoutServiceMock.state.staticMenuMobileActive).toBe(false);
    expect(layoutServiceMock.state.menuHoverActive).toBe(false);
  });

  it('should register an outside-click listener when the overlay menu opens on mobile', () => {
    layoutServiceMock.state.staticMenuMobileActive = true;
    overlayOpenSubject.next(null);

    expect((component as any).menuOutsideClickListener).toBeTruthy();
  });
});
