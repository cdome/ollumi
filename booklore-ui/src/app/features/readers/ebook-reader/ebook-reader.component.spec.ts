import {NO_ERRORS_SCHEMA} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {describe, expect, it, vi, beforeEach, afterEach} from 'vitest';
import {CommonModule} from '@angular/common';
import {TranslocoPipe} from '@jsverse/transloco';
import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {EbookReaderComponent} from './ebook-reader.component';
import {mockBookFileServiceProvider} from '../../../../testing/metadata-component-helpers';
import {
  createReaderActivatedRoute,
  mockBookServiceReaderProvider,
  mockEpubCustomFontServiceReaderProvider,
  mockMessageServiceReaderProvider,
  mockReaderAnnotationHttpServiceReaderProvider,
  mockReaderBookmarkServiceReaderProvider,
  mockReaderHeaderServiceReaderProvider,
  mockReaderLeftSidebarServiceReaderProvider,
  mockReaderLoaderServiceReaderProvider,
  mockReaderNoteServiceReaderProvider,
  mockReaderProgressServiceReaderProvider,
  mockReaderSelectionServiceReaderProvider,
  mockReaderSidebarServiceReaderProvider,
  mockReaderStateServiceReaderProvider,
  mockReaderStyleServiceReaderProvider,
  mockReaderViewManagerServiceReaderProvider,
  mockUserServiceReaderProvider,
} from '../../../../testing/reader-component-mocks';

describe('EbookReaderComponent', () => {
  let fixture: ComponentFixture<EbookReaderComponent>;
  let component: EbookReaderComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      providers: [
        createReaderActivatedRoute(),
        mockBookServiceReaderProvider(),
        mockUserServiceReaderProvider(),
        mockBookFileServiceProvider(),
        mockEpubCustomFontServiceReaderProvider(),
      ],
    });

    TestBed.overrideComponent(EbookReaderComponent, {
      set: {
        imports: [CommonModule, TranslocoPipe],
        schemas: [NO_ERRORS_SCHEMA],
        providers: [
          mockMessageServiceReaderProvider(),
          mockReaderLoaderServiceReaderProvider(),
          mockReaderViewManagerServiceReaderProvider(),
          mockReaderStateServiceReaderProvider(),
          mockReaderStyleServiceReaderProvider(),
          mockReaderBookmarkServiceReaderProvider(),
          mockReaderAnnotationHttpServiceReaderProvider(),
          mockReaderProgressServiceReaderProvider(),
          mockReaderSelectionServiceReaderProvider(),
          mockReaderSidebarServiceReaderProvider(),
          mockReaderLeftSidebarServiceReaderProvider(),
          mockReaderHeaderServiceReaderProvider(),
          mockReaderNoteServiceReaderProvider(),
        ],
      },
    });

    fixture = TestBed.createComponent(EbookReaderComponent);
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
    expect((component as any).bookId).toBe(1);
    expect(fixture.nativeElement).toBeTruthy();
  });
});
