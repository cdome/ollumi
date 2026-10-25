package org.booklore.service.task;

import org.booklore.repository.jooq.JooqTaskHistoryRepository;
import org.booklore.repository.jooq.dto.TaskHistory;
import org.booklore.service.audit.AuditService;
import org.booklore.task.TaskStatus;
import org.booklore.model.dto.response.TasksHistoryResponse;
import org.booklore.model.enums.TaskType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskHistoryServiceTest {

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2025, 1, 1, 12, 0, 0);

    @Mock
    private JooqTaskHistoryRepository taskHistoryRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private TaskHistoryService taskHistoryService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    private TaskHistory history(String id, TaskType type, TaskStatus status, Integer progress, LocalDateTime createdAt) {
        return new TaskHistory(id, type, status, progress, null, createdAt, null, null);
    }

    @Test
    void testCreateTask_insertsRow() {
        String taskId = "task1";
        TaskType type = TaskType.REFRESH_LIBRARY_METADATA;
        Long userId = 123L;
        Map<String, Object> options = new HashMap<>();
        options.put("key", "value");

        taskHistoryService.createTask(taskId, type, userId, options);

        verify(taskHistoryRepository, times(1)).insert(
                eq(taskId), eq(type), eq(TaskStatus.ACCEPTED), eq(userId), any(), eq(0), eq(options));
    }

    @Test
    void testUpdateTaskStatus_terminalCompletesRow() {
        String taskId = "task2";

        taskHistoryService.updateTaskStatus(taskId, TaskStatus.COMPLETED, "Done");

        verify(taskHistoryRepository).completeStatus(eq(taskId), eq(TaskStatus.COMPLETED), eq("Done"), any(), any(), eq(100));
        verify(taskHistoryRepository, never()).updateStatus(any(), any(), any(), any());
    }

    @Test
    void testUpdateTaskStatus_nonTerminalUpdatesStatusOnly() {
        String taskId = "task2b";

        taskHistoryService.updateTaskStatus(taskId, TaskStatus.IN_PROGRESS, "Working");

        verify(taskHistoryRepository).updateStatus(eq(taskId), eq(TaskStatus.IN_PROGRESS), eq("Working"), any());
        verify(taskHistoryRepository, never()).completeStatus(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void testUpdateTaskError_updatesRow() {
        String taskId = "task3";

        taskHistoryService.updateTaskError(taskId, "Some error");

        verify(taskHistoryRepository).updateError(eq(taskId), eq("Some error"), any(), any());
    }

    @Test
    void testGetLatestTasksForEachType_success() {
        TaskHistory importTask = history("t1", TaskType.REFRESH_LIBRARY_METADATA, TaskStatus.COMPLETED, 100, FIXED_TIME);
        TaskHistory exportTask = history("t2", TaskType.SYNC_LIBRARY_FILES, TaskStatus.ACCEPTED, 50, FIXED_TIME.plusMinutes(5));

        when(taskHistoryRepository.findLatestTaskForEachType())
                .thenReturn(Arrays.asList(importTask, exportTask));

        TasksHistoryResponse response = taskHistoryService.getLatestTasksForEachType();

        assertNotNull(response);
        List<TasksHistoryResponse.TaskHistory> histories = response.getTaskHistories();
        assertTrue(histories.stream().anyMatch(h -> TaskType.REFRESH_LIBRARY_METADATA.equals(h.getType()) && "t1".equals(h.getId())));
        assertTrue(histories.stream().anyMatch(h -> TaskType.SYNC_LIBRARY_FILES.equals(h.getType()) && "t2".equals(h.getId())));
        assertFalse(histories.stream().anyMatch(h -> h.getType() != null && h.getType().isHiddenFromUI()));
        assertTrue(histories.stream().anyMatch(h -> h.getId() == null));
    }

    @Test
    void testGetLatestTasksForEachType_exceptionHandled() {
        when(taskHistoryRepository.findLatestTaskForEachType()).thenThrow(new RuntimeException("DB error"));
        TasksHistoryResponse response = taskHistoryService.getLatestTasksForEachType();
        assertNotNull(response);
        assertTrue(response.getTaskHistories().stream().allMatch(h -> h.getId() == null));
    }

    @Test
    void testCreateTask_withNullOptions() {
        String taskId = "taskNullOptions";
        TaskType type = TaskType.CLEANUP_TEMP_METADATA;
        Long userId = 456L;

        taskHistoryService.createTask(taskId, type, userId, null);

        verify(taskHistoryRepository, times(1)).insert(
                eq(taskId), eq(type), eq(TaskStatus.ACCEPTED), eq(userId), any(), eq(0), isNull());
    }

    @Test
    void testUpdateTaskStatus_withNullMessage() {
        String taskId = "taskNullMsg";

        taskHistoryService.updateTaskStatus(taskId, TaskStatus.COMPLETED, null);

        verify(taskHistoryRepository).completeStatus(eq(taskId), eq(TaskStatus.COMPLETED), isNull(), any(), any(), eq(100));
    }

    @Test
    void testUpdateTaskError_withNullErrorDetails() {
        String taskId = "taskNullError";

        taskHistoryService.updateTaskError(taskId, null);

        verify(taskHistoryRepository).updateError(eq(taskId), isNull(), any(), any());
    }

    @Test
    void testCreateTask_withNullType() {
        String taskId = "taskNullType";
        Long userId = 789L;
        Map<String, Object> options = new HashMap<>();

        assertDoesNotThrow(() -> taskHistoryService.createTask(taskId, null, userId, options));
        verify(taskHistoryRepository, times(1)).insert(
                eq(taskId), isNull(), eq(TaskStatus.ACCEPTED), eq(userId), any(), eq(0), eq(options));
    }

    @Test
    void testGetLatestTasksForEachType_emptyRepository() {
        when(taskHistoryRepository.findLatestTaskForEachType()).thenReturn(Collections.emptyList());
        TasksHistoryResponse response = taskHistoryService.getLatestTasksForEachType();
        assertNotNull(response);
        assertTrue(response.getTaskHistories().stream().allMatch(h -> h.getId() == null));
    }

    @Test
    void testGetLatestTasksForEachType_allTypesHidden() {
        List<TaskType> hiddenTypes = Arrays.asList(TaskType.values());
        TaskHistory dummyTask = history("dummy", TaskType.CLEANUP_DELETED_BOOKS, TaskStatus.FAILED, 0, FIXED_TIME);
        when(taskHistoryRepository.findLatestTaskForEachType()).thenReturn(Collections.singletonList(dummyTask));

        hiddenTypes.forEach(type -> {
            try {
                java.lang.reflect.Field field = TaskType.class.getDeclaredField("hiddenFromUI");
                field.setAccessible(true);
                field.set(type, true);
            } catch (Exception ignored) {
            }
        });

        TasksHistoryResponse response = taskHistoryService.getLatestTasksForEachType();
        assertNotNull(response);
        assertTrue(response.getTaskHistories().isEmpty());
    }

    // --- buildTaskDescription tests (via createTask audit log capture) ---

    private String captureAuditDescription(TaskType type, Map<String, Object> options) {
        taskHistoryService.createTask("test-task", type, 1L, options);
        ArgumentCaptor<String> descCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).log(any(), any(), any(), descCaptor.capture());
        reset(auditService);
        return descCaptor.getValue();
    }

    @Test
    void testBuildTaskDescription_withSmallBookIdsList() {
        Map<String, Object> options = new HashMap<>();
        options.put("bookIds", Set.of(10L, 20L, 30L));

        String desc = captureAuditDescription(TaskType.REFRESH_METADATA_MANUAL, options);

        assertTrue(desc.startsWith("Started task:"));
        assertTrue(desc.contains("3 books, IDs:"));
        assertTrue(desc.contains("10"));
        assertTrue(desc.contains("20"));
        assertTrue(desc.contains("30"));
        assertTrue(desc.endsWith(")"));
        assertFalse(desc.contains("..."));
    }

    @Test
    void testBuildTaskDescription_withSingleBookId() {
        Map<String, Object> options = new HashMap<>();
        options.put("bookIds", Set.of(42L));

        String desc = captureAuditDescription(TaskType.REFRESH_METADATA_MANUAL, options);

        assertTrue(desc.contains("1 books, IDs: 42)"));
    }

    @Test
    void testBuildTaskDescription_withLibraryId() {
        Map<String, Object> options = new HashMap<>();
        options.put("libraryId", 7L);

        String desc = captureAuditDescription(TaskType.REFRESH_LIBRARY_METADATA, options);

        assertTrue(desc.contains("Library ID: 7"));
    }

    @Test
    void testBuildTaskDescription_withEmptyBookIds() {
        Map<String, Object> options = new HashMap<>();
        options.put("bookIds", Collections.emptySet());

        String desc = captureAuditDescription(TaskType.REFRESH_METADATA_MANUAL, options);

        assertFalse(desc.contains("books"));
        assertFalse(desc.contains("Library ID"));
    }

    @Test
    void testBuildTaskDescription_withNullOptions() {
        String desc = captureAuditDescription(TaskType.REFRESH_METADATA_MANUAL, null);

        assertEquals("Started task: Refresh Metadata", desc);
    }

    @Test
    void testBuildTaskDescription_withEmptyOptions() {
        String desc = captureAuditDescription(TaskType.REFRESH_METADATA_MANUAL, new HashMap<>());

        assertEquals("Started task: Refresh Metadata", desc);
    }

    @Test
    void testBuildTaskDescription_withNullType() {
        Map<String, Object> options = new HashMap<>();
        options.put("bookIds", Set.of(1L));

        String desc = captureAuditDescription(null, options);

        assertTrue(desc.startsWith("Started task: Unknown"));
    }

    @Test
    void testBuildTaskDescription_bookIdsPrefersOverLibraryId() {
        Map<String, Object> options = new HashMap<>();
        options.put("bookIds", Set.of(1L, 2L));
        options.put("libraryId", 5L);

        String desc = captureAuditDescription(TaskType.REFRESH_METADATA_MANUAL, options);

        assertTrue(desc.contains("2 books, IDs:"));
        assertFalse(desc.contains("Library ID"));
    }

    @Test
    void testBuildTaskDescription_largeBookIdsListTruncated() {
        Set<Long> ids = LongStream.rangeClosed(1, 500)
                .boxed()
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Object> options = new HashMap<>();
        options.put("bookIds", ids);

        String desc = captureAuditDescription(TaskType.REFRESH_METADATA_MANUAL, options);

        assertTrue(desc.length() <= 1024, "Description length " + desc.length() + " exceeds 1024");
        assertTrue(desc.contains("500 books, IDs:"));
        assertTrue(desc.endsWith("...)"));
    }

    @Test
    void testBuildTaskDescription_veryLargeBookIdsListStaysWithinLimit() {
        Set<Long> ids = LongStream.rangeClosed(1, 2000)
                .boxed()
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Object> options = new HashMap<>();
        options.put("bookIds", ids);

        String desc = captureAuditDescription(TaskType.REFRESH_METADATA_MANUAL, options);

        assertTrue(desc.length() <= 1024, "Description length " + desc.length() + " exceeds 1024");
        assertTrue(desc.contains("2000 books, IDs:"));
        assertTrue(desc.endsWith("...)"));
    }
}
