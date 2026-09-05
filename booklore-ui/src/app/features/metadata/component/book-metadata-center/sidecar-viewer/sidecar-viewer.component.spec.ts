import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {BehaviorSubject, of} from 'rxjs';
import {TranslocoTestingModule} from '@jsverse/transloco';
import {SidecarViewerComponent} from './sidecar-viewer.component';
import {SidecarService} from '../../../service/sidecar.service';
import {MessageService} from 'primeng/api';
import {createMockBook, commonComponentTestProviders} from '../../../../../../testing';

describe('SidecarViewerComponent', () => {
  let fixture: ComponentFixture<SidecarViewerComponent>;
  let component: SidecarViewerComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SidecarViewerComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        ...commonComponentTestProviders,
        {provide: SidecarService, useValue: {
          getSyncStatus: vi.fn(() => of({status: 'MISSING'})),
          getSidecarContent: vi.fn(() => of({})),
          exportToSidecar: vi.fn(() => of(undefined)),
          importFromSidecar: vi.fn(() => of(undefined))
        }},
        {provide: MessageService, useValue: {add: vi.fn(), clear: vi.fn()}}
      ],
      schemas: [NO_ERRORS_SCHEMA]
    });

    fixture = TestBed.createComponent(SidecarViewerComponent);
    component = fixture.componentInstance;
    component.book$ = of(createMockBook());
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
});
