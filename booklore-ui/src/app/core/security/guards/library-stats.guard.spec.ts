import {runPermissionGuardTests} from './permission-guard.spec-helper';
import {LibraryStatsGuard} from './library-stats.guard';

runPermissionGuardTests('LibraryStatsGuard', LibraryStatsGuard, 'canAccessLibraryStats');
