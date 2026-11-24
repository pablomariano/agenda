package com.pablomariano.agenda

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch
import org.json.JSONException

internal data class AgendaDraft(
    val projectId: String?,
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val level: AgendaLevel = AgendaLevel.MEDIUM,
    val plannedOn: String = LocalDate.now(ZoneId.of("America/Santiago")).toString(),
    val plannedTime: String? = null,
    val submitted: Boolean = false,
    val original: AgendaTask? = null,
    val originalProject: AgendaProject? = null,
)

internal data class IdeaConversion(
    val idea: AgendaIdea,
    val resultId: String = UUID.randomUUID().toString(),
    val kind: String = "task",
    val title: String = "",
    val level: AgendaLevel = AgendaLevel.MEDIUM,
    val projectId: String? = null,
)

internal enum class AgendaPage { TODAY, PROJECTS, PROJECT, AI, PRIORITIES, WEEK, IDEAS }

private val RecoveryInstant = DateTimeFormatterBuilder().appendInstant(3).toFormatter()

internal class PersonalAgendaModel(application: Application) : AndroidViewModel(application) {
    private val api = AgendaApi()
    private val store = AccessCodeStore(application)
    val reminders = TaskReminders(application)
    private var pendingTaskId: String? = null
    private var pendingRecovery = false

    private var code: String? by mutableStateOf(null)
    val signedIn: Boolean get() = code != null
    var data: AgendaSnapshot? by mutableStateOf(null)
        private set
    var page by mutableStateOf(AgendaPage.TODAY)
        private set
    var projectId: String? by mutableStateOf(null)
        private set
    var draft: AgendaDraft? by mutableStateOf(null)
        private set
    var selectedTaskId: String? by mutableStateOf(null)
        private set
    val selectedTask: AgendaTask? get() = (data?.tasks.orEmpty() + data?.futureTasks.orEmpty() + data?.completedTasks.orEmpty()).find { it.id == selectedTaskId }
    var busy by mutableStateOf(false)
        private set
    var error: String? by mutableStateOf(null)
        private set
    var recoveries: List<AiRecovery>? by mutableStateOf(null)
        private set
    var recoveryDraft: AiRecovery? by mutableStateOf(null)
        private set
    var recoveryIsNew by mutableStateOf(false)
        private set
    var recoverySubmitted by mutableStateOf(false)
        private set
    var priorities: DailyPriorities? by mutableStateOf(null)
        private set
    var priorityDraft: List<String>? by mutableStateOf(null)
        private set
    var prioritySubmitted by mutableStateOf(false)
        private set

    var weekStart by mutableStateOf(LocalDate.now(ZoneId.of("America/Santiago")).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
        private set
    var weeklyGoals: List<WeeklyGoal>? by mutableStateOf(null)
        private set
    var goalDraft: WeeklyGoal? by mutableStateOf(null)
        private set
    var goalSubmitted by mutableStateOf(false)
        private set

    var ideas: List<AgendaIdea>? by mutableStateOf(null)
        private set
    var ideaDraft: AgendaIdea? by mutableStateOf(null)
        private set
    var ideaConversion: IdeaConversion? by mutableStateOf(null)
        private set
    var ideaSubmitted by mutableStateOf(false)
        private set

    init {
        operation {
            store.read()?.let {
                code = it
                load(it)
            }
        }
    }

    fun signIn(value: String) {
        if (busy) return
        val entered = value.trim().lowercase(Locale.ROOT)
        if (!entered.matches(Regex("[0-9a-f]{64}"))) {
            error = "Introduce el código de Agenda de 64 caracteres."
            return
        }
        operation {
            load(entered)
            code = entered
            if (!store.write(entered)) {
                error = "Acceso confirmado. El dispositivo no pudo guardar el código para la próxima vez."
            }
        }
    }

    fun signOut() = operation {
        reminders.clear()
        code = null
        data = null
        recoveries = null
        recoveryDraft = null
        priorities = null
        priorityDraft = null
        weeklyGoals = null
        goalDraft = null
        ideas = null
        ideaDraft = null
        ideaConversion = null
        pendingRecovery = false
        pendingTaskId = null
        page = AgendaPage.TODAY
        projectId = null
        draft = null
        selectedTaskId = null
        if (!store.clear()) error = "No pudimos borrar el acceso guardado. Borra los datos de Agenda en Ajustes de Android."
    }

    fun openIdeas() {
        val session = code ?: return
        if (busy) return
        page = AgendaPage.IDEAS
        ideas = null
        operation { ideas = api.readIdeas(session) }
    }

    fun startIdea() {
        if (busy) return
        ideaDraft = AgendaIdea(UUID.randomUUID().toString(), "")
        ideaSubmitted = false
        error = null
    }

    fun setIdeaContent(content: String) {
        val current = ideaDraft ?: return
        if (!busy && !ideaSubmitted) ideaDraft = current.copy(content = content)
    }

    fun startIdeaConversion(idea: AgendaIdea) {
        if (busy) return
        val firstLine = idea.content.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val end = firstLine.offsetByCodePoints(0, firstLine.codePointCount(0, firstLine.length).coerceAtMost(120))
        ideaConversion = IdeaConversion(idea = idea, title = firstLine.substring(0, end),
            kind = if (data?.projects.orEmpty().isEmpty()) "project" else "task", projectId = data?.projects?.firstOrNull()?.id)
        ideaSubmitted = false
        error = null
    }

    fun editIdeaConversion(update: (IdeaConversion) -> IdeaConversion) {
        val current = ideaConversion ?: return
        if (!busy && !ideaSubmitted) ideaConversion = update(current)
    }

    fun closeIdeaForm() {
        if (busy) return
        ideaDraft = null
        ideaConversion = null
        ideaSubmitted = false
        error = null
        selectPendingTask()
        openPendingRecovery()
    }

    fun saveIdea() {
        val session = code ?: return
        val value = ideaDraft ?: return
        if (busy || value.content.isBlank()) return
        ideaSubmitted = true
        operation {
            try { api.createIdea(session, value) } catch (http: AgendaHttpError) {
                if (http.status == 400) ideaSubmitted = false
                throw http
            }
            ideaDraft = null
            ideaSubmitted = false
            ideas = listOf(value) + ideas.orEmpty().filterNot { it.id == value.id }
        }
    }

    fun convertIdea() {
        val session = code ?: return
        val value = ideaConversion ?: return
        if (busy || value.title.isBlank() || (value.kind == "task" && value.projectId == null)) return
        val submitted = if (value.kind == "project") value.copy(projectId = null) else value
        ideaConversion = submitted
        ideaSubmitted = true
        operation {
            try { api.convertIdea(session, submitted) } catch (http: AgendaHttpError) {
                if (http.status == 400) ideaSubmitted = false
                throw http
            }
            ideas = ideas.orEmpty().filterNot { it.id == submitted.idea.id }
            ideaConversion = null
            ideaSubmitted = false
            if (submitted.kind == "project") {
                page = AgendaPage.PROJECT
                projectId = submitted.resultId
            } else {
                page = AgendaPage.PROJECT
                projectId = submitted.projectId
                selectedTaskId = submitted.resultId
            }
            reloadAfterSave(session)
        }
    }

    fun openWeek() {
        val session = code ?: return
        if (busy) return
        page = AgendaPage.WEEK
        operation { weeklyGoals = api.readGoals(session, weekStart.toString()) }
    }

    fun changeWeek(offset: Long) {
        val session = code ?: return
        if (busy || goalDraft != null || ideaDraft != null || ideaConversion != null) return
        weekStart = weekStart.plusWeeks(offset)
        weeklyGoals = null
        operation { weeklyGoals = api.readGoals(session, weekStart.toString()) }
    }

    fun startGoal(goal: WeeklyGoal? = null) {
        if (busy) return
        goalDraft = goal ?: WeeklyGoal(UUID.randomUUID().toString(), weekStart.toString(), "", 0)
        goalSubmitted = false
        error = null
    }

    fun setGoalTitle(title: String) {
        val current = goalDraft ?: return
        if (!busy && !goalSubmitted) goalDraft = current.copy(title = title)
    }

    fun closeGoal() {
        if (busy) return
        goalDraft = null
        goalSubmitted = false
        error = null
        selectPendingTask()
        openPendingRecovery()
    }

    fun saveGoal() {
        val session = code ?: return
        val goal = goalDraft ?: return
        if (busy || goal.title.isBlank()) return
        goalSubmitted = true
        operation {
            try { api.saveGoal(session, goal) } catch (http: AgendaHttpError) {
                if (http.status == 400) goalSubmitted = false
                throw http
            }
            goalDraft = null
            goalSubmitted = false
            weeklyGoals = weeklyGoals.orEmpty().filterNot { it.id == goal.id } + goal.copy(title = goal.title.trim(), version = goal.version + 1)
            try { weeklyGoals = api.readGoals(session, weekStart.toString()) } catch (http: AgendaHttpError) {
                if (http.status == 401) throw http
                error = "Guardado confirmado. Actualiza para ver los objetivos."
            } catch (_: IOException) { error = "Guardado confirmado. Actualiza para ver los objetivos." }
        }
    }

    fun refresh() {
        val session = code ?: return
        operation { if (page == AgendaPage.AI) loadRecoveries(session) else { load(session); if (page == AgendaPage.WEEK) weeklyGoals = api.readGoals(session, weekStart.toString()); if (page == AgendaPage.IDEAS) ideas = api.readIdeas(session) } }
    }

    fun openRecoveries() {
        val session = code ?: return
        if (busy) return
        page = AgendaPage.AI
        recoveryDraft = null
        operation { loadRecoveries(session) }
    }

    fun openRecoveryNotification() {
        pendingRecovery = true
        openPendingRecovery()
    }

    private fun openPendingRecovery() {
        if (pendingRecovery && signedIn && !busy && draft == null && recoveryDraft == null && priorityDraft == null && goalDraft == null && ideaDraft == null && ideaConversion == null) {
            pendingRecovery = false
            selectedTaskId = null
            openRecoveries()
        }
    }

    private suspend fun loadRecoveries(session: String) {
        val result = api.readRecoveries(session)
        recoveries = result
        reminders.reconcileAi(result)
    }

    fun startRecovery(item: AiRecovery? = null) {
        if (busy) return
        recoveryIsNew = item == null
        recoverySubmitted = false
        recoveryDraft = item ?: AiRecovery(UUID.randomUUID().toString(), "codex", "short",
            RecoveryInstant.format(Instant.now().plusSeconds(3600)), "wait", null, null, false, "", 0)
        error = null
    }

    fun editRecovery(update: (AiRecovery) -> AiRecovery) {
        val current = recoveryDraft ?: return
        if (!busy && !recoverySubmitted) recoveryDraft = update(current)
    }

    fun closeRecovery() {
        if (busy) return
        recoveryDraft = null
        recoverySubmitted = false
        error = null
        selectPendingTask()
        openPendingRecovery()
    }

    fun saveRecovery(cancel: Boolean = false) {
        val session = code ?: return
        val current = recoveryDraft ?: return
        if (busy) return
        val input = if (cancel) current.copy(cancelled = true) else current
        if (!cancel && !recoverySubmitted && !Instant.parse(input.expectedAt).isAfter(Instant.now())) {
            error = "Elige una fecha y hora futuras."
            return
        }
        recoveryDraft = input
        recoverySubmitted = true
        operation {
            try {
                api.saveRecovery(session, input, recoveryIsNew)
            } catch (http: AgendaHttpError) {
                if (http.status == 400) recoverySubmitted = false
                throw http
            }
            recoveryDraft = null
            recoverySubmitted = false
            val confirmed = input.copy(version = if (recoveryIsNew) 1 else input.version + 1,
                createdAt = input.createdAt.ifEmpty { RecoveryInstant.format(Instant.now()) })
            val updated = recoveries.orEmpty().filterNot { it.id == input.id } + confirmed
            recoveries = updated
            reminders.reconcileAi(updated)
            try { loadRecoveries(session) } catch (http: AgendaHttpError) {
                if (http.status == 401) throw http
                error = "Guardado confirmado. Actualiza para ver el estado más reciente."
            } catch (_: IOException) {
                error = "Guardado confirmado. Actualiza para ver el estado más reciente."
            }
        }
    }

    fun openNotificationTask(id: String) {
        pendingTaskId = id
        selectPendingTask()
    }

    private fun selectPendingTask() {
        val id = pendingTaskId ?: return
        if (!signedIn || busy || draft != null || recoveryDraft != null || priorityDraft != null || goalDraft != null || ideaDraft != null || ideaConversion != null) return
        if ((data?.tasks.orEmpty() + data?.futureTasks.orEmpty() + data?.completedTasks.orEmpty()).any { it.id == id }) {
            selectedTaskId = id
            pendingTaskId = null
        }
    }

    private suspend fun load(session: String) {
        val snapshot = api.read(session)
        data = snapshot
        reminders.reconcile(snapshot)
        try { priorities = api.readPriorities(session) } catch (http: AgendaHttpError) {
            if (http.status == 401) throw http
            priorities = null
        } catch (_: IOException) {
            priorities = null
        }
        try { loadRecoveries(session) } catch (http: AgendaHttpError) {
            if (http.status == 401) throw http
            recoveries = null
        } catch (_: IOException) {
            recoveries = null
        }
        selectPendingTask()
    }

    fun navigate(destination: AgendaPage, project: String? = null) {
        if (busy || draft != null) return
        page = destination
        projectId = project
    }

    fun openPriorities() {
        val current = priorities ?: return
        if (busy) return
        val validIds = (data?.tasks.orEmpty() + data?.completedTasks.orEmpty().filter { it.completedToday }).map { it.id }.toSet()
        priorityDraft = current.ids.filter { it in validIds }
        prioritySubmitted = false
        page = AgendaPage.PRIORITIES
        error = null
    }

    fun togglePriority(id: String) {
        val current = priorityDraft ?: return
        if (busy || prioritySubmitted) return
        priorityDraft = if (id in current) current - id else current + id
    }

    fun movePriority(id: String, offset: Int) {
        val current = priorityDraft ?: return
        if (busy || prioritySubmitted) return
        val index = current.indexOf(id)
        val destination = index + offset
        if (index < 0 || destination !in current.indices) return
        priorityDraft = current.toMutableList().apply { add(destination, removeAt(index)) }
    }

    fun closePriorities() {
        if (busy) return
        priorityDraft = null
        prioritySubmitted = false
        page = AgendaPage.TODAY
        error = null
        refresh()
    }

    fun savePriorities() {
        val session = code ?: return
        val base = priorities ?: return
        val ids = priorityDraft ?: return
        if (busy) return
        prioritySubmitted = true
        operation {
            priorities = api.savePriorities(session, base, ids)
            priorityDraft = null
            prioritySubmitted = false
            page = AgendaPage.TODAY
            reloadAfterSave(session)
        }
    }

    fun openForm(project: String? = null) {
        if (busy || draft != null) return
        error = null
        draft = AgendaDraft(project)
    }

    fun editProject() {
        val project = data?.projects?.find { it.id == projectId } ?: return
        if (!busy && draft == null) {
            draft = AgendaDraft(projectId = null, id = project.id, title = project.title,
                level = project.importance, originalProject = project)
            error = null
        }
    }

    fun selectTask(task: AgendaTask?) {
        if (!busy && draft == null) {
            selectedTaskId = task?.id
            error = null
        }
    }

    fun editTask() {
        val task = selectedTask ?: return
        if (!busy) {
            draft = AgendaDraft(projectId = task.projectId, id = task.id, title = task.title, level = task.impact, plannedOn = task.plannedOn, plannedTime = task.plannedTime, original = task)
            error = null
        }
    }

    fun setProject(project: String) = edit { it.copy(projectId = project) }

    fun setCompleted(completed: Boolean) {
        val task = selectedTask ?: return
        val session = code ?: return
        operation {
            api.update(session, task, task.title, task.projectId, task.impact, completed)
            reminders.update(task.copy(completed = completed))
            reloadAfterSave(session)
        }
    }

    fun setBlockedBy(provider: String?) {
        val task = selectedTask ?: return
        val session = code ?: return
        if (provider == task.blockedBy) return
        operation {
            api.update(session, task, task.title, task.projectId, task.impact, task.completed, blockedBy = provider)
            reloadAfterSave(session)
        }
    }

    fun setTitle(title: String) = edit { it.copy(title = title) }

    fun setDate(date: String) = edit { it.copy(plannedOn = date, plannedTime = if (it.original?.carriedOver == true && date != it.original.plannedOn) null else it.plannedTime) }

    fun setTime(time: String?) = edit { it.copy(plannedTime = time) }

    fun setLevel(level: AgendaLevel) = edit { it.copy(level = level) }

    private fun edit(update: (AgendaDraft) -> AgendaDraft) {
        val current = draft ?: return
        if (!busy && !current.submitted) draft = update(current)
    }

    fun closeForm() {
        if (busy || draft == null) return
        draft = null
        error = null
        refresh()
    }

    fun save() {
        val session = code ?: return
        val input = draft ?: return
        if (busy || input.title.isBlank()) return
        if (input.projectId != null && input.plannedTime != null && reminderInstant(input.plannedOn, input.plannedTime) == null) {
            error = "Esta hora no es válida o se repite por el cambio horario en Santiago. Elige otra."
            return
        }
        draft = input.copy(submitted = true)
        operation {
            try {
                val original = input.original
                if (input.originalProject != null) api.updateProject(session, input.originalProject, input.title, input.level)
                else if (original == null) api.create(session, input)
                else api.update(session, original, input.title, requireNotNull(input.projectId), input.level, original.completed, input.plannedOn, input.plannedTime)
            } catch (error: AgendaHttpError) {
                if (error.status == 400) draft = input.copy(submitted = false)
                throw error
            }
            if (input.projectId != null) {
                val task = input.original ?: AgendaTask(input.id, input.title, input.projectId, "", input.level, input.plannedOn, false)
                reminders.update(task.copy(title = input.title.trim(), projectId = input.projectId, impact = input.level, plannedOn = input.plannedOn, plannedTime = input.plannedTime))
            }
            draft = null
            if (input.original != null) {
                selectedTaskId = input.id
            } else if (input.projectId == null) {
                page = AgendaPage.PROJECT
                projectId = input.id
            } else {
                page = AgendaPage.PROJECT
                projectId = input.projectId
            }
            reloadAfterSave(session)
        }
    }

    private suspend fun reloadAfterSave(session: String) {
        try {
            load(session)
        } catch (error: AgendaHttpError) {
            if (error.status == 401) throw error
            this.error = "Guardado confirmado. Actualiza para ver los cambios."
        } catch (_: IOException) {
            error = "Guardado confirmado. Actualiza para ver los cambios."
        } catch (_: JSONException) {
            error = "Guardado confirmado. Actualiza para ver los cambios."
        }
    }

    private fun operation(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                block()
            } catch (http: AgendaHttpError) {
                error = when (http.status) {
                    401 -> {
                        reminders.clear()
                        code = null
                        data = null
                        recoveries = null
                        recoveryDraft = null
                        priorities = null
                        priorityDraft = null
                        weeklyGoals = null
                        goalDraft = null
                        ideas = null
                        ideaDraft = null
                        ideaConversion = null
                        pendingRecovery = false
                        pendingTaskId = null
                        store.clear()
                        "Introduce tu código de acceso vigente."
                    }
                    400 -> "Revisa los datos antes de guardar."
                    409 -> "No se confirmó el guardado. Vuelve y actualiza la agenda antes de probar otra vez."
                    else -> "La agenda no está disponible. Vuelve a intentarlo."
                }
            } catch (_: IOException) {
                error = "No hubo respuesta de la agenda. Vuelve a intentarlo."
            } catch (_: JSONException) {
                error = "La respuesta de la agenda no se pudo leer. Vuelve a intentarlo."
            } finally {
                busy = false
                selectPendingTask()
                openPendingRecovery()
            }
        }
    }
}
