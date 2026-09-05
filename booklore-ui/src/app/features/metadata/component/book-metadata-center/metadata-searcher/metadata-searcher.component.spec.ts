import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {MetadataSearcherComponent} from './metadata-searcher.component';
import {MetadataPickerComponent} from '../metadata-picker/metadata-picker.component';
import {
  commonMetadataProviders,
  MetadataPickerStubComponent,
  mockActivatedRouteProvider,
  mockBrowserGlobals,
  provideBookSubject
} from '../../../../../../testing/metadata-component-helpers';

describe('MetadataSearcherComponent', () => {
  let fixture: ComponentFixture<MetadataSearcherComponent>;
  let component: MetadataSearcherComponent;

  beforeEach(() => {
    mockBrowserGlobals();

    TestBed.overrideComponent(MetadataSearcherComponent, {
      remove: {imports: [MetadataPickerComponent]},
      add: {imports: [MetadataPickerStubComponent]}
    });

    TestBed.configureTestingModule({
      imports: [MetadataSearcherComponent, TranslocoTestingModule.forRoot({langs: {en: {}}}), NoopAnimationsModule],
      providers: [
        mockActivatedRouteProvider({id: '1'}),
        ...commonMetadataProviders
      ]
    });

    fixture = TestBed.createComponent(MetadataSearcherComponent);
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
