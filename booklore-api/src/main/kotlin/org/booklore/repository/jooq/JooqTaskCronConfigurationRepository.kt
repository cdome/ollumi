package org.booklore.repository.jooq

import org.booklore.jooq.tables.TaskCronConfiguration.TASK_CRON_CONFIGURATION
import org.booklore.jooq.tables.records.TaskCronConfigurationRecord
import org.booklore.model.dto.response.CronConfig
import org.booklore.model.enums.TaskType
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class JooqTaskCronConfigurationRepository(private val dsl: DSLContext) {

    private val t = TASK_CRON_CONFIGURATION

    fun findAllEnabled(): List<CronConfig> =
        dsl.selectFrom(t).where(t.ENABLED.eq(1.toByte())).fetch().map(::toDto)

    fun findByTaskType(taskType: TaskType): CronConfig? =
        dsl.selectFrom(t).where(t.TASK_TYPE.eq(taskType.name)).fetchOne()?.let(::toDto)

    /** Upsert by task type: updates cron_expression/enabled when a row exists, else inserts. */
    fun save(taskType: TaskType, cronExpression: String?, enabled: Boolean, createdBy: Long): CronConfig {
        val now = LocalDateTime.now()
        val existingId = dsl.select(t.ID).from(t).where(t.TASK_TYPE.eq(taskType.name)).fetchOne(t.ID)
        if (existingId != null) {
            dsl.update(t)
                .set(t.CRON_EXPRESSION, cronExpression)
                .set(t.ENABLED, enabled.toByteFlag())
                .set(t.UPDATED_AT, now)
                .where(t.ID.eq(existingId))
                .execute()
        } else {
            dsl.insertInto(t)
                .set(t.TASK_TYPE, taskType.name)
                .set(t.CRON_EXPRESSION, cronExpression)
                .set(t.ENABLED, enabled.toByteFlag())
                .set(t.CREATED_BY, createdBy)
                .set(t.CREATED_AT, now)
                .set(t.UPDATED_AT, now)
                .execute()
        }
        return findByTaskType(taskType)!!
    }

    private fun toDto(r: TaskCronConfigurationRecord): CronConfig =
        CronConfig.builder()
            .id(r.id)
            .taskType(TaskType.valueOf(r.taskType))
            .cronExpression(r.cronExpression)
            .enabled(r.enabled == 1.toByte())
            .createdAt(r.createdAt)
            .updatedAt(r.updatedAt)
            .build()

    private fun Boolean.toByteFlag(): Byte = if (this) 1 else 0
}
