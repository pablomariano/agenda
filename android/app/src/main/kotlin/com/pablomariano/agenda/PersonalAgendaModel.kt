package com.pablomariano.agenda

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch
import org.json.JSONException

internal data class AgendaDraft(
    val projectId: String?,
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val level: AgendaLevel = AgendaLevel.MEDIUM,
    val submitted: Boolean = false,
    val original: AgendaTask? = null,
)

internal enum class AgendaPage { TODAY, PROJECTS, PROJECT }

internal class PersonalAgendaModel(application: Application) : AndroidViewModel(application) {
    private val api = AgendaApi()
    private val store = AccessCodeStore(application)

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
    val selectedTask: AgendaTask? get() = (data?.tasks.orEmpty() + data?.completedTasks.orEmpty()).find { it.id == selectedTaskId }
    var busy by mutableStateOf(false)
        private set
    var error: String? by mutableStateOf(null)
        private set

    init {
        operation {
            store.read()?.let {
                code = it
                data = api.read(it)
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
            data = api.read(entered)
            code = entered
            if (!store.write(entered)) {
                error = "Acceso confirmado. El dispositivo no pudo guardar el código para la próxima vez."
            }
        }
    }

    fun signOut() = operation {
        code = null
        data = null
        page = AgendaPage.TODAY
        projectId = null
        draft = null
        selectedTaskId = null
        if (!store.clear()) error = "No pudimos borrar el acceso guardado. Borra los datos de Agenda en Ajustes de Android."
    }

    fun refresh() {
        val session = code ?: return
        operation { data = api.read(session) }
    }

    fun navigate(destination: AgendaPage, project: String? = null) {
        if (busy || draft != null) return
        page = destination
        projectId = project
    }

    fun openForm(project: String? = null) {
        if (busy || draft != null) return
        error = null
        draft = AgendaDraft(project)
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
            draft = AgendaDraft(projectId = task.projectId, id = task.id, title = task.title, level = task.impact, original = task)
            error = null
        }
    }

    fun setProject(project: String) = edit { it.copy(projectId = project) }

    fun setCompleted(completed: Boolean) {
        val task = selectedTask ?: return
        val session = code ?: return
        operation {
            api.update(session, task, task.title, task.projectId, task.impact, completed)
            reloadAfterSave(session)
        }
    }

    fun setTitle(title: String) = edit { it.copy(title = title) }

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
        draft = input.copy(submitted = true)
        operation {
            try {
                val original = input.original
                if (original == null) api.create(session, input)
                else api.update(session, original, input.title, requireNotNull(input.projectId), input.level, original.completed)
            } catch (error: AgendaHttpError) {
                if (error.status == 400) draft = input.copy(submitted = false)
                throw error
            }
            draft = null
            if (input.original != null) {
                selectedTaskId = input.id
            } else if (input.projectId == null) {
                page = AgendaPage.PROJECT
                projectId = input.id
            } else {
                page = AgendaPage.TODAY
                projectId = null
            }
            reloadAfterSave(session)
        }
    }

    private suspend fun reloadAfterSave(session: String) {
        try {
            data = api.read(session)
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
                        code = null
                        data = null
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
            }
        }
    }
}
