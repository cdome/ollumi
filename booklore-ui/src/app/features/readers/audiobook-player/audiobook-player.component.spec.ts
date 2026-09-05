import {NO_ERRORS_SCHEMA} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {describe, expect, it, vi, beforeEach, afterEach} from 'vitest';
import {CommonModule} from '@angular/common';
import {TranslocoDirective} from '@jsverse/transloco';
import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {AudiobookPlayerComponent} from './audiobook-player.component';
import {
  createReaderActivatedRoute,
  mockAudiobookServiceReaderProvider,
  mockAudiobookSessionServiceReaderProvider,
  mockAuthServiceReaderProvider,
  mockBookMarkServiceReaderProvider,
  mockBookServiceReaderProvider,
  mockLocationReaderProvider,
  mockMessageServiceReaderProvider,
  mockPageTitleServiceReaderProvider,
} from '../../../../testing/reader-component-mocks';

describe('AudiobookPlayerComponent', () => {
  let fixture: ComponentFixture<AudiobookPlayerComponent>;
  let component: AudiobookPlayerComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      providers: [
        createReaderActivatedRoute(),
        mockAuthServiceReaderProvider(),
        mockBookServiceReaderProvider(),
        mockAudiobookServiceReaderProvider(),
        mockAudiobookSessionServiceReaderProvider(),
        mockBookMarkServiceReaderProvider(),
        mockMessageServiceReaderProvider(),
        mockPageTitleServiceReaderProvider(),
        mockLocationReaderProvider(),
      ],
    });

    TestBed.overrideComponent(AudiobookPlayerComponent, {
      set: {
        imports: [CommonModule, TranslocoDirective],
        schemas: [NO_ERRORS_SCHEMA],
      },
    });

    fixture = TestBed.createComponent(AudiobookPlayerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture?.destroy();
    TestBed.resetTestingModule();
    vi.clearAllMocks();
  });

  it('should instantiate and render without error', () => {
    expect(component).toBeTruthy();
    expect(component.bookId).toBe(1);
    expect(fixture.nativeElement).toBeTruthy();
    expect(component.audiobookInfo).toBeTruthy();
  });
});
