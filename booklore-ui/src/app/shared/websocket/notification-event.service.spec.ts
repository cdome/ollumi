import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {NotificationEventService} from './notification-event.service';
import {Severity, LogNotification} from './model/log-notification.model';

describe('NotificationEventService', () => {
  let service: NotificationEventService;

  beforeEach(() => {
    vi.useFakeTimers({toFake: ['setTimeout', 'clearTimeout']});

    TestBed.configureTestingModule({
      providers: [NotificationEventService]
    });

    service = TestBed.inject(NotificationEventService);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should emit notification and highlight on handleNewNotification', () => {
    const notifications: LogNotification[] = [];
    const highlights: boolean[] = [];

    service.latestNotification$.subscribe(n => notifications.push(n));
    service.notificationHighlight$.subscribe(h => highlights.push(h));

    const notification: LogNotification = {
      message: 'Test notification',
      severity: Severity.INFO
    };

    service.handleNewNotification(notification);

    expect(notifications).toEqual([notification]);
    expect(highlights).toEqual([false, true]);
  });

  it('should filter out the initial null value', () => {
    const notifications: LogNotification[] = [];

    service.latestNotification$.subscribe(n => notifications.push(n));

    expect(notifications).toEqual([]);
  });

  it('should turn off highlight after 7500ms', () => {
    const highlights: boolean[] = [];

    service.notificationHighlight$.subscribe(h => highlights.push(h));

    service.handleNewNotification({message: 'Test'});
    expect(highlights).toEqual([false, true]);

    vi.advanceTimersByTime(7500);
    expect(highlights).toEqual([false, true, false]);
  });

  it('should clear the latest notification after the cleanup window', () => {
    const notifications: LogNotification[] = [];
    const subject = (service as unknown as { latestNotificationSubject: { value: LogNotification | null } }).latestNotificationSubject;

    service.latestNotification$.subscribe(n => notifications.push(n));

    const notification: LogNotification = {message: 'Test'};
    service.handleNewNotification(notification);
    expect(notifications).toEqual([notification]);
    expect(subject.value).toEqual(notification);

    vi.advanceTimersByTime(7500);
    expect(getLastHighlight()).toBe(false);

    vi.advanceTimersByTime(12500);

    expect(subject.value).toBeNull();
  });

  it('should reset the highlight timer when a new notification arrives while highlighted', () => {
    service.handleNewNotification({message: 'First'});
    expect(getLastHighlight()).toBe(true);

    vi.advanceTimersByTime(5000);

    service.handleNewNotification({message: 'Second'});
    expect(getLastHighlight()).toBe(true);

    vi.advanceTimersByTime(7500);
    expect(getLastHighlight()).toBe(false);

    vi.advanceTimersByTime(12500);
    expect(getLastHighlight()).toBe(false);
  });

  function getLastHighlight(): boolean {
    let latest = false;
    service.notificationHighlight$.subscribe(h => latest = h).unsubscribe();
    return latest;
  }

  it('should keep highlight alive if notifications arrive within the highlight window', () => {

    const highlights: boolean[] = [];

    service.notificationHighlight$.subscribe(h => highlights.push(h));

    service.handleNewNotification({message: 'First'});

    vi.advanceTimersByTime(7000);
    service.handleNewNotification({message: 'Second'});

    vi.advanceTimersByTime(7000);
    service.handleNewNotification({message: 'Third'});

    vi.advanceTimersByTime(6000);

    expect(highlights[highlights.length - 1]).toBe(true);

    vi.advanceTimersByTime(1500);
    expect(highlights[highlights.length - 1]).toBe(false);
  });
});
