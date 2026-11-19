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
    val plannedTime: String? = null,
    val completed: Boolean = false,
    val completedToday: Boolean = false,
)

internal data class AgendaSnapshot(val projects: List<AgendaProject>, val tasks: List<AgendaTask>, val completedTasks: List<AgendaTask>, val futureTasks: List<AgendaTask>)

internal class AgendaHttpError(val status: Int) : IOException()

internal class AgendaApi {
    suspend fun read(code: String): AgendaSnapshot {
        val json = request(code)
        return AgendaSnapshot(
            projects = json.getJSONArray("projects").objects { item ->
                AgendaProject(item.getString("id"), item.getString("title"), AgendaLevel.read(item.getString("importance")))
            },
            tasks = json.getJSONArray("tasks").objects(::readTask),
            completedTasks = json.optJSONArray("completedTasks")?.objects(::readTask).orEmpty(),
            futureTasks = json.optJSONArray("futureTasks")?.objects(::readTask).orEmpty(),
        )
    }

    suspend fun create(code: String, draft: AgendaDraft) {
        val body = JSONObject()
            .put("kind", if (draft.projectId == null) "project" else "task")
            .put("id", draft.id)
            .put("title", draft.title.trim())
            .put("level", draft.level.value)
            .put("projectId", draft.projectId)
        if (draft.projectId != null) body.put("plannedOn", draft.plannedOn).put("plannedTime", draft.plannedTime ?: JSONObject.NULL)
        if (request(code, body).getString("id") != draft.id) {
            throw IOException("El servidor no confirmó la operación.")
        }
    }

    suspend fun update(code: String, original: AgendaTask, title: String, projectId: String, level: AgendaLevel, completed: Boolean, plannedOn: String = original.plannedOn, plannedTime: String? = original.plannedTime) {
        fun fields(title: String, projectId: String, level: AgendaLevel, completed: Boolean, date: String, time: String?) = JSONObject()
            .put("title", title.trim()).put("projectId", projectId).put("impact", level.value).put("completed", completed)
            .put("plannedOn", date).put("plannedTime", time ?: JSONObject.NULL)
        val body = JSONObject().put("id", original.id)
            .put("expected", fields(original.title, original.projectId, original.impact, original.completed, original.plannedOn, original.plannedTime))
            .put("task", fields(title, projectId, level, completed, plannedOn, plannedTime))
        if (request(code, body, "PUT").getString("id") != original.id) {
            throw IOException("El servidor no confirmó la operación.")
        }
    }

    private suspend fun request(code: String, body: JSONObject? = null, method: String = "POST"): JSONObject =
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
                    connection.requestMethod = method
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setFixedLengthStreamingMode(bytes.size)
                    connection.outputStream.use { it.write(bytes) }
                }
                val status = connection.responseCode
                if (status != if (body == null || method == "PUT") 200 else 201) throw AgendaHttpError(status)
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
            } finally {
                connection.disconnect()
            }
        }
}

private fun <T> JSONArray.objects(read: (JSONObject) -> T): List<T> =
    List(length()) { index -> read(getJSONObject(index)) }

private fun readTask(item: JSONObject) = AgendaTask(
    item.getString("id"), item.getString("title"), item.getString("projectId"),
    item.getString("projectTitle"), AgendaLevel.read(item.getString("impact")),
    item.getString("plannedOn"), item.getBoolean("carriedOver"),
    if (item.isNull("plannedTime")) null else item.getString("plannedTime"),
    item.optBoolean("completed", false), item.optBoolean("completedToday", false),
)
