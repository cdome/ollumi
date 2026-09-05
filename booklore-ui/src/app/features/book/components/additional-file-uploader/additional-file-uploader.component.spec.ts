import {beforeEach, afterEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {AdditionalFileUploaderComponent} from './additional-file-uploader.component';
import {BookFileService} from '../../service/book-file.service';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {commonComponentTestProviders} from '../../../../../testing';
import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {createMockBook} from '../../../../../testing/factories';
import {AdditionalFile, AdditionalFileType} from '../../model/book.model';
import {AppSettings} from '../../../../shared/model/app-settings.model';
import {BehaviorSubject, of} from 'rxjs';

describe('AdditionalFileUploaderComponent', () => {
  let fixture: ComponentFixture<AdditionalFileUploaderComponent>;
  let component: AdditionalFileUploaderComponent;
  const appSettingsSubject = new BehaviorSubject<AppSettings | null>({maxFileUploadSizeInMb: 100} as AppSettings);

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [getTranslocoModule()],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        ...commonComponentTestProviders.filter(p => p.provide !== TranslocoService),
        {provide: DynamicDialogRef, useValue: {close: vi.fn()}},
        {provide: DynamicDialogConfig, useValue: {data: {book: createMockBook()}}},
        {
          provide: BookFileService,
          useValue: {
            uploadAdditionalFile: vi.fn(() => of({id: 1, fileName: 'file.epub', bookType: 'EPUB', fileSizeKb: 100} as AdditionalFile))
          }
        },
        {
          provide: AppSettingsService,
          useValue: {appSettings$: appSettingsSubject.asObservable()}
        }
      ]
    });

    fixture = TestBed.createComponent(AdditionalFileUploaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should create and render without error', () => {
    expect(component).toBeTruthy();
    expect(component.fileType).toBe(AdditionalFileType.ALTERNATIVE_FORMAT);
    expect(fixture.nativeElement).toBeTruthy();
  });
});
