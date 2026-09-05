import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslocoDirective} from '@jsverse/transloco';
import {BookUploaderComponent} from './book-uploader.component';
import {
  mockHttpClientProvider,
  mockLibraryServiceProvider,
  mockAppSettingsServiceProvider,
  mockMessageServiceProvider,
  mockDynamicDialogRefProvider,
  mockTranslocoServiceProvider
} from '../../../../testing/providers';

describe('BookUploaderComponent', () => {
  let fixture: ComponentFixture<BookUploaderComponent>;
  let component: BookUploaderComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [BookUploaderComponent],
      providers: [
        mockLibraryServiceProvider,
        mockAppSettingsServiceProvider,
        mockMessageServiceProvider,
        mockDynamicDialogRefProvider,
        mockHttpClientProvider,
        mockTranslocoServiceProvider
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    TestBed.overrideComponent(BookUploaderComponent, {
      set: {
        imports: [CommonModule, TranslocoDirective],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(BookUploaderComponent);
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

  it('should start in library destination mode', () => {
    expect(component.value).toBe('library');
  });
});
