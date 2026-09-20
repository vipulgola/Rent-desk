
package com.get.detail.rentdesk.backup

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

class GoogleDriveClient(private val accessToken: String) {

    fun getAccountInfo(): DriveAccountInfo {
        val response = request(
            method = "GET",
            url = "$API_BASE/about?fields=user(emailAddress,displayName)"
        )
        val user = JsonParser.parseString(response.toString(Charsets.UTF_8))
            .asJsonObject.getAsJsonObject("user")
        return DriveAccountInfo(
            emailAddress = user.get("emailAddress")?.asString.orEmpty(),
            displayName = user.get("displayName")?.asString
        )
    }

    fun listBackups(): List<DriveBackupFile> {
        val query = "name contains '$BACKUP_PREFIX' and trashed = false"
        val url = "$API_BASE/files" + queryString(
            "spaces" to APP_DATA_FOLDER,
            "q" to query,
            "orderBy" to "modifiedTime desc",
            "pageSize" to "100",
            "fields" to "files(id,name,modifiedTime,size)"
        )
        return parseFiles(request("GET", url))
    }

    fun uploadBackup(fileName: String, bytes: ByteArray): DriveBackupFile {
        val file = uploadMultipart(
            fileName = fileName,
            mimeType = JSON_MIME,
            parentId = APP_DATA_FOLDER,
            bytes = bytes
        )
        pruneOldBackups()
        return file
    }

    fun download(fileId: String): ByteArray = request(
        method = "GET",
        url = "$API_BASE/files/${encode(fileId)}?alt=media"
    )

    fun uploadReadableExport(fileName: String, bytes: ByteArray): DriveBackupFile {
        val folderId = findOrCreateExportFolder()
        return uploadMultipart(
            fileName = fileName,
            mimeType = ZIP_MIME,
            parentId = folderId,
            bytes = bytes
        )
    }

    private fun findOrCreateExportFolder(): String {
        val query = "name = '$EXPORT_FOLDER' and mimeType = '$FOLDER_MIME' and trashed = false"
        val listUrl = "$API_BASE/files" + queryString(
            "spaces" to "drive",
            "q" to query,
            "pageSize" to "10",
            "fields" to "files(id,name,modifiedTime,size)"
        )
        parseFiles(request("GET", listUrl)).firstOrNull()?.let { return it.id }

        val metadata = JsonObject().apply {
            addProperty("name", EXPORT_FOLDER)
            addProperty("mimeType", FOLDER_MIME)
        }
        val response = request(
            method = "POST",
            url = "$API_BASE/files?fields=id",
            contentType = JSON_MIME,
            body = metadata.toString().toByteArray(Charsets.UTF_8)
        )
        return JsonParser.parseString(response.toString(Charsets.UTF_8))
            .asJsonObject.get("id").asString
    }

    private fun uploadMultipart(
        fileName: String,
        mimeType: String,
        parentId: String,
        bytes: ByteArray
    ): DriveBackupFile {
        val boundary = "rentdesk-${UUID.randomUUID()}"
        val metadata = JsonObject().apply {
            addProperty("name", fileName)
            addProperty("mimeType", mimeType)
            add("parents", com.google.gson.JsonArray().apply { add(parentId) })
        }
        val body = ByteArrayOutputStream().use { output ->
            output.write("--$boundary\r\n".toByteArray())
            output.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
            output.write(metadata.toString().toByteArray(Charsets.UTF_8))
            output.write("\r\n--$boundary\r\n".toByteArray())
            output.write("Content-Type: $mimeType\r\n\r\n".toByteArray())
            output.write(bytes)
            output.write("\r\n--$boundary--\r\n".toByteArray())
            output.toByteArray()
        }
        val response = request(
            method = "POST",
            url = "$UPLOAD_BASE/files?uploadType=multipart&fields=id,name,modifiedTime,size",
            contentType = "multipart/related; boundary=$boundary",
            body = body
        )
        return parseFile(JsonParser.parseString(response.toString(Charsets.UTF_8)).asJsonObject)
    }

    private fun pruneOldBackups() {
        listBackups().drop(MAX_BACKUPS).forEach { file ->
            runCatching { request("DELETE", "$API_BASE/files/${encode(file.id)}") }
        }
    }

    private fun parseFiles(bytes: ByteArray): List<DriveBackupFile> {
        val root = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
        return root.getAsJsonArray("files")?.map { parseFile(it.asJsonObject) }.orEmpty()
    }

    private fun parseFile(file: JsonObject): DriveBackupFile = DriveBackupFile(
        id = file.get("id").asString,
        name = file.get("name")?.asString.orEmpty(),
        modifiedTime = file.get("modifiedTime")?.asString.orEmpty(),
        size = file.get("size")?.asString?.toLongOrNull() ?: 0L
    )

    private fun request(
        method: String,
        url: String,
        contentType: String? = null,
        body: ByteArray? = null
    ): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 20_000
            connection.readTimeout = 45_000
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType ?: "application/octet-stream")
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }

            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.use { it.readBytes() }
                ?: ByteArray(0)
            if (status !in 200..299) {
                val detail = response.toString(Charsets.UTF_8).take(500)
                error("Google Drive request failed ($status): $detail")
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun queryString(vararg values: Pair<String, String>): String = values.joinToString(
        prefix = "?",
        separator = "&"
    ) { (key, value) -> "${encode(key)}=${encode(value)}" }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        private const val API_BASE = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
        private const val APP_DATA_FOLDER = "appDataFolder"
        private const val EXPORT_FOLDER = "RentDesk Exports"
        private const val BACKUP_PREFIX = "rentdesk_backup_"
        private const val JSON_MIME = "application/json"
        private const val ZIP_MIME = "application/zip"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val MAX_BACKUPS = 5
    }
}
