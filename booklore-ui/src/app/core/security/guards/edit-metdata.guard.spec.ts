import {runPermissionGuardTests} from './permission-guard.spec-helper';
import {EditMetadataGuard} from './edit-metdata.guard';

runPermissionGuardTests('EditMetadataGuard', EditMetadataGuard, 'canEditMetadata');
