package org.booklore.repository.jooq

import org.jooq.OrderField
import org.jooq.Table
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

object PageableHelper {

    @JvmStatic
    fun <T : Any> toPage(content: List<T>, totalCount: Long, pageable: Pageable): Page<T> =
        PageImpl(content, pageable, totalCount)

    @JvmStatic
    fun toOrderFields(sort: Sort, table: Table<*>): Array<OrderField<*>> {
        if (sort.isUnsorted) return emptyArray()
        return sort.mapNotNull { order ->
            // Spring Data sorts by entity property names (camelCase); DB columns are snake_case.
            // Match the exact name first, then fall back to a camelCase->snake_case translation,
            // mirroring how Spring Data JPA resolved sort properties to columns.
            val field = table.field(order.property) ?: table.field(camelToSnake(order.property))
            field?.let { if (order.isAscending) it.asc() else it.desc() }
        }.toTypedArray()
    }

    private val CAMEL_BOUNDARY = Regex("([a-z0-9])([A-Z])")

    private fun camelToSnake(property: String): String =
        property.replace(CAMEL_BOUNDARY, "$1_$2").lowercase()
}
