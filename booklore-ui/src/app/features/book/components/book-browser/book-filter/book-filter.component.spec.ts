import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslocoDirective} from '@jsverse/transloco';
import {BehaviorSubject, of, Subject} from 'rxjs';
import {BookFilterComponent} from './book-filter.component';
import {BookFilterService} from './book-filter.service';
import {UserService, DEFAULT_VISIBLE_FILTERS} from '../../../../settings/user-management/user.service';
import {EntityType} from '../book-browser.component';
import {mockTranslocoServiceProvider} from '../../../../../../testing/providers';

describe('BookFilterComponent', () => {
  let fixture: ComponentFixture<BookFilterComponent>;
  let component: BookFilterComponent;

  const userStateSubject = new BehaviorSubject<any>({
    user: {userSettings: {visibleFilters: [...DEFAULT_VISIBLE_FILTERS]}},
    loaded: true,
    error: null
  });

  const resetFilter$ = new Subject<void>();

  const bookFilterServiceMock = {
    createFilterStreams: vi.fn(() => ({})),
    processFilterValue: vi.fn((_key: string, value: unknown) => value)
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [BookFilterComponent],
      providers: [
        {provide: BookFilterService, useValue: bookFilterServiceMock},
        {
          provide: UserService,
          useValue: {
            userState$: userStateSubject.asObservable(),
            updateUserSetting: vi.fn()
          }
        },
        mockTranslocoServiceProvider
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    TestBed.overrideComponent(BookFilterComponent, {
      set: {
        imports: [CommonModule, TranslocoDirective],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(BookFilterComponent);
    component = fixture.componentInstance;
    component.entity$ = of(null);
    component.entityType$ = of(EntityType.ALL_BOOKS);
    component.resetFilter$ = resetFilter$;
    component.showFilter = true;
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

  it('should initialize with default AND filter mode', () => {
    expect(component.selectedFilterMode).toBe('and');
  });

  it('should clear active filters on reset', () => {
    component.activeFilters = {author: [1]};
    resetFilter$.next();

    expect(component.activeFilters).toEqual({});
  });
});
