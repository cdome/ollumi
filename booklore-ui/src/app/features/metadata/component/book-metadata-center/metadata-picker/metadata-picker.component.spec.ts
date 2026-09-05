import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {MetadataPickerComponent} from './metadata-picker.component';
import {MetadataFormBuilder} from '../../../../../shared/metadata';
import {MetadataUtilsService} from '../../../../../shared/metadata';
import {
  commonMetadataProviders,
  createMockBookMetadata,
  mockActivatedRouteProvider,
  mockBrowserGlobals,
  provideBookSubject
} from '../../../../../../testing/metadata-component-helpers';

describe('MetadataPickerComponent', () => {
  let fixture: ComponentFixture<MetadataPickerComponent>;
  let component: MetadataPickerComponent;

  beforeEach(() => {
    mockBrowserGlobals();

    TestBed.configureTestingModule({
      imports: [MetadataPickerComponent, TranslocoTestingModule.forRoot({langs: {en: {}}}), NoopAnimationsModule],
      providers: [
        mockActivatedRouteProvider(),
        MetadataFormBuilder,
        MetadataUtilsService,
        ...commonMetadataProviders
      ]
    });

    fixture = TestBed.createComponent(MetadataPickerComponent);
    component = fixture.componentInstance;
    component.reviewMode = false;
    component.fetchedMetadata = createMockBookMetadata({bookId: 1});
    component.book$ = provideBookSubject().asObservable();
    component.detailLoading = false;
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
