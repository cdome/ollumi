package org.booklore.repository.jooq

import org.booklore.jooq.tables.UserEmailProviderPreference.USER_EMAIL_PROVIDER_PREFERENCE
import org.booklore.jooq.tables.records.UserEmailProviderPreferenceRecord
import org.booklore.repository.jooq.dto.UserEmailProviderPreference
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
class JooqUserEmailProviderPreferenceRepository(private val dsl: DSLContext) {

    private val t = USER_EMAIL_PROVIDER_PREFERENCE

    fun findByUserId(userId: Long): Optional<UserEmailProviderPreference> =
        Optional.ofNullable(dsl.selectFrom(t).where(t.USER_ID.eq(userId)).fetchOne()?.let(::toDto))

    fun findAllByDefaultProviderId(defaultProviderId: Long): List<UserEmailProviderPreference> =
        dsl.selectFrom(t).where(t.DEFAULT_PROVIDER_ID.eq(defaultProviderId)).fetch().map(::toDto)

    /** Upsert keyed by the unique user_id: update the row if the user already has a preference, else insert. */
    fun upsertDefaultProvider(userId: Long, defaultProviderId: Long) {
        val updated = dsl.update(t)
            .set(t.DEFAULT_PROVIDER_ID, defaultProviderId)
            .where(t.USER_ID.eq(userId))
            .execute()
        if (updated == 0) {
            dsl.insertInto(t)
                .set(t.USER_ID, userId)
                .set(t.DEFAULT_PROVIDER_ID, defaultProviderId)
                .execute()
        }
    }

    fun updateDefaultProviderById(id: Long, defaultProviderId: Long) {
        dsl.update(t).set(t.DEFAULT_PROVIDER_ID, defaultProviderId).where(t.ID.eq(id)).execute()
    }

    fun deleteById(id: Long) {
        dsl.deleteFrom(t).where(t.ID.eq(id)).execute()
    }

    private fun toDto(r: UserEmailProviderPreferenceRecord): UserEmailProviderPreference =
        UserEmailProviderPreference(
            id = r.id,
            userId = r.userId,
            defaultProviderId = r.defaultProviderId,
        )
}
