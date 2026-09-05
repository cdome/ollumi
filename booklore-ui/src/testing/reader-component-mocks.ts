import {BehaviorSubject, of} from 'rxjs';
import {vi} from 'vitest';
import {ActivatedRoute} from '@angular/router';
import {Location} from '@angular/common';

import {createMockActivatedRoute} from './book-component-mocks';
import {createMockBook, createMockUser} from './factories';

import {AuthService} from '../app/shared/service/auth.service';
import {MessageService} from 'primeng/api';
import {PageTitleService} from '../app/shared/service/page-title.service';
import {ReadingSessionService} from '../app/shared/service/reading-session.service';
import {PdfAnnotationService} from '../app/shared/service/pdf-annotation.service';
import {NgxExtendedPdfViewerService} from 'ngx-extended-pdf-viewer';
import {TranslocoService} from '@jsverse/transloco';
import {BookService} from '../app/features/book/service/book.service';
import {UserService} from '../app/features/settings/user-management/user.service';
import {CbxReaderService} from '../app/features/book/service/cbx-reader.service';
import {AudiobookService} from '../app/features/readers/audiobook-player/audiobook.service';
import {AudiobookSessionService} from '../app/shared/service/audiobook-session.service';
import {BookMarkService} from '../app/shared/service/book-mark.service';

import {CbxHeaderService} from '../app/features/readers/cbx-reader/layout/header/cbx-header.service';
import {CbxSidebarService} from '../app/features/readers/cbx-reader/layout/sidebar/cbx-sidebar.service';
import {CbxFooterService} from '../app/features/readers/cbx-reader/layout/footer/cbx-footer.service';
import {CbxQuickSettingsService} from '../app/features/readers/cbx-reader/layout/quick-settings/cbx-quick-settings.service';

import {EpubCustomFontService} from '../app/features/readers/ebook-reader/features/fonts/custom-font.service';
import {ReaderLoaderService} from '../app/features/readers/ebook-reader/core/loader.service';
import {ReaderViewManagerService} from '../app/features/readers/ebook-reader/core/view-manager.service';
import {ReaderStateService} from '../app/features/readers/ebook-reader/state/reader-state.service';
import {ReaderStyleService} from '../app/features/readers/ebook-reader/core/style.service';
import {ReaderBookmarkService} from '../app/features/readers/ebook-reader/features/bookmarks/bookmark.service';
import {ReaderAnnotationHttpService} from '../app/features/readers/ebook-reader/features/annotations/annotation.service';
import {ReaderProgressService} from '../app/features/readers/ebook-reader/state/progress.service';
import {ReaderSelectionService} from '../app/features/readers/ebook-reader/features/selection/selection.service';
import {ReaderSidebarService} from '../app/features/readers/ebook-reader/layout/sidebar/sidebar.service';
import {ReaderLeftSidebarService} from '../app/features/readers/ebook-reader/layout/panel/panel.service';
import {ReaderHeaderService} from '../app/features/readers/ebook-reader/layout/header/header.service';
import {ReaderNoteService} from '../app/features/readers/ebook-reader/features/notes/note.service';

/* ========================= ActivatedRoute ========================= */

export function createReaderActivatedRoute(
  params: Record<string, string | null> = {bookId: '1'},
  queryParams: Record<string, string | null> = {}
) {
  return createMockActivatedRoute({path: 'book/:bookId', params, queryParams});
}

/* ========================= Global/Shared services ========================= */

export function mockAuthServiceReaderProvider() {
  return {
    provide: AuthService,
    useValue: {
      token$: of(null),
      tokenSubject: {next: vi.fn(), value: null},
      internalLogin: vi.fn(),
      internalRefreshToken: vi.fn(),
      remoteLogin: vi.fn(),
      logout: vi.fn(),
      forceLogout: vi.fn(),
      getInternalAccessToken: vi.fn(() => 'mock-token'),
      getInternalRefreshToken: vi.fn(() => null),
      saveInternalTokens: vi.fn()
    } as any
  };
}

export function mockMessageServiceReaderProvider() {
  return {
    provide: MessageService,
    useValue: {
      add: vi.fn(),
      clear: vi.fn()
    } as any
  };
}

export function mockPageTitleServiceReaderProvider() {
  return {
    provide: PageTitleService,
    useValue: {
      setPageTitle: vi.fn(),
      setBookPageTitle: vi.fn()
    } as any
  };
}

export function mockTranslocoServiceReaderProvider() {
  return {
    provide: TranslocoService,
    useValue: {
      translate: vi.fn((key: string) => key),
      selectTranslation: vi.fn(() => of({})),
      langChanges$: of('en'),
      getActiveLang: vi.fn(() => 'en'),
      setActiveLang: vi.fn(),
      config: {reRenderOnLangChange: true}
    } as any
  };
}

export function mockLocationReaderProvider() {
  return {
    provide: Location,
    useValue: {back: vi.fn()} as any
  };
}

const mockBook = createMockBook();
const mockUser = createMockUser();

export function mockBookServiceReaderProvider() {
  const bookState$ = new BehaviorSubject<{loaded: boolean; books: unknown[]; error: unknown}>({
    loaded: true,
    books: [mockBook],
    error: null
  });

  return {
    provide: BookService,
    useValue: {
      bookState$: bookState$.asObservable(),
      bookStateSubject: bookState$,
      getCurrentBookState: vi.fn(() => ({loaded: true, books: [mockBook], error: null})),
      getBooksByIdsFromState: vi.fn(() => []),
      getBookByIdFromAPI: vi.fn(() => of(mockBook)),
      getBookByIdFromState: vi.fn(() => mockBook),
      getBookSetting: vi.fn(() => of({})),
      updateViewerSetting: vi.fn(() => of({})),
      savePdfProgress: vi.fn(() => of({})),
      saveCbxProgress: vi.fn(() => of({})),
      getBooksInSeries: vi.fn(() => of([])),
      refreshBooks: vi.fn(),
      readBook: vi.fn(),
      deleteBooks: vi.fn(() => of({})),
      updateBookReadStatus: vi.fn(() => of({})),
      updateBookShelves: vi.fn(() => of({})),
      resetProgress: vi.fn(() => of({}))
    } as any
  };
}

export function mockUserServiceReaderProvider() {
  const userStateSubject = new BehaviorSubject<{user: unknown | null; loaded: boolean; error: unknown}>({
    user: mockUser,
    loaded: true,
    error: null
  });

  return {
    provide: UserService,
    useValue: {
      userState$: userStateSubject.asObservable(),
      userStateSubject,
      getCurrentUser: vi.fn(() => mockUser),
      getMyself: vi.fn(() => of(mockUser)),
      updateUserSetting: vi.fn(() => of({}))
    } as any
  };
}

export function mockReadingSessionServiceReaderProvider() {
  return {
    provide: ReadingSessionService,
    useValue: {
      startSession: vi.fn(),
      updateProgress: vi.fn(),
      endSession: vi.fn(),
      isSessionActive: vi.fn(() => false)
    } as any
  };
}

export function mockAudiobookSessionServiceReaderProvider() {
  return {
    provide: AudiobookSessionService,
    useValue: {
      startSession: vi.fn(),
      pauseSession: vi.fn(),
      resumeSession: vi.fn(),
      endSession: vi.fn(),
      updatePosition: vi.fn(),
      updatePlaybackRate: vi.fn(),
      isSessionActive: vi.fn(() => false),
      isPlaying: vi.fn(() => false)
    } as any
  };
}

export function mockBookMarkServiceReaderProvider() {
  return {
    provide: BookMarkService,
    useValue: {
      getBookmarksForBook: vi.fn(() => of([])),
      createBookmark: vi.fn(() => of({id: 1, bookId: 1, title: 'Mock', createdAt: ''})),
      deleteBookmark: vi.fn(() => of(undefined)),
      updateBookmark: vi.fn(() => of({id: 1, bookId: 1, title: 'Mock', createdAt: ''}))
    } as any
  };
}

/* ========================= PDF reader mocks ========================= */

export function mockPdfAnnotationServiceReaderProvider() {
  return {
    provide: PdfAnnotationService,
    useValue: {
      getAnnotations: vi.fn(() => of({data: '[]'})),
      saveAnnotations: vi.fn(() => of(undefined)),
      deleteAnnotations: vi.fn(() => of(undefined))
    } as any
  };
}

export function mockNgxExtendedPdfViewerServiceReaderProvider() {
  return {
    provide: NgxExtendedPdfViewerService,
    useValue: {
      addEditorAnnotation: vi.fn(),
      getSerializedAnnotations: vi.fn(() => [])
    } as any
  };
}

/* ========================= CBX reader mocks ========================= */

export function mockCbxReaderServiceReaderProvider() {
  return {
    provide: CbxReaderService,
    useValue: {
      getAvailablePages: vi.fn(() => of([1, 2, 3])),
      getPageInfo: vi.fn(() => of([])),
      getPageImageUrl: vi.fn((bookId: number, page: number) => `/api/v1/media/book/${bookId}/cbx/pages/${page}`)
    } as any
  };
}

export function mockCbxHeaderServiceReaderProvider() {
  return {
    provide: CbxHeaderService,
    useValue: {
      initialize: vi.fn(),
      updateState: vi.fn(),
      setForceVisible: vi.fn(),
      forceVisible$: of(true),
      state$: of({isFullscreen: false, isSlideshowActive: false, isMagnifierActive: false}),
      showQuickSettings$: of(),
      toggleBookmark$: of(),
      openNoteDialog$: of(),
      toggleFullscreen$: of(),
      toggleSlideshow$: of(),
      toggleMagnifier$: of(),
      showShortcutsHelp$: of(),
      title: '',
      isVisible: true,
      state: {isFullscreen: false, isSlideshowActive: false, isMagnifierActive: false}
    } as any
  };
}

export function mockCbxSidebarServiceReaderProvider() {
  return {
    provide: CbxSidebarService,
    useValue: {
      initialize: vi.fn(),
      setCurrentPage: vi.fn(),
      toggle: vi.fn(),
      open: vi.fn(),
      close: vi.fn(),
      reset: vi.fn(),
      isPageBookmarked: vi.fn(() => false),
      pageHasNotes: vi.fn(() => false),
      toggleBookmark: vi.fn(),
      createNote: vi.fn(),
      updateNote: vi.fn(),
      isOpen$: of(false),
      activeTab$: of('pages'),
      bookInfo$: of({id: null, title: '', authors: '', coverUrl: null}),
      pages$: of([]),
      currentPage$: of(1),
      bookmarks$: of([]),
      notes$: of([]),
      navigateToPage$: of(),
      editNote$: of(),
      bookmarksChanged$: of(),
      getFilteredNotes: vi.fn(() => [])
    } as any
  };
}

export function mockCbxFooterServiceReaderProvider() {
  return {
    provide: CbxFooterService,
    useValue: {
      updateState: vi.fn(),
      setCurrentPage: vi.fn(),
      setTwoPageView: vi.fn(),
      setSeriesBooks: vi.fn(),
      setHasSeries: vi.fn(),
      setForceVisible: vi.fn(),
      reset: vi.fn(),
      state$: of({currentPage: 0, totalPages: 0, isTwoPageView: false, previousBookInSeries: null, nextBookInSeries: null, hasSeries: false}),
      forceVisible$: of(false),
      previousPage$: of(),
      nextPage$: of(),
      goToPage$: of(),
      firstPage$: of(),
      lastPage$: of(),
      previousBook$: of(),
      nextBook$: of(),
      sliderChange$: of()
    } as any
  };
}

export function mockCbxQuickSettingsServiceReaderProvider() {
  return {
    provide: CbxQuickSettingsService,
    useValue: {
      show: vi.fn(),
      close: vi.fn(),
      reset: vi.fn(),
      updateState: vi.fn(),
      setFitMode: vi.fn(),
      setScrollMode: vi.fn(),
      setPageViewMode: vi.fn(),
      setPageSpread: vi.fn(),
      setBackgroundColor: vi.fn(),
      setReadingDirection: vi.fn(),
      setSlideshowInterval: vi.fn(),
      setMagnifierZoom: vi.fn(),
      setMagnifierLensSize: vi.fn(),
      isVisible: false,
      state$: of({}),
      visible$: of(false),
      fitModeChange$: of(),
      scrollModeChange$: of(),
      pageViewModeChange$: of(),
      pageSpreadChange$: of(),
      backgroundColorChange$: of(),
      readingDirectionChange$: of(),
      slideshowIntervalChange$: of(),
      magnifierZoomChange$: of(),
      magnifierLensSizeChange$: of()
    } as any
  };
}

/* ========================= Audiobook mocks ========================= */

export function mockAudiobookServiceReaderProvider() {
  return {
    provide: AudiobookService,
    useValue: {
      getAudiobookInfo: vi.fn(() => of({
        title: 'Mock Audiobook',
        author: 'Mock Author',
        durationMs: 60000,
        folderBased: false,
        bookFileId: 1,
        chapters: [],
        tracks: []
      })),
      getStreamUrl: vi.fn(() => '/api/v1/audiobooks/1/stream'),
      getTrackStreamUrl: vi.fn(() => '/api/v1/audiobooks/1/track/0/stream'),
      getEmbeddedCoverUrl: vi.fn(() => '/api/v1/audiobooks/1/cover'),
      saveProgress: vi.fn(() => of(undefined))
    } as any
  };
}

/* ========================= Ebook (Foliate) mocks ========================= */

export function mockEpubCustomFontServiceReaderProvider() {
  return {
    provide: EpubCustomFontService,
    useValue: {
      loadAndCacheFonts: vi.fn(() => of([])),
      cleanup: vi.fn()
    } as any
  };
}

export function mockReaderLoaderServiceReaderProvider() {
  return {
    provide: ReaderLoaderService,
    useValue: {
      loadFoliateScript: vi.fn(() => of(undefined)),
      waitForCustomElement: vi.fn(() => of(undefined))
    } as any
  };
}

export function mockReaderViewManagerServiceReaderProvider() {
  const events$ = new BehaviorSubject<{type: string; detail?: unknown}>({type: 'load'});
  return {
    provide: ReaderViewManagerService,
    useValue: {
      createView: vi.fn(),
      destroy: vi.fn(),
      loadEpubStreaming: vi.fn(() => of(undefined)),
      loadEpub: vi.fn(() => of(undefined)),
      getMetadata: vi.fn(() => of(undefined)),
      goTo: vi.fn(() => of(undefined)),
      goToFraction: vi.fn(() => of(undefined)),
      goToSection: vi.fn(() => of(undefined)),
      getRenderer: vi.fn(() => null),
      getSectionFractions: vi.fn(() => []),
      events$: events$.asObservable()
    } as any
  };
}

export function mockReaderStateServiceReaderProvider() {
  const state$ = new BehaviorSubject<any>({
    theme: {bg: '#ffffff'},
    flow: 'paginated'
  });
  return {
    provide: ReaderStateService,
    useValue: {
      initializeState: vi.fn(() => of(undefined)),
      refreshCustomFonts: vi.fn(),
      state$: state$.asObservable(),
      currentState: state$.value
    } as any
  };
}

export function mockReaderStyleServiceReaderProvider() {
  return {
    provide: ReaderStyleService,
    useValue: {
      applyStylesToRenderer: vi.fn()
    } as any
  };
}

export function mockReaderBookmarkServiceReaderProvider() {
  return {
    provide: ReaderBookmarkService,
    useValue: {} as any
  };
}

export function mockReaderAnnotationHttpServiceReaderProvider() {
  return {
    provide: ReaderAnnotationHttpService,
    useValue: {
      reset: vi.fn()
    } as any
  };
}

export function mockReaderProgressServiceReaderProvider() {
  return {
    provide: ReaderProgressService,
    useValue: {
      initialize: vi.fn(),
      handleRelocateEvent: vi.fn(),
      endSession: vi.fn(),
      reset: vi.fn(),
      currentCfi: '',
      currentProgressData: {section: {index: 0, total: 1}, fraction: 0}
    } as any
  };
}

export function mockReaderSelectionServiceReaderProvider() {
  return {
    provide: ReaderSelectionService,
    useValue: {
      initialize: vi.fn(),
      reset: vi.fn(),
      handleTextSelected: vi.fn(),
      handleAction: vi.fn(),
      state$: of({
        visible: false,
        position: {x: 0, y: 0},
        showBelow: false,
        overlappingAnnotationId: null,
        selectedText: ''
      })
    } as any
  };
}

export function mockReaderSidebarServiceReaderProvider() {
  return {
    provide: ReaderSidebarService,
    useValue: {
      initialize: vi.fn(),
      updateChapters: vi.fn(),
      toggle: vi.fn(),
      setCurrentPage: vi.fn(),
      reset: vi.fn(),
      showMetadata$: of(),
      bookmarks$: of([])
    } as any
  };
}

export function mockReaderLeftSidebarServiceReaderProvider() {
  return {
    provide: ReaderLeftSidebarService,
    useValue: {
      initialize: vi.fn(),
      toggle: vi.fn(),
      reset: vi.fn()
    } as any
  };
}

export function mockReaderHeaderServiceReaderProvider() {
  return {
    provide: ReaderHeaderService,
    useValue: {
      initialize: vi.fn(),
      setForceVisible: vi.fn(),
      setCurrentCfiBookmarked: vi.fn(),
      setFullscreen: vi.fn(),
      reset: vi.fn(),
      showControls$: of(),
      showMetadata$: of(),
      toggleFullscreen$: of(),
      showShortcutsHelp$: of()
    } as any
  };
}

export function mockReaderNoteServiceReaderProvider() {
  return {
    provide: ReaderNoteService,
    useValue: {
      initialize: vi.fn(),
      openNewNoteDialog: vi.fn(),
      closeDialog: vi.fn(),
      saveNote: vi.fn(),
      reset: vi.fn(),
      dialogState$: of({visible: false, data: null})
    } as any
  };
}
