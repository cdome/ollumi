import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {ReadingSessionService} from './reading-session.service';
import {ReadingSessionApiService, CreateReadingSessionDto} from './reading-session-api.service';
import {BookType} from '../../features/book/model/book.model';

describe('ReadingSessionService', () => {
  let service: ReadingSessionService;
  let createSessionSpy: ReturnType<typeof vi.fn>;
  let sendSessionBeaconSpy: ReturnType<typeof vi.fn>;

  const testBookType: BookType = 'EPUB';

  beforeEach(() => {
    vi.useFakeTimers({toFake: ['setTimeout', 'clearTimeout', 'Date']});
    vi.setSystemTime(new Date('2026-01-01T12:00:00.000Z'));

    createSessionSpy = vi.fn().mockReturnValue(of(undefined));
    sendSessionBeaconSpy = vi.fn().mockReturnValue(true);

    TestBed.configureTestingModule({
      providers: [
        ReadingSessionService,
        {
          provide: ReadingSessionApiService,
          useValue: {
            createSession: createSessionSpy,
            sendSessionBeacon: sendSessionBeaconSpy
          }
        }
      ]
    });

    service = TestBed.inject(ReadingSessionService);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should be created and have no active session', () => {
    expect(service).toBeTruthy();
    expect(service.isSessionActive()).toBe(false);
  });

  it('should start a new reading session', () => {
    service.startSession(42, testBookType, 'chapter-1', 10);

    expect(service.isSessionActive()).toBe(true);
  });

  it('should end any previous session before starting a new one', () => {
    service.startSession(1, testBookType, 'loc-1', 0);
    vi.advanceTimersByTime(60000);

    service.startSession(2, testBookType, 'loc-2', 0);

    expect(service.isSessionActive()).toBe(true);
    expect(createSessionSpy).toHaveBeenCalledTimes(1);
  });

  it('should update progress and reset idle timer', () => {
    service.startSession(10, testBookType, 'loc-1', 0);
    vi.advanceTimersByTime(200000);

    service.updateProgress('loc-2', 50);
    vi.advanceTimersByTime(200000);

    expect(service.isSessionActive()).toBe(true);
  });

  it('should not end session before idle timeout expires', () => {
    service.startSession(10, testBookType);
    vi.advanceTimersByTime(299999);

    expect(service.isSessionActive()).toBe(true);
  });

  it('should end session after idle timeout expires', () => {
    service.startSession(10, testBookType);
    vi.advanceTimersByTime(300000);

    expect(service.isSessionActive()).toBe(false);
    expect(createSessionSpy).toHaveBeenCalledTimes(1);
  });

  it('should persist session when progress updates occur', () => {
    service.startSession(10, testBookType, 'loc-1', 0);

    for (let i = 0; i < 5; i++) {
      vi.advanceTimersByTime(200000);
      service.updateProgress(`loc-${i}`, i * 10);
    }

    expect(service.isSessionActive()).toBe(true);
    vi.advanceTimersByTime(300000);
    expect(service.isSessionActive()).toBe(false);
  });

  it('should send session to backend when ending a long session', () => {
    service.startSession(42, testBookType, 'chapter-1', 10);
    vi.advanceTimersByTime(60000);

    service.endSession('chapter-10', 95);

    expect(createSessionSpy).toHaveBeenCalledTimes(1);
    const dto: CreateReadingSessionDto = createSessionSpy.mock.calls[0][0];
    expect(dto.bookId).toBe(42);
    expect(dto.bookType).toBe(testBookType);
    expect(dto.durationSeconds).toBe(60);
    expect(dto.startProgress).toBe(10);
    expect(dto.endProgress).toBe(95);
    expect(dto.progressDelta).toBe(85);
    expect(dto.startLocation).toBe('chapter-1');
    expect(dto.endLocation).toBe('chapter-10');
    expect(dto.durationFormatted).toBe('1m 0s');
  });

  it('should discard a session shorter than the minimum duration', () => {
    service.startSession(42, testBookType);
    vi.advanceTimersByTime(29999);

    service.endSession();

    expect(createSessionSpy).not.toHaveBeenCalled();
    expect(service.isSessionActive()).toBe(false);
  });

  it('should end a session synchronously on beforeunload for a long session', () => {
    service.startSession(99, 'PDF', 'page-1', 5);
    vi.advanceTimersByTime(45000);

    window.dispatchEvent(new Event('beforeunload'));

    expect(sendSessionBeaconSpy).toHaveBeenCalledTimes(1);
    const dto: CreateReadingSessionDto = sendSessionBeaconSpy.mock.calls[0][0];
    expect(dto.bookId).toBe(99);
    expect(dto.durationSeconds).toBe(45);
    expect(createSessionSpy).not.toHaveBeenCalled();
    expect(service.isSessionActive()).toBe(false);
  });

  it('should not send a beacon on beforeunload for a short session', () => {
    service.startSession(99, 'PDF');
    vi.advanceTimersByTime(20000);

    window.dispatchEvent(new Event('beforeunload'));

    expect(sendSessionBeaconSpy).not.toHaveBeenCalled();
    expect(service.isSessionActive()).toBe(false);
  });

  it('should end the session when the tab is hidden longer than the idle timeout', () => {
    service.startSession(7, testBookType, 'loc-1', 0);
    vi.advanceTimersByTime(10000);

    Object.defineProperty(document, 'hidden', {value: true, configurable: true});
    document.dispatchEvent(new Event('visibilitychange'));

    vi.advanceTimersByTime(350000);

    Object.defineProperty(document, 'hidden', {value: false, configurable: true});
    document.dispatchEvent(new Event('visibilitychange'));

    expect(createSessionSpy).toHaveBeenCalledTimes(1);
    const dto: CreateReadingSessionDto = createSessionSpy.mock.calls[0][0];
    expect(dto.durationSeconds).toBe(310);
    expect(service.isSessionActive()).toBe(false);
  });

  it('should resume session when the tab is hidden for less than the idle timeout', () => {
    service.startSession(7, testBookType);
    vi.advanceTimersByTime(10000);

    Object.defineProperty(document, 'hidden', {value: true, configurable: true});
    document.dispatchEvent(new Event('visibilitychange'));

    vi.advanceTimersByTime(100000);

    Object.defineProperty(document, 'hidden', {value: false, configurable: true});
    document.dispatchEvent(new Event('visibilitychange'));

    expect(createSessionSpy).not.toHaveBeenCalled();
    expect(service.isSessionActive()).toBe(true);
  });
});
