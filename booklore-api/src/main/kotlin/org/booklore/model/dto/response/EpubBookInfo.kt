package org.booklore.model.dto.response

import com.fasterxml.jackson.annotation.JsonCreator

data class EpubBookInfo constructor(
        val containerPath: String? = null,
        val rootPath: String? = null,
        val spine: List<EpubSpineItem?>? = null,
        val manifest: List<EpubManifestItem?>? = null,
        val toc: EpubTocItem? = null,
        val metadata: Map<String?, Object?>? = null,
        val coverPath: String? = null
)
