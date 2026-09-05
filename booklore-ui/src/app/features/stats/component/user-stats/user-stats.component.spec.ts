import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {CommonModule} from '@angular/common';
import {DragDropModule} from '@angular/cdk/drag-drop';
import {BehaviorSubject, of} from 'rxjs';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {UserStatsComponent} from './user-stats.component';
import {UserChartConfig, UserChartConfigService} from './service/user-chart-config.service';
import {UserService} from '../../../settings/user-management/user.service';
import {createMockUser} from '../../../../../testing/factories';

describe('UserStatsComponent', () => {
  let fixture: ComponentFixture<UserStatsComponent>;
  let component: UserStatsComponent;

  let chartsSubject: BehaviorSubject<UserChartConfig[]>;
  let userChartConfigServiceMock: {
    charts$: ReturnType<BehaviorSubject<UserChartConfig[]>['asObservable']>;
    getVisibleCharts: ReturnType<typeof vi.fn>;
    toggleChart: ReturnType<typeof vi.fn>;
    reorderCharts: ReturnType<typeof vi.fn>;
    resetLayout: ReturnType<typeof vi.fn>;
  };

  let userStateSubject: BehaviorSubject<{user: ReturnType<typeof createMockUser> | null; loaded: boolean}>;
  let userServiceMock: {
    userState$: ReturnType<BehaviorSubject<{user: ReturnType<typeof createMockUser> | null; loaded: boolean}>['asObservable']>;
  };

  beforeEach(() => {
    const defaultCharts: UserChartConfig[] = [
      {id: 'read-status', title: 'Read Status', component: {}, enabled: true, sizeClass: 'chart-small-square', order: 0},
      {id: 'reading-habits', title: 'Reading Habits', component: {}, enabled: true, sizeClass: 'chart-medium', order: 1}
    ];

    chartsSubject = new BehaviorSubject<UserChartConfig[]>(defaultCharts);
    userChartConfigServiceMock = {
      charts$: chartsSubject.asObservable(),
      getVisibleCharts: vi.fn(() => defaultCharts.filter(chart => chart.enabled)),
      toggleChart: vi.fn(),
      reorderCharts: vi.fn(),
      resetLayout: vi.fn()
    };

    userStateSubject = new BehaviorSubject<{user: ReturnType<typeof createMockUser> | null; loaded: boolean}>({
      user: createMockUser({name: 'Jane Doe', username: 'janedoe'}),
      loaded: true
    });
    userServiceMock = {
      userState$: userStateSubject.asObservable()
    };

    TestBed.configureTestingModule({
      imports: [UserStatsComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        {provide: UserChartConfigService, useValue: userChartConfigServiceMock},
        {provide: UserService, useValue: userServiceMock}
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    TestBed.overrideComponent(UserStatsComponent, {
      set: {
        imports: [CommonModule, DragDropModule, TranslocoTestingModule]
      }
    });

    fixture = TestBed.createComponent(UserStatsComponent);
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

  it('should set the user name from the user service', () => {
    expect(component.userName).toBe('Jane Doe');
  });

  it('should sync charts and visible charts from the chart config service', () => {
    expect(component.charts.length).toBe(2);
    expect(component.visibleCharts.length).toBe(2);
  });

  it('should toggle the config panel', () => {
    expect(component.showConfigPanel).toBe(false);
    component.toggleConfigPanel();
    expect(component.showConfigPanel).toBe(true);
    component.toggleConfigPanel();
    expect(component.showConfigPanel).toBe(false);
  });

  it('should delegate toggleChart to the chart config service', () => {
    component.toggleChart('read-status');
    expect(userChartConfigServiceMock.toggleChart).toHaveBeenCalledWith('read-status');
  });

  it('should delegate drop reordering to the chart config service', () => {
    const event = {previousIndex: 0, currentIndex: 1, item: null, container: null, previousContainer: null} as any;
    component.drop(event);
    expect(userChartConfigServiceMock.reorderCharts).toHaveBeenCalledWith(0, 1);
  });

  it('should hide all visible charts', () => {
    component.hideAllCharts();
    expect(userChartConfigServiceMock.toggleChart).toHaveBeenCalledTimes(2);
  });

  it('should show all hidden charts', () => {
    chartsSubject.next([
      {id: 'read-status', title: 'Read Status', component: {}, enabled: false, sizeClass: 'chart-small-square', order: 0},
      {id: 'reading-habits', title: 'Reading Habits', component: {}, enabled: false, sizeClass: 'chart-medium', order: 1}
    ]);
    component.showAllCharts();
    expect(userChartConfigServiceMock.toggleChart).toHaveBeenCalledTimes(2);
  });

  it('should reset the layout through the chart config service', () => {
    component.resetLayout();
    expect(userChartConfigServiceMock.resetLayout).toHaveBeenCalled();
  });
});
