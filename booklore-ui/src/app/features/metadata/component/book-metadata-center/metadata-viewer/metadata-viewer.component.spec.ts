import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {MetadataViewerComponent} from './metadata-viewer.component';
import {MetadataTabsComponent} from './metadata-tabs/metadata-tabs.component';
import {
  commonMetadataProviders,
  MetadataTabsStubComponent,
  mockActivatedRouteProvider,
  mockBrowserGlobals,
  provideBookSubject
} from '../../../../../../testing/metadata-component-helpers';

describe('MetadataViewerComponent', () => {
  let fixture: ComponentFixture<MetadataViewerComponent>;
  let component: MetadataViewerComponent;

  beforeEach(() => {
    mockBrowserGlobals();

    TestBed.overrideComponent(MetadataViewerComponent, {
      remove: {imports: [MetadataTabsComponent]},
      add: {imports: [MetadataTabsStubComponent]}
    });

    TestBed.configureTestingModule({
      imports: [MetadataViewerComponent, TranslocoTestingModule.forRoot({langs: {en: {}}}), NoopAnimationsModule],
      providers: [
        mockActivatedRouteProvider(),
        ...commonMetadataProviders
      ]
    });

    fixture = TestBed.createComponent(MetadataViewerComponent);
    component = fixture.componentInstance;
    component.book$ = provideBookSubject().asObservable();
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
