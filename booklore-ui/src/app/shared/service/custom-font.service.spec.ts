import {afterEach, beforeEach, describe, expect, it, vi, type Mock} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {of} from 'rxjs';
import {CustomFontService} from './custom-font.service';
import {AuthService} from './auth.service';
import {mockAuthServiceProvider, mockHttpClientProvider} from '../../../testing/providers';
import {CustomFont, FontFormat} from '../model/custom-font.model';

describe('CustomFontService', () => {
  let service: CustomFontService;
  let httpClient: {get: Mock; post: Mock; put: Mock; delete: Mock; patch: Mock; request: Mock};
  const baseUrl = 'http://localhost:6060/api/v1/custom-fonts';
  let fontsSet: {add: Mock; delete: Mock; [Symbol.iterator]: () => IterableIterator<{family: string}>};

  function createFont(overrides: Partial<CustomFont> = {}): CustomFont {
    return {
      id: 1,
      fontName: 'TestFont',
      originalFileName: 'test.ttf',
      format: FontFormat.TTF,
      fileSize: 1234,
      uploadedAt: '2024-01-01T00:00:00Z',
      ...overrides
    };
  }

  beforeEach(() => {
    const fontsArray: Array<{family: string}> = [];
    fontsSet = {
      add: vi.fn(face => fontsArray.push(face)),
      delete: vi.fn(face => {
        const idx = fontsArray.indexOf(face);
        if (idx > -1) {
          fontsArray.splice(idx, 1);
        }
      }),
      [Symbol.iterator]: () => fontsArray[Symbol.iterator]()
    };

    (document as any).fonts = fontsSet;
    (globalThis as any).FontFace = vi.fn(function (family: string, source: string) {
      return {
        family,
        source,
        load: vi.fn().mockResolvedValue(undefined),
        status: 'loaded'
      };
    });

    TestBed.configureTestingModule({
      providers: [
        CustomFontService,
        mockHttpClientProvider,
        mockAuthServiceProvider
      ]
    });

    service = TestBed.inject(CustomFontService);
    httpClient = TestBed.inject(HttpClient) as any;

    const auth = TestBed.inject(AuthService);
    (auth.getInternalAccessToken as Mock).mockReturnValue(null);

    httpClient.get.mockReset();
    httpClient.get.mockReturnValue(of([]));
    httpClient.post.mockReset();
    httpClient.post.mockReturnValue(of(createFont()));
    httpClient.delete.mockReset();
    httpClient.delete.mockReturnValue(of(undefined));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should start with an empty font list', () => {
    let result: CustomFont[] | undefined;
    service.fonts$.subscribe(f => result = f);

    expect(result).toEqual([]);
  });

  it('should build the font file URL', () => {
    expect(service.getFontUrl(5)).toBe(`${baseUrl}/5/file`);
  });

  it('should append a token when one is available', () => {
    const auth = TestBed.inject(AuthService);
    (auth.getInternalAccessToken as Mock).mockReturnValue('abc123');

    expect(service.appendToken(`${baseUrl}/5/file`)).toBe(`${baseUrl}/5/file?token=abc123`);
  });

  it('should not append a token when one is unavailable', () => {
    expect(service.appendToken(`${baseUrl}/5/file`)).toBe(`${baseUrl}/5/file`);
  });

  it('should fetch and cache user fonts', () => {
    const fonts = [createFont({id: 1}), createFont({id: 2, fontName: 'Other'})];
    httpClient.get.mockReturnValue(of(fonts));

    const emitted: CustomFont[][] = [];
    service.fonts$.subscribe(f => emitted.push(f));

    service.getUserFonts().subscribe();

    expect(httpClient.get).toHaveBeenCalledWith(baseUrl);
    expect(emitted[emitted.length - 1]).toEqual(fonts);
  });

  it('should upload a font, cache it, and load it', async () => {
    const file = new File(['content'], 'upload.ttf', {type: 'font/ttf'});
    const uploaded = createFont({id: 3, fontName: 'Uploaded'});
    httpClient.post.mockReturnValue(of(uploaded));

    const emitted: CustomFont[][] = [];
    service.fonts$.subscribe(f => emitted.push(f));

    service.uploadFont(file, 'Uploaded').subscribe();
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(httpClient.post).toHaveBeenCalledWith(`${baseUrl}/upload`, expect.any(FormData));
    expect(emitted[emitted.length - 1]).toEqual([uploaded]);
    expect((globalThis as any).FontFace).toHaveBeenCalledWith(
      'Uploaded',
      expect.stringContaining(`${baseUrl}/3/file`),
      {weight: 'normal', style: 'normal'}
    );
    expect(service.isFontLoaded('Uploaded')).toBe(true);
  });

  it('should delete a font and unload it', () => {
    const font1 = createFont({id: 1, fontName: 'Keep'});
    const font2 = createFont({id: 2, fontName: 'Remove'});
    (service as any)['fontsSubject'].next([font1, font2]);
    (service as any)['loadedFonts'].add('Remove');

    const face = {family: 'Remove', source: '', load: vi.fn(), status: 'loaded'};
    fontsSet.add(face);

    service.deleteFont(2).subscribe();

    expect(httpClient.delete).toHaveBeenCalledWith(`${baseUrl}/2`);

    let last: CustomFont[] = [];
    service.fonts$.subscribe(f => last = f);
    expect(last).toEqual([font1]);
    expect(service.isFontLoaded('Remove')).toBe(false);
    expect(fontsSet.delete).toHaveBeenCalledWith(face);
  });

  it('should skip loading a font that is already loaded', async () => {
    const font = createFont({id: 1});
    (service as any)['loadedFonts'].add(font.fontName);

    await service.loadFontFace(font);

    expect((globalThis as any).FontFace).not.toHaveBeenCalled();
  });

  it('should load all fonts', async () => {
    const fonts = [createFont({id: 1}), createFont({id: 2, fontName: 'Two'})];

    await service.loadAllFonts(fonts);

    expect((globalThis as any).FontFace).toHaveBeenCalledTimes(2);
    expect(service.isFontLoaded('TestFont')).toBe(true);
    expect(service.isFontLoaded('Two')).toBe(true);
  });

  it('should report font loaded status', () => {
    expect(service.isFontLoaded('Missing')).toBe(false);

    (service as any)['loadedFonts'].add('Present');

    expect(service.isFontLoaded('Present')).toBe(true);
  });
});
