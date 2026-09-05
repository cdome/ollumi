package org.booklore.repository.jooq

import org.assertj.core.api.Assertions.assertThat
import org.booklore.jooq.tables.EmailProviderV2.EMAIL_PROVIDER_V2
import org.booklore.jooq.tables.UserPermissions.USER_PERMISSIONS
import org.booklore.jooq.tables.Users.USERS
import org.booklore.repository.jooq.dto.EmailProviderV2Row
import org.booklore.test.AbstractIntegrationTest
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class JooqEmailProviderV2RepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var repository: JooqEmailProviderV2Repository
    @Autowired private lateinit var dsl: DSLContext

    private var adminUserId: Long = 0
    private var userId: Long = 0
    private var otherUserId: Long = 0

    @BeforeEach
    fun setUp() {
        // child-first: providers reference users; permissions reference users.
        dsl.deleteFrom(EMAIL_PROVIDER_V2).execute()
        dsl.deleteFrom(USER_PERMISSIONS).execute()
        dsl.deleteFrom(USERS).execute()
        adminUserId = insertUser("admin", admin = true)
        userId = insertUser("owner", admin = false)
        otherUserId = insertUser("other", admin = false)
    }

    @Test
    fun `insert then findByIdAndUserId round-trips all fields including byte-boolean flags and password`() {
        val row = EmailProviderV2Row(
            id = 0L,
            userId = userId,
            name = "Primary",
            host = "smtp.example.com",
            port = 465,
            username = "sender@example.com",
            password = "s3cr3t",
            fromAddress = "noreply@example.com",
            auth = true,
            startTls = false,
            defaultProvider = true,
            shared = false,
        )

        val saved = repository.insert(row)
        assertThat(saved.id).isPositive()

        val found = repository.findByIdAndUserId(saved.id, userId)!!
        assertThat(found.userId).isEqualTo(userId)
        assertThat(found.name).isEqualTo("Primary")
        assertThat(found.host).isEqualTo("smtp.example.com")
        assertThat(found.port).isEqualTo(465)
        assertThat(found.username).isEqualTo("sender@example.com")
        assertThat(found.password).isEqualTo("s3cr3t")
        assertThat(found.fromAddress).isEqualTo("noreply@example.com")
        assertThat(found.auth).isTrue()
        assertThat(found.startTls).isFalse()
        assertThat(found.defaultProvider).isTrue()
        assertThat(found.shared).isFalse()

        // Verify boolean flags are physically stored as TINYINT 1/0 (bool -> byte mapping).
        val record = dsl.selectFrom(EMAIL_PROVIDER_V2).where(EMAIL_PROVIDER_V2.ID.eq(saved.id)).fetchOne()!!
        assertThat(record.auth).isEqualTo(1.toByte())
        assertThat(record.startTls).isEqualTo(0.toByte())
        assertThat(record.isDefault).isEqualTo(1.toByte())
        assertThat(record.shared).isEqualTo(0.toByte())
    }

    @Test
    fun `insert persists a null from_address`() {
        val saved = repository.insert(providerRow(userId, "No From", shared = false, fromAddress = null))
        val found = repository.findByIdAndUserId(saved.id, userId)!!
        assertThat(found.fromAddress as String?).isNull()
    }

    @Test
    fun `findByIdAndUserId returns null when absent`() {
        assertThat(repository.findByIdAndUserId(9999L, userId)).isNull()
    }

    @Test
    fun `findByIdAndUserId returns null for the wrong user`() {
        val saved = repository.insert(providerRow(userId, "Owned", shared = false))
        assertThat(repository.findByIdAndUserId(saved.id, otherUserId)).isNull()
    }

    @Test
    fun `findAllByUserId returns only that user's providers ordered by id`() {
        val first = repository.insert(providerRow(userId, "Alpha", shared = false)).id
        val second = repository.insert(providerRow(userId, "Beta", shared = false)).id
        repository.insert(providerRow(otherUserId, "Gamma", shared = false))

        val result = repository.findAllByUserId(userId)
        assertThat(result.map { it.id }).containsExactly(first, second)
    }

    @Test
    fun `update round-trips changed fields`() {
        val saved = repository.insert(providerRow(userId, "Original", shared = false))

        val updated = repository.update(
            EmailProviderV2Row(
                id = saved.id,
                userId = userId,
                name = "Renamed",
                host = "smtp.new.com",
                port = 587,
                username = "new@new.com",
                password = "newpass",
                fromAddress = "from@new.com",
                auth = false,
                startTls = true,
                defaultProvider = false,
                shared = true,
            )
        )

        assertThat(updated.name).isEqualTo("Renamed")
        assertThat(updated.host).isEqualTo("smtp.new.com")
        assertThat(updated.port).isEqualTo(587)
        assertThat(updated.username).isEqualTo("new@new.com")
        assertThat(updated.password).isEqualTo("newpass")
        assertThat(updated.fromAddress).isEqualTo("from@new.com")
        assertThat(updated.auth).isFalse()
        assertThat(updated.startTls).isTrue()
        assertThat(updated.shared).isTrue()

        // re-read to confirm the update was persisted, not just echoed
        val reread = repository.findByIdAndUserId(saved.id, userId)!!
        assertThat(reread.name).isEqualTo("Renamed")
        assertThat(reread.startTls).isTrue()
        assertThat(reread.shared).isTrue()
    }

    @Test
    fun `deleteById removes the row`() {
        val saved = repository.insert(providerRow(userId, "Doomed", shared = false))
        repository.deleteById(saved.id)
        assertThat(repository.findByIdAndUserId(saved.id, userId)).isNull()
    }

    @Test
    fun `count reflects the number of stored providers`() {
        assertThat(repository.count()).isZero()
        repository.insert(providerRow(userId, "One", shared = false))
        repository.insert(providerRow(otherUserId, "Two", shared = false))
        assertThat(repository.count()).isEqualTo(2L)
    }

    // --- shared / admin queries -------------------------------------------------

    @Test
    fun `findAllBySharedTrueAndAdmin returns only shared providers owned by admins`() {
        val adminShared = repository.insert(providerRow(adminUserId, "admin-shared", shared = true)).id
        repository.insert(providerRow(adminUserId, "admin-private", shared = false))
        repository.insert(providerRow(userId, "user-shared", shared = true))

        val result = repository.findAllBySharedTrueAndAdmin()
        assertThat(result.map { it.id }).containsExactly(adminShared)
        assertThat(result[0].shared).isTrue()
    }

    @Test
    fun `findSharedProviderById honours the shared flag and admin ownership`() {
        val adminShared = repository.insert(providerRow(adminUserId, "admin-shared", shared = true)).id
        val adminPrivate = repository.insert(providerRow(adminUserId, "admin-private", shared = false)).id
        val userShared = repository.insert(providerRow(userId, "user-shared", shared = true)).id

        assertThat(repository.findSharedProviderById(adminShared)?.id).isEqualTo(adminShared)
        // not shared -> null
        assertThat(repository.findSharedProviderById(adminPrivate)).isNull()
        // shared but owner is not an admin -> null
        assertThat(repository.findSharedProviderById(userShared)).isNull()
    }

    @Test
    fun `findAccessibleProvider allows the owner to access their own provider`() {
        val userPrivate = repository.insert(providerRow(userId, "user-private", shared = false)).id
        assertThat(repository.findAccessibleProvider(userPrivate, userId)?.id).isEqualTo(userPrivate)
    }

    @Test
    fun `findAccessibleProvider allows another user to access an admin's shared provider`() {
        val adminShared = repository.insert(providerRow(adminUserId, "admin-shared", shared = true)).id
        assertThat(repository.findAccessibleProvider(adminShared, userId)?.id).isEqualTo(adminShared)
    }

    @Test
    fun `findAccessibleProvider denies access when not owner and not an admin-shared provider`() {
        // admin's private provider is not accessible to a different user
        val adminPrivate = repository.insert(providerRow(adminUserId, "admin-private", shared = false)).id
        assertThat(repository.findAccessibleProvider(adminPrivate, userId)).isNull()

        // a shared provider owned by a non-admin is not accessible to a third user
        val userShared = repository.insert(providerRow(userId, "user-shared", shared = true)).id
        assertThat(repository.findAccessibleProvider(userShared, otherUserId)).isNull()
    }

    // --- helpers ----------------------------------------------------------------

    private fun providerRow(
        ownerId: Long,
        name: String,
        shared: Boolean,
        fromAddress: String? = "from@test.com",
    ): EmailProviderV2Row =
        EmailProviderV2Row(
            id = 0L,
            userId = ownerId,
            name = name,
            host = "smtp.test.com",
            port = 587,
            username = "user@test.com",
            password = "pw",
            fromAddress = fromAddress,
            auth = true,
            startTls = true,
            defaultProvider = false,
            shared = shared,
        )

    private fun insertUser(username: String, admin: Boolean): Long {
        val id = dsl.insertInto(USERS)
            .set(USERS.USERNAME, username)
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.IS_DEFAULT_PASSWORD, 0.toByte())
            .set(USERS.NAME, username)
            .returningResult(USERS.ID).fetchOne()!!.get(USERS.ID)!!
        dsl.insertInto(USER_PERMISSIONS)
            .set(USER_PERMISSIONS.USER_ID, id)
            .set(USER_PERMISSIONS.PERMISSION_ADMIN, if (admin) 1.toByte() else 0.toByte())
            .execute()
        return id
    }
}
