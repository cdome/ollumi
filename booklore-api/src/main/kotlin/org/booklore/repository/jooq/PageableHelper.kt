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
            table.field(order.property)?.let { field ->
                if (order.isAscending) field.asc() else field.desc()
            }
        }.toTypedArray()
    }
}
