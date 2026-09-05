import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { TranslocoTestingModule } from '@jsverse/transloco';

import { UploadDialogComponent } from './upload-dialog.component';
import { BackgroundUploadService } from '../background-upload.service';
import { DynamicDialogRef } from 'primeng/dynamicdialog';

describe('UploadDialogComponent', () => {
  let fixture: ComponentFixture<UploadDialogComponent>;
  let component: UploadDialogComponent;

  let backgroundUploadServiceMock: { uploadFile: ReturnType<typeof vi.fn>; uploadUrl: ReturnType<typeof vi.fn> };
  let dialogRefMock: { close: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    backgroundUploadServiceMock = {
      uploadFile: vi.fn(() => of('https://example.com/uploaded.png')),
      uploadUrl: vi.fn(() => of('https://example.com/uploaded.png')),
    };
    dialogRefMock = { close: vi.fn() };

    TestBed.configureTestingModule({
      imports: [UploadDialogComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        { provide: BackgroundUploadService, useValue: backgroundUploadServiceMock },
        { provide: DynamicDialogRef, useValue: dialogRefMock },
      ]
    });

    fixture = TestBed.createComponent(UploadDialogComponent);
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

  it('should store the selected file', () => {
    const file = new File([''], 'test.png', { type: 'image/png' });
    const target = { files: [file] } as unknown as HTMLInputElement;

    component.onFileSelected({ target } as unknown as Event);

    expect(component.uploadFile).toBe(file);
    expect(component.uploadImageUrl).toBe('');
  });

  it('should upload a file and close the dialog on success', () => {
    component.uploadFile = new File([''], 'test.png', { type: 'image/png' });
    component.submit();

    expect(backgroundUploadServiceMock.uploadFile).toHaveBeenCalled();
    expect(dialogRefMock.close).toHaveBeenCalledWith({ imageUrl: 'https://example.com/uploaded.png' });
  });

  it('should upload from a URL and close the dialog on success', () => {
    component.uploadImageUrl = 'https://example.com/image.png';
    component.submit();

    expect(backgroundUploadServiceMock.uploadUrl).toHaveBeenCalledWith('https://example.com/image.png');
    expect(dialogRefMock.close).toHaveBeenCalledWith({ imageUrl: 'https://example.com/uploaded.png' });
  });

  it('should show an error when no input is provided', () => {
    component.uploadFile = null;
    component.uploadImageUrl = '   ';
    component.submit();

    expect(component.uploadError).toBe('en.layout.uploadDialog.errorNoInput');
    expect(dialogRefMock.close).not.toHaveBeenCalled();
  });

  it('should display an error when the upload fails', () => {
    backgroundUploadServiceMock.uploadUrl.mockReturnValue(throwError(() => new Error('upload failed')));
    component.uploadImageUrl = 'https://example.com/image.png';
    component.submit();

    expect(component.uploadError).toBe('Upload failed. Please try again.');
  });

  it('should close the dialog when cancelled', () => {
    component.cancel();
    expect(dialogRefMock.close).toHaveBeenCalledWith();
  });
});
