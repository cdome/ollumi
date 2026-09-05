import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {BehaviorSubject} from 'rxjs';
import {TranslocoDirective, TranslocoPipe} from '@jsverse/transloco';
import {LiveNotificationBoxComponent} from './live-notification-box.component';
import {NotificationEventService} from '../../websocket/notification-event.service';
import {TagComponent} from '../tag/tag.component';
import {mockTranslocoServiceProvider} from '../../../../testing/providers';

describe('LiveNotificationBoxComponent', () => {
  let fixture: ComponentFixture<LiveNotificationBoxComponent>;
  let component: LiveNotificationBoxComponent;

  const notificationSubject = new BehaviorSubject<any>({message: 'default'});

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LiveNotificationBoxComponent, TagComponent, TranslocoDirective, TranslocoPipe],
      providers: [
        mockTranslocoServiceProvider,
        {provide: NotificationEventService, useValue: {latestNotification$: notificationSubject.asObservable()}}
      ]
    });

    fixture = TestBed.createComponent(LiveNotificationBoxComponent);
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

  it('should update the latest notification when the service emits', () => {
    notificationSubject.next({message: 'Test notification', severity: 'INFO'});

    expect(component.latestNotification.message).toBe('Test notification');
    expect(component.getSeverityColor('INFO')).toBe('green');
  });
});
