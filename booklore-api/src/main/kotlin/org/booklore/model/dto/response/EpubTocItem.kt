package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator

data class EpubTocItem constructor(
        val label: String? = null,
        val href: String? = null,
        val children: List<EpubTocItem?>? = null
)
