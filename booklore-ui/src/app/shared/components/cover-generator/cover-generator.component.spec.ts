import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {CoverGeneratorComponent} from './cover-generator.component';

describe('CoverGeneratorComponent', () => {
  let fixture: ComponentFixture<CoverGeneratorComponent>;
  let component: CoverGeneratorComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CoverGeneratorComponent]
    });

    fixture = TestBed.createComponent(CoverGeneratorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    TestBed.resetTestingModule();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should generate a base64 SVG data URL for an ebook', () => {
    component.title = 'Test Title';
    component.author = 'Test Author';
    component.isSquare = false;

    const cover = component.generateCover();
    expect(cover).toMatch(/^data:image\/svg\+xml;base64,/);
  });

  it('should generate a square audiobook cover when requested', () => {
    component.title = 'Audio Title';
    component.author = 'Audio Author';
    component.isSquare = true;

    const cover = component.generateCover();
    expect(cover).toMatch(/^data:image\/svg\+xml;base64,/);
  });
});
