import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslocoDirective, TranslocoPipe} from '@jsverse/transloco';
import {BehaviorSubject, of} from 'rxjs';
import {DirectoryPickerComponent} from './directory-picker.component';
import {UtilityService} from './utility.service';
import {mockDynamicDialogRefProvider, mockTranslocoServiceProvider} from '../../../../testing/providers';

describe('DirectoryPickerComponent', () => {
  let fixture: ComponentFixture<DirectoryPickerComponent>;
  let component: DirectoryPickerComponent;

  const foldersSubject = new BehaviorSubject<string[]>([]);

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [DirectoryPickerComponent],
      providers: [
        {provide: UtilityService, useValue: {getFolders: vi.fn(() => foldersSubject.asObservable())}},
        mockDynamicDialogRefProvider,
        mockTranslocoServiceProvider
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    TestBed.overrideComponent(DirectoryPickerComponent, {
      set: {
        imports: [CommonModule, TranslocoDirective, TranslocoPipe],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(DirectoryPickerComponent);
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

  it('should load folders on init', () => {
    expect(component.isLoading).toBe(true);
  });
});
