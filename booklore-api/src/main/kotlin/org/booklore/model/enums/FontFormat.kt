package org.booklore.model.enums

enum class FontFormat(val mimeType: String, val extension: String) {
    TTF("font/ttf", ".ttf"),
    OTF("font/otf", ".otf"),
    WOFF("font/woff", ".woff"),
    WOFF2("font/woff2", ".woff2");

    companion object {
        @JvmStatic
        fun fromExtension(extension: String): FontFormat {
            var normalizedExt = extension.lowercase()
            if (!normalizedExt.startsWith(".")) {
                normalizedExt = "." + normalizedExt
            }
            for (format in entries) {
                if (format.extension == normalizedExt) {
                    return format
                }
            }
            throw IllegalArgumentException("Unsupported font format: $extension")
        }

        @JvmStatic
        fun fromMimeType(mimeType: String): FontFormat {
            for (format in entries) {
                if (format.mimeType == mimeType) {
                    return format
                }
            }
            throw IllegalArgumentException("Unsupported MIME type: $mimeType")
        }

        @JvmStatic
        fun isSupportedExtension(extension: String): Boolean {
            var normalizedExt = extension.lowercase()
            if (!normalizedExt.startsWith(".")) {
                normalizedExt = "." + normalizedExt
            }
            for (format in entries) {
                if (format.extension == normalizedExt) {
                    return true
                }
            }
            return false
        }
    }
}
