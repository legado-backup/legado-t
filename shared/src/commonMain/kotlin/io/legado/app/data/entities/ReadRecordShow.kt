package io.legado.app.data.entities

data class ReadRecordShow(
    var bookName: String,
    var readTime: Long,
    var lastRead: Long,
    var author: String = "",
    var coverUrl: String? = null,
    var bookUrl: String = "",
    var coverOrigin: String = ""
)
