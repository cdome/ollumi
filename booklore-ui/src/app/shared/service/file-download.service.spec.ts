import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient, HttpHeaders, HttpResponse} from '@angular/common/http';
import {of} from 'rxjs';
import {FileDownloadService} from './file-download.service';
import {mockHttpClientProvider} from '../../../testing/providers';

describe('FileDownloadService', () => {
  let service: FileDownloadService;
  let httpClient: {get: Mock; post: Mock; put: Mock; delete: Mock; patch: Mock; request: Mock};
  let createObjectURLSpy: ReturnType<typeof vi.fn>;
  let revokeObjectURLSpy: ReturnType<typeof vi.fn>;
  let clickSpy: ReturnType<typeof vi.fn>;
  let linkElement: {href: string; download: string; click: ReturnType<typeof vi.fn>};
  let originalCreateObjectURL: typeof URL.createObjectURL;
  let originalRevokeObjectURL: typeof URL.revokeObjectURL;
  let originalCreateElement: typeof document.createElement;

  beforeEach(() => {
    originalCreateObjectURL = URL.createObjectURL;
    originalRevokeObjectURL = URL.revokeObjectURL;
    originalCreateElement = document.createElement;

    createObjectURLSpy = vi.fn(() => 'blob:object-url');
    revokeObjectURLSpy = vi.fn();
    (URL as any).createObjectURL = createObjectURLSpy;
    (URL as any).revokeObjectURL = revokeObjectURLSpy;

    clickSpy = vi.fn();
    linkElement = {href: '', download: '', click: clickSpy};
    (document as any).createElement = vi.fn(() => linkElement as unknown as HTMLAnchorElement);

    TestBed.configureTestingModule({
      providers: [
        FileDownloadService,
        mockHttpClientProvider
      ]
    });

    service = TestBed.inject(FileDownloadService);
    httpClient = TestBed.inject(HttpClient) as any;

    vi.resetAllMocks();
    createObjectURLSpy.mockReturnValue('blob:object-url');
    (document.createElement as unknown as ReturnType<typeof vi.fn>).mockReturnValue(linkElement as unknown as HTMLAnchorElement);
    httpClient.get.mockReturnValue(of(new HttpResponse({body: new Blob(['test']), headers: new HttpHeaders()})));
  });

  afterEach(() => {
    (URL as any).createObjectURL = originalCreateObjectURL;
    (URL as any).revokeObjectURL = originalRevokeObjectURL;
    (document as any).createElement = originalCreateElement;
    vi.restoreAllMocks();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should download a file using the provided filename when no Content-Disposition is present', () => {
    const response = new HttpResponse({
      body: new Blob(['file content']),
      headers: new HttpHeaders()
    });
    httpClient.get.mockReturnValue(of(response));

    service.downloadFile('http://localhost/file', 'default.pdf');

    expect(httpClient.get).toHaveBeenCalledWith('http://localhost/file', {responseType: 'blob', observe: 'response'});
    expect(createObjectURLSpy).toHaveBeenCalledWith(response.body);
    expect(linkElement.href).toBe('blob:object-url');
    expect(linkElement.download).toBe('default.pdf');
    expect(clickSpy).toHaveBeenCalled();
    expect(revokeObjectURLSpy).toHaveBeenCalledWith('blob:object-url');
  });

  it('should extract filename from Content-Disposition header', () => {
    const headers = new HttpHeaders({'Content-Disposition': "filename*=UTF-8''report%20name.pdf"});
    const response = new HttpResponse({
      body: new Blob(['file content']),
      headers
    });
    httpClient.get.mockReturnValue(of(response));

    service.downloadFile('http://localhost/file', 'default.pdf');

    expect(linkElement.download).toBe('report name.pdf');
  });

  it('should fall back to default filename when Content-Disposition is not RFC5987 encoded', () => {
    const headers = new HttpHeaders({'Content-Disposition': 'attachment; filename="plain.pdf"'});
    const response = new HttpResponse({
      body: new Blob(['file content']),
      headers
    });
    httpClient.get.mockReturnValue(of(response));

    service.downloadFile('http://localhost/file', 'default.pdf');

    expect(linkElement.download).toBe('default.pdf');
  });

  it('should do nothing when response body is empty', () => {
    const response = new HttpResponse({
      body: null,
      headers: new HttpHeaders()
    });
    httpClient.get.mockReturnValue(of(response));

    service.downloadFile('http://localhost/file', 'default.pdf');

    expect(createObjectURLSpy).not.toHaveBeenCalled();
    expect(clickSpy).not.toHaveBeenCalled();
    expect(revokeObjectURLSpy).not.toHaveBeenCalled();
  });
});
