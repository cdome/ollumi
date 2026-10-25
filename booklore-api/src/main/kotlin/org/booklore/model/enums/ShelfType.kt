package org.booklore.model.enums

enum class ShelfType(val displayName: String, val icon: String) {
    KOBO("Kobo", "pi pi-tablet");

    fun getName(): String = displayName
}
