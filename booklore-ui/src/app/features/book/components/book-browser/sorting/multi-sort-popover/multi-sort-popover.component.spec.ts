import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NO_ERRORS_SCHEMA} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslocoDirective} from '@jsverse/transloco';
import {MultiSortPopoverComponent} from './multi-sort-popover.component';
import {SortDirection, SortOption} from '../../../../model/sort.model';
import {mockTranslocoServiceProvider} from '../../../../../../../testing/providers';

describe('MultiSortPopoverComponent', () => {
  let fixture: ComponentFixture<MultiSortPopoverComponent>;
  let component: MultiSortPopoverComponent;

  const initialCriteria: SortOption[] = [
    {field: 'title', label: 'Title', direction: SortDirection.ASCENDING}
  ];

  const availableOptions: SortOption[] = [
    {field: 'title', label: 'Title', direction: SortDirection.ASCENDING},
    {field: 'author', label: 'Author', direction: SortDirection.ASCENDING}
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MultiSortPopoverComponent],
      providers: [mockTranslocoServiceProvider],
      schemas: [NO_ERRORS_SCHEMA]
    });

    TestBed.overrideComponent(MultiSortPopoverComponent, {
      set: {
        imports: [CommonModule, TranslocoDirective],
        schemas: [NO_ERRORS_SCHEMA]
      }
    });

    fixture = TestBed.createComponent(MultiSortPopoverComponent);
    component = fixture.componentInstance;
    component.sortCriteria = [...initialCriteria];
    component.availableSortOptions = [...availableOptions];
    component.showSaveButton = true;
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

  it('should toggle the sort direction', () => {
    const emitSpy = vi.spyOn(component.criteriaChange, 'emit');
    component.onToggleDirection(0);

    expect(component.sortCriteria[0].direction).toBe(SortDirection.DESCENDING);
    expect(emitSpy).toHaveBeenCalledWith(expect.arrayContaining([
      expect.objectContaining({direction: SortDirection.DESCENDING})
    ]));
  });
});
