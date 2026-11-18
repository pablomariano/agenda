package com.pablomariano.agenda

import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal enum class AgendaLevel(val value: String, val importance: String, val impact: String) {
    HIGH("alta", "Alta", "Alto"),
    MEDIUM("media", "Media", "Medio"),
    LOW("baja", "Baja", "Bajo");

    companion object {
        fun read(value: String): AgendaLevel = entries.firstOrNull { it.value == value }
            ?: throw IOException("Nivel inválido en la respuesta.")
    }
}

internal data class AgendaProject(val id: String, val title: String, val importance: AgendaLevel)

internal data class AgendaTask(
    val id: String,
    val title: String,
    val projectId: String,
    val projectTitle: String,
    val impact: AgendaLevel,
    val plannedOn: String,
    val carriedOver: Boolean,
)

internal data class AgendaSnapshot(val projects: List<AgendaProject>, val tasks: List<AgendaTask>)

internal class AgendaHttpError(val status: Int) : IOException()

internal class AgendaApi {
    suspend fun read(code: String): AgendaSnapshot {
        val json = request(code)
        return AgendaSnapshot(
            projects = json.getJSONArray("projects").objects { item ->
                AgendaProject(item.getString("id"), item.getString("title"), AgendaLevel.read(item.getString("importance")))
            },
            tasks = json.getJSONArray("tasks").objects { item ->
                AgendaTask(
                    item.getString("id"),
                    item.getString("title"),
                    item.getString("projectId"),
                    item.getString("projectTitle"),
                    AgendaLevel.read(item.getString("impact")),
                    item.getString("plannedOn"),
                    item.getBoolean("carriedOver"),
                )
            },
        )
    }

    suspend fun create(code: String, draft: AgendaDraft) {
        val body = JSONObject()
            .put("kind", if (draft.projectId == null) "project" else "task")
            .put("id", draft.id)
            .put("title", draft.title.trim())
            .put("level", draft.level.value)
            .put("projectId", draft.projectId)
        if (request(code, body).getString("id") != draft.id) {
            throw IOException("El servidor no confirmó la operación.")
        }
    }

    private suspend fun request(code: String, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = URL("${BuildConfig.AGENDA_API_URL}/api/agenda").openConnection() as HttpsURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.setRequestProperty("Authorization", "Bearer $code")
                connection.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    val bytes = body.toString().toByteArray(Charsets.UTF_8)
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setFixedLengthStreamingMode(bytes.size)
                    connection.outputStream.use { it.write(bytes) }
                }
                val status = connection.responseCode
                if (status != if (body == null) 200 else 201) throw AgendaHttpError(status)
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
            } finally {
                connection.disconnect()
            }
        }
}

private fun <T> JSONArray.objects(read: (JSONObject) -> T): List<T> =
    List(length()) { index -> read(getJSONObject(index)) }
