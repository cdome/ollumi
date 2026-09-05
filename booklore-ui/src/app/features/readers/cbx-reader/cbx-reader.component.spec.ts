import {NO_ERRORS_SCHEMA} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {describe, expect, it, vi, beforeEach, afterEach} from 'vitest';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TranslocoPipe} from '@jsverse/transloco';
import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {CbxReaderComponent} from './cbx-reader.component';
import {mockRouterProvider} from '../../../../testing/providers';
import {
  createReaderActivatedRoute,
  mockBookServiceReaderProvider,
  mockCbxFooterServiceReaderProvider,
  mockCbxHeaderServiceReaderProvider,
  mockCbxQuickSettingsServiceReaderProvider,
  mockCbxReaderServiceReaderProvider,
  mockCbxSidebarServiceReaderProvider,
  mockMessageServiceReaderProvider,
  mockPageTitleServiceReaderProvider,
  mockReadingSessionServiceReaderProvider,
  mockUserServiceReaderProvider,
} from '../../../../testing/reader-component-mocks';

describe('CbxReaderComponent', () => {
  let fixture: ComponentFixture<CbxReaderComponent>;
  let component: CbxReaderComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      providers: [
        createReaderActivatedRoute(),
        mockRouterProvider,
        mockBookServiceReaderProvider(),
        mockUserServiceReaderProvider(),
        mockCbxReaderServiceReaderProvider(),
        mockMessageServiceReaderProvider(),
        mockPageTitleServiceReaderProvider(),
        mockReadingSessionServiceReaderProvider(),
      ],
    });

    TestBed.overrideComponent(CbxReaderComponent, {
      set: {
        imports: [CommonModule, FormsModule, TranslocoPipe],
        schemas: [NO_ERRORS_SCHEMA],
        providers: [
          mockCbxHeaderServiceReaderProvider(),
          mockCbxSidebarServiceReaderProvider(),
          mockCbxFooterServiceReaderProvider(),
          mockCbxQuickSettingsServiceReaderProvider(),
        ],
      },
    });

    fixture = TestBed.createComponent(CbxReaderComponent);
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
