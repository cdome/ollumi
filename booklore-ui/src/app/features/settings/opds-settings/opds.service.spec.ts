import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {of} from 'rxjs';
import {OpdsService, type OpdsSortOrder, type OpdsUserV2CreateRequest} from './opds.service';
import {mockHttpClientProvider} from '../../../../testing/providers';

describe('OpdsService', () => {
  let service: OpdsService;
  let httpMock: {
    get: ReturnType<typeof vi.fn>;
    post: ReturnType<typeof vi.fn>;
    patch: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        OpdsService,
        mockHttpClientProvider
      ]
    });

    service = TestBed.inject(OpdsService);
    httpMock = TestBed.inject(HttpClient) as unknown as typeof httpMock;

    vi.resetAllMocks();
    httpMock.get.mockReturnValue(of([]));
    httpMock.post.mockReturnValue(of({}));
    httpMock.patch.mockReturnValue(of({}));
    httpMock.delete.mockReturnValue(of(undefined));
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch OPDS users', () => {
    let result: unknown;
    httpMock.get.mockReturnValue(of([{id: 1}]));

    service.getUser().subscribe(value => {
      result = value;
    });

    expect(httpMock.get).toHaveBeenCalledWith('http://localhost:6060/api/v2/opds-users');
    expect(result).toEqual([{id: 1}]);
  });

  it('should create an OPDS user', () => {
    let result: unknown;
    const request: OpdsUserV2CreateRequest = {username: 'opds', password: 'secret', sortOrder: 'TITLE_ASC'};
    httpMock.post.mockReturnValue(of({id: 7, username: 'opds'}));

    service.createUser(request).subscribe(value => {
      result = value;
    });

    expect(httpMock.post).toHaveBeenCalledWith(
      'http://localhost:6060/api/v2/opds-users',
      request
    );
    expect(result).toEqual({id: 7, username: 'opds'});
  });

  it('should update an OPDS user sort order', () => {
    const sortOrder: OpdsSortOrder = 'AUTHOR_DESC';
    let result: unknown;
    httpMock.patch.mockReturnValue(of({id: 3, sortOrder}));

    service.updateUser(3, sortOrder).subscribe(value => {
      result = value;
    });

    expect(httpMock.patch).toHaveBeenCalledWith(
      'http://localhost:6060/api/v2/opds-users/3',
      {sortOrder}
    );
    expect(result).toEqual({id: 3, sortOrder});
  });

  it('should delete an OPDS credential', () => {
    service.deleteCredential(5).subscribe();

    expect(httpMock.delete).toHaveBeenCalledWith('http://localhost:6060/api/v2/opds-users/5');
  });
});
