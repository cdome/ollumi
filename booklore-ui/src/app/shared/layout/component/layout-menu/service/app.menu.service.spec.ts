import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {Subscription} from 'rxjs';
import {MenuService} from './app.menu.service';
import type {MenuChangeEvent} from '../../../api/menuchangeevent';

describe('MenuService', () => {
  let service: MenuService;
  let subscriptions: Subscription[];

  beforeEach(() => {
    subscriptions = [];
    TestBed.configureTestingModule({
      providers: [MenuService]
    });
    service = TestBed.inject(MenuService);
  });

  afterEach(() => {
    subscriptions.forEach(sub => sub.unsubscribe());
    TestBed.resetTestingModule();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should emit menu state changes via menuSource$', () => {
    const events: MenuChangeEvent[] = [];
    const sub = service.menuSource$.subscribe(event => events.push(event));
    subscriptions.push(sub);

    service.onMenuStateChange({key: 'dashboard'});
    service.onMenuStateChange({key: 'books', routeEvent: true});

    expect(events).toEqual([
      {key: 'dashboard'},
      {key: 'books', routeEvent: true}
    ]);
  });

  it('should emit reset events via resetSource$', () => {
    let emitted = false;
    const sub = service.resetSource$.subscribe(() => {
      emitted = true;
    });
    subscriptions.push(sub);

    service.reset();

    expect(emitted).toBe(true);
  });
});
