import {NO_ERRORS_SCHEMA} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {describe, expect, it, vi, beforeEach, afterEach} from 'vitest';
import {TranslocoPipe} from '@jsverse/transloco';
import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {PdfReaderComponent} from './pdf-reader.component';
import {
  createReaderActivatedRoute,
  mockAuthServiceReaderProvider,
  mockBookServiceReaderProvider,
  mockLocationReaderProvider,
  mockMessageServiceReaderProvider,
  mockNgxExtendedPdfViewerServiceReaderProvider,
  mockPageTitleServiceReaderProvider,
  mockPdfAnnotationServiceReaderProvider,
  mockReadingSessionServiceReaderProvider,
  mockUserServiceReaderProvider,
} from '../../../../testing/reader-component-mocks';

describe('PdfReaderComponent', () => {
  let fixture: ComponentFixture<PdfReaderComponent>;
  let component: PdfReaderComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      providers: [
        createReaderActivatedRoute(),
        mockAuthServiceReaderProvider(),
        mockBookServiceReaderProvider(),
        mockUserServiceReaderProvider(),
        mockMessageServiceReaderProvider(),
        mockPageTitleServiceReaderProvider(),
        mockReadingSessionServiceReaderProvider(),
        mockPdfAnnotationServiceReaderProvider(),
        mockNgxExtendedPdfViewerServiceReaderProvider(),
        mockLocationReaderProvider(),
      ],
    });

    TestBed.overrideComponent(PdfReaderComponent, {
      set: {
        imports: [TranslocoPipe],
        schemas: [NO_ERRORS_SCHEMA],
      },
    });

    fixture = TestBed.createComponent(PdfReaderComponent);
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
  });
});
