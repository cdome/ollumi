import {runPermissionGuardTests} from './permission-guard.spec-helper';
import {ManageLibraryGuard} from './manage-library.guard';

runPermissionGuardTests('ManageLibraryGuard', ManageLibraryGuard, 'canManageLibrary');
