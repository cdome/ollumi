import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {BookMetadataCenterComponent} from './book-metadata-center.component';
import {MetadataViewerComponent} from './metadata-viewer/metadata-viewer.component';
import {MetadataEditorComponent} from './metadata-editor/metadata-editor.component';
import {MetadataSearcherComponent} from './metadata-searcher/metadata-searcher.component';
import {SidecarViewerComponent} from './sidecar-viewer/sidecar-viewer.component';
import {
  commonMetadataProviders,
  MetadataEditorStubComponent,
  MetadataSearcherStubComponent,
  MetadataViewerStubComponent,
  mockActivatedRouteProvider,
  mockBrowserGlobals,
  SidecarViewerStubComponent
} from '../../../../../testing/metadata-component-helpers';

describe('BookMetadataCenterComponent', () => {
  let fixture: ComponentFixture<BookMetadataCenterComponent>;
  let component: BookMetadataCenterComponent;

  beforeEach(() => {
    mockBrowserGlobals();

    TestBed.overrideComponent(BookMetadataCenterComponent, {
      remove: {imports: [MetadataViewerComponent, MetadataEditorComponent, MetadataSearcherComponent, SidecarViewerComponent]},
      add: {imports: [MetadataViewerStubComponent, MetadataEditorStubComponent, MetadataSearcherStubComponent, SidecarViewerStubComponent]}
    });

    TestBed.configureTestingModule({
      imports: [BookMetadataCenterComponent, TranslocoTestingModule.forRoot({langs: {en: {}}}), NoopAnimationsModule],
      providers: [
        mockActivatedRouteProvider({bookId: '1'}, {tab: 'view'}),
        ...commonMetadataProviders
      ]
    });

    fixture = TestBed.createComponent(BookMetadataCenterComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
