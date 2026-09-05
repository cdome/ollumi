import {runPermissionGuardTests} from './permission-guard.spec-helper';
import {BookdropGuard} from './bookdrop.guard';

runPermissionGuardTests('BookdropGuard', BookdropGuard, 'canAccessBookdrop');
