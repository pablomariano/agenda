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

internal data class AgendaProject(val id: String, val title: String, val importance: AgendaLevel, val description: String = "")

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
    val blockedBy: String? = null,
    val description: String = "",
)

internal data class AgendaSnapshot(val projects: List<AgendaProject>, val tasks: List<AgendaTask>, val completedTasks: List<AgendaTask>, val futureTasks: List<AgendaTask>)
internal data class AgendaIdea(val id: String, val content: String)

internal data class WeeklyGoal(val id: String, val weekStart: String, val title: String, val version: Int)

internal data class DailyPriorities(val day: String, val version: Int, val ids: List<String>)

internal data class AiRecovery(
    val id: String,
    val provider: String,
    val window: String,
    val expectedAt: String,
    val decision: String,
    val alternative: String?,
    val taskId: String?,
    val cancelled: Boolean,
    val createdAt: String,
    val version: Int,
) {
    fun fields(): JSONObject = JSONObject().put("provider", provider).put("window", window)
        .put("expectedAt", expectedAt).put("decision", decision)
        .put("alternative", alternative ?: JSONObject.NULL).put("taskId", taskId ?: JSONObject.NULL)
        .put("cancelled", cancelled)
}

internal class AgendaHttpError(val status: Int) : IOException()

internal class AgendaApi {
    suspend fun readIdeas(code: String): List<AgendaIdea> = request(code, path = "ideas")
        .getJSONArray("ideas").objects { AgendaIdea(it.getString("id"), it.getString("content")) }

    suspend fun createIdea(code: String, idea: AgendaIdea) {
        val body = JSONObject().put("id", idea.id).put("content", idea.content)
        if (request(code, body, path = "ideas").getString("id") != idea.id) throw IOException("El servidor no confirmó la operación.")
    }

    suspend fun convertIdea(code: String, draft: IdeaConversion) {
        val body = JSONObject().put("id", draft.idea.id).put("resultId", draft.resultId).put("kind", draft.kind)
            .put("projectId", draft.projectId ?: JSONObject.NULL).put("title", draft.title.trim()).put("level", draft.level.value)
        if (request(code, body, "PUT", "ideas").getString("id") != draft.resultId) throw IOException("El servidor no confirmó la operación.")
    }

    suspend fun readGoals(code: String, week: String): List<WeeklyGoal> = request(code, path = "weekly-goals?week=$week")
        .getJSONArray("goals").objects { WeeklyGoal(it.getString("id"), it.getString("weekStart"), it.getString("title"), it.getInt("version")) }

    suspend fun saveGoal(code: String, goal: WeeklyGoal) {
        val body = JSONObject().put("id", goal.id).put("title", goal.title.trim())
        if (goal.version == 0) body.put("weekStart", goal.weekStart) else body.put("version", goal.version)
        if (request(code, body, if (goal.version == 0) "POST" else "PUT", "weekly-goals").getString("id") != goal.id)
            throw IOException("El servidor no confirmó la operación.")
    }

    suspend fun readPriorities(code: String): DailyPriorities = readPriorityResponse(request(code, path = "priorities"))

    suspend fun savePriorities(code: String, original: DailyPriorities, ids: List<String>): DailyPriorities {
        val body = JSONObject().put("day", original.day).put("version", original.version).put("ids", JSONArray(ids))
        return readPriorityResponse(request(code, body, "PUT", "priorities"))
    }

    private fun readPriorityResponse(item: JSONObject): DailyPriorities {
        val ids = item.getJSONArray("ids")
        return DailyPriorities(item.getString("day"), item.getInt("version"), List(ids.length()) { ids.getString(it) })
    }

    suspend fun readRecoveries(code: String): List<AiRecovery> = request(code, path = "recoveries")
        .getJSONArray("recoveries").objects { item ->
            AiRecovery(item.getString("id"), item.getString("provider"), item.getString("window"),
                item.getString("expectedAt"), item.getString("decision"),
                if (item.isNull("alternative")) null else item.getString("alternative"),
                if (item.isNull("taskId")) null else item.getString("taskId"),
                item.getBoolean("cancelled"), item.getString("createdAt"), item.getInt("version"))
        }

    suspend fun saveRecovery(code: String, recovery: AiRecovery, create: Boolean) {
        val body = JSONObject().put("id", recovery.id).put("recovery", recovery.fields())
        if (!create) body.put("version", recovery.version)
        if (request(code, body, if (create) "POST" else "PUT", "recoveries").getString("id") != recovery.id) {
            throw IOException("El servidor no confirmó la operación.")
        }
    }
    suspend fun read(code: String): AgendaSnapshot {
        val json = request(code)
        return AgendaSnapshot(
            projects = json.getJSONArray("projects").objects { item ->
                AgendaProject(item.getString("id"), item.getString("title"), AgendaLevel.read(item.getString("importance")), item.optString("description", ""))
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

    suspend fun updateProject(code: String, original: AgendaProject, title: String, level: AgendaLevel) {
        fun fields(name: String, importance: AgendaLevel) = JSONObject().put("title", name.trim()).put("importance", importance.value)
        val body = JSONObject().put("kind", "project").put("id", original.id)
            .put("expected", fields(original.title, original.importance))
            .put("project", fields(title, level))
        if (request(code, body, "PUT").getString("id") != original.id) throw IOException("El servidor no confirmó la operación.")
    }

    suspend fun update(code: String, original: AgendaTask, title: String, projectId: String, level: AgendaLevel, completed: Boolean, plannedOn: String = original.plannedOn, plannedTime: String? = original.plannedTime, blockedBy: String? = original.blockedBy) {
        fun fields(title: String, projectId: String, level: AgendaLevel, completed: Boolean, date: String, time: String?, block: String?) = JSONObject()
            .put("title", title.trim()).put("projectId", projectId).put("impact", level.value).put("completed", completed)
            .put("plannedOn", date).put("plannedTime", time ?: JSONObject.NULL)
            .put("blockedBy", block ?: JSONObject.NULL)
        val body = JSONObject().put("id", original.id)
            .put("expected", fields(original.title, original.projectId, original.impact, original.completed, original.plannedOn, original.plannedTime, original.blockedBy))
            .put("task", fields(title, projectId, level, completed, plannedOn, plannedTime, blockedBy))
        if (request(code, body, "PUT").getString("id") != original.id) {
            throw IOException("El servidor no confirmó la operación.")
        }
    }

    private suspend fun request(code: String, body: JSONObject? = null, method: String = "POST", path: String = "agenda"): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = URL("${BuildConfig.AGENDA_API_URL}/api/$path").openConnection() as HttpsURLConnection
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
    if (item.isNull("blockedBy")) null else item.getString("blockedBy"),
    item.optString("description", ""),
)
