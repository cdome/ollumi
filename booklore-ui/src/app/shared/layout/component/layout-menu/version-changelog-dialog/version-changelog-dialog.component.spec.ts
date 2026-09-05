import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { TranslocoTestingModule } from '@jsverse/transloco';

import { VersionChangelogDialogComponent } from './version-changelog-dialog.component';
import { VersionService } from '../../../../service/version.service';
import { DynamicDialogRef } from 'primeng/dynamicdialog';

describe('VersionChangelogDialogComponent', () => {
  let fixture: ComponentFixture<VersionChangelogDialogComponent>;
  let component: VersionChangelogDialogComponent;

  const mockReleaseNotes = [
    {
      version: 'v1.0.0',
      name: 'Initial Release',
      changelog: '## Features\n- First release',
      url: 'https://github.com/booklore-app/booklore/releases/tag/v1.0.0',
      publishedAt: '2024-01-01T00:00:00Z',
    },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [VersionChangelogDialogComponent, TranslocoTestingModule.forRoot({langs: {}})],
      providers: [
        { provide: VersionService, useValue: { getChangelog: vi.fn(() => of(mockReleaseNotes)) } },
        { provide: DynamicDialogRef, useValue: { close: vi.fn() } },
      ]
    });

    fixture = TestBed.createComponent(VersionChangelogDialogComponent);
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

  it('should load the changelog on init', () => {
    const versionService = TestBed.inject(VersionService) as any;
    expect(versionService.getChangelog).toHaveBeenCalled();
    expect(component.changelog).toEqual(mockReleaseNotes);
    expect(component.loading).toBe(false);
  });

  it('should convert markdown to sanitized html', () => {
    const html = component.markdownToHtml('## Hello');
    expect(html).toContain('<h3');
    expect(html).toContain('Hello');
  });
});
