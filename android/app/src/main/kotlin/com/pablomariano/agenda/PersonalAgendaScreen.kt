package com.pablomariano.agenda

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val AgendaTimeZone = ZoneId.of("America/Santiago")
private val Chile = Locale.forLanguageTag("es-CL")
private val DateLabel = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Chile)

@Composable
internal fun PersonalAgendaScreen(model: PersonalAgendaModel) {
    val draft = model.draft
    val projectPage = model.page == AgendaPage.PROJECT
    val selectedTask = model.selectedTask
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) now = System.currentTimeMillis() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(model.recoveries) {
        now = System.currentTimeMillis()
        while (true) {
            val next = model.recoveries.orEmpty().filterNot { it.cancelled }
                .map { Instant.parse(it.expectedAt).toEpochMilli() }.filter { it > now }.minOrNull() ?: break
            delay((next - now).coerceAtLeast(1L).coerceAtMost(3_600_000L))
            now = System.currentTimeMillis()
        }
    }

    BackHandler(enabled = model.signedIn && (draft != null || selectedTask != null || projectPage || model.page == AgendaPage.AI || model.page == AgendaPage.PRIORITIES || model.page == AgendaPage.WEEK || model.page == AgendaPage.IDEAS)) {
        if (draft != null) model.closeForm()
        else if (selectedTask != null) model.selectTask(null)
        else if (model.page == AgendaPage.IDEAS && (model.ideaDraft != null || model.ideaConversion != null)) model.closeIdeaForm()
        else if (model.page == AgendaPage.IDEAS) model.navigate(AgendaPage.TODAY)
        else if (model.page == AgendaPage.WEEK && model.goalDraft != null) model.closeGoal()
        else if (model.page == AgendaPage.WEEK) model.navigate(AgendaPage.TODAY)
        else if (model.page == AgendaPage.PRIORITIES) model.closePriorities()
        else if (model.page == AgendaPage.AI && model.recoveryDraft != null) model.closeRecovery()
        else if (model.page == AgendaPage.AI) model.navigate(AgendaPage.TODAY)
        else model.navigate(AgendaPage.PROJECTS)
    }
    Surface(Modifier.fillMaxSize(), color = AgendaColors.Canvas) {
        Column(
            Modifier.safeDrawingPadding().imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            key(model.signedIn, model.page, draft?.id, model.recoveryDraft?.id, model.goalDraft?.id, model.ideaDraft?.id, model.ideaConversion?.idea?.id) {
                Column(
                    Modifier.weight(1f).widthIn(max = 520.dp).fillMaxWidth()
                        .verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (model.busy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    model.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    when {
                        !model.signedIn -> AccessScreen(model)
                        draft != null -> AgendaForm(model, draft)
                        selectedTask != null -> TaskDetail(model, selectedTask)
                        model.page == AgendaPage.IDEAS -> IdeasScreen(model)
                        model.page == AgendaPage.WEEK -> WeekScreen(model)
                        model.page == AgendaPage.PRIORITIES -> PriorityScreen(model)
                        model.page == AgendaPage.AI -> RecoveryScreen(model, now)
                        model.page == AgendaPage.TODAY -> PersonalToday(model, model::selectTask, now)
                        model.page == AgendaPage.PROJECTS -> ProjectList(model)
                        else -> ProjectDetail(model, model::selectTask)
                    }
                }
            }
            if (model.signedIn && draft == null && selectedTask == null && !projectPage && model.page != AgendaPage.AI && model.page != AgendaPage.PRIORITIES && model.page != AgendaPage.WEEK && model.page != AgendaPage.IDEAS) {
                TodayNavigation(
                    modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                    activeTab = if (model.page == AgendaPage.TODAY) 0 else 2,
                    projectsEnabled = true,
                    weekEnabled = true,
                    ideasEnabled = true,
                    onNavigate = { tab ->
                        when (tab) { 0 -> model.navigate(AgendaPage.TODAY); 1 -> model.openWeek(); 2 -> model.navigate(AgendaPage.PROJECTS); 3 -> model.openIdeas() }
                    },
                )
            }
        }
    }


}

@Composable
private fun AccessScreen(model: PersonalAgendaModel) {
    var code by remember { mutableStateOf("") }
    PageHeading("Agenda", "Introduce tu código privado para abrir la agenda.")
    OutlinedTextField(
        value = code,
        onValueChange = { code = it },
        label = { Text("Código de acceso") },
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        enabled = !model.busy,
    )
    AgendaAction("Entrar", enabled = !model.busy && code.isNotBlank()) { model.signIn(code) }
}

@Composable
private fun ReminderPermissions(reminders: TaskReminders) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var version by remember { mutableStateOf(0) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { version++ }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) version++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    key(version) {
        if (!reminders.notificationsAllowed()) {
            Text("Las notificaciones están desactivadas; no recibirás recordatorios.", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    request.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text("Activar notificaciones") }
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Abrir ajustes de notificaciones") }
        }
        if (!reminders.exactAllowed() && Build.VERSION.SDK_INT >= 31) {
            Text("Android puede retrasar los recordatorios si no permites alarmas exactas.", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }) { Text("Permitir alarmas exactas") }
        }
    }
}

@Composable
private fun PersonalToday(model: PersonalAgendaModel, openTask: (AgendaTask) -> Unit, now: Long) {
    val today = LocalDate.now(AgendaTimeZone).format(DateLabel).replaceFirstChar { it.titlecase(Chile) }
    PageHeading("Hoy", today)
    val tasks = model.data?.tasks
    val completed = model.data?.completedTasks.orEmpty().filter { it.completedToday }
    if (tasks != null) {
        val candidates = tasks + completed
        val selected = model.priorities?.ids.orEmpty().mapNotNull { id -> candidates.find { it.id == id } }
        TodaySummary(
            completed = selected.count { it.completed },
            total = selected.size,
            actionLabel = "Elegir prioridades",
            completedLabel = "prioridades completadas",
            emptyTitle = if (model.priorities == null) "Prioridades no disponibles" else "Sin prioridades elegidas",
            emptyMessage = if (model.priorities == null) "Actualiza para cargar tus prioridades." else "Elige las tareas en las que quieres enfocarte hoy.",
            actionEnabled = !model.busy && model.priorities != null,
        ) { model.openPriorities() }
        if (selected.isNotEmpty()) {
            Text("Prioridades", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            TaskRows(selected, openTask)
        }
        val selectedIds = selected.map { it.id }.toSet()
        val suggestions = tasks.filterNot { it.id in selectedIds }
        if (suggestions.isNotEmpty()) {
            Text("Otras tareas de hoy", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            TaskRows(suggestions, openTask)
        }
        Text("Recuperación de IA", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        val nextRecovery = model.recoveries.orEmpty().filterNot { it.cancelled }
            .filter { Instant.parse(it.expectedAt).toEpochMilli() > now }.minByOrNull { Instant.parse(it.expectedAt) }
        if (nextRecovery != null) {
            val label = if (nextRecovery.provider == "codex") "Codex" else "Gemini"
            val whenText = Instant.parse(nextRecovery.expectedAt).atZone(AgendaTimeZone)
                .format(DateTimeFormatter.ofPattern("d MMM · HH:mm", Chile))
            SummaryCard(indicator = label.take(1), title = label, subtitle = whenText,
                caption = "Hora prevista · Registro manual", indicatorBackground = AgendaColors.Sky,
                accent = AgendaColors.Blue, onClick = model::openRecoveries)
        } else if (model.recoveries != null) {
            Text("Sin horarios futuros registrados; disponibilidad desconocida.", color = AgendaColors.Muted, style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = model::openRecoveries, enabled = !model.busy) { Text("Ver recuperaciones de IA") }
    }
    TextButton(onClick = model::openWeek, enabled = !model.busy) { Text("Planificar semana") }
    ReminderPermissions(model.reminders)
    RefreshAction(model)
    TextButton(onClick = model::signOut, enabled = !model.busy) { Text("Cerrar sesión") }
}

@Composable
private fun IdeasScreen(model: PersonalAgendaModel) {
    val idea = model.ideaDraft
    val conversion = model.ideaConversion
    BackButton(!model.busy) { if (idea != null || conversion != null) model.closeIdeaForm() else model.navigate(AgendaPage.TODAY) }
    if (idea != null) {
        PageHeading("Nueva idea", "Guárdala ahora y organízala cuando quieras.")
        OutlinedTextField(value = idea.content,
            onValueChange = { if (it.codePointCount(0, it.length) <= 4000) model.setIdeaContent(it) },
            label = { Text("Tu idea") }, modifier = Modifier.fillMaxWidth(), minLines = 4,
            enabled = !model.busy && !model.ideaSubmitted)
        if (model.ideaSubmitted && !model.busy) Text("Reintenta con el mismo envío para evitar duplicados.")
        AgendaAction(if (model.ideaSubmitted) "Reintentar guardado" else "Guardar idea", !model.busy && idea.content.isNotBlank(), model::saveIdea)
        return
    }
    if (conversion != null) {
        val editable = !model.busy && !model.ideaSubmitted
        PageHeading("Organizar idea", "El texto original se conservará en el resultado.")
        Text("Idea original", style = MaterialTheme.typography.titleMedium)
        Text(conversion.idea.content, style = MaterialTheme.typography.bodyMedium)
        Text("Convertir en", style = MaterialTheme.typography.titleMedium)
        RecoveryChoices(listOf("task" to "Tarea", "project" to "Proyecto"), conversion.kind, editable) { kind ->
            model.editIdeaConversion { it.copy(kind = kind, projectId = if (kind == "task") model.data?.projects?.firstOrNull()?.id else null) }
        }
        OutlinedTextField(value = conversion.title,
            onValueChange = { text -> if (text.codePointCount(0, text.length) <= 120) model.editIdeaConversion { it.copy(title = text) } },
            label = { Text("Título") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = editable)
        val isTask = conversion.kind == "task"
        if (isTask) {
            Text("Proyecto", style = MaterialTheme.typography.titleMedium)
            val projects = model.data?.projects.orEmpty()
            if (projects.isEmpty()) Text("Crea un proyecto primero o convierte la idea en proyecto.")
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                projects.forEach { project ->
                    Surface(modifier = Modifier.fillMaxWidth().selectable(project.id == conversion.projectId,
                        enabled = editable, role = Role.RadioButton) { model.editIdeaConversion { it.copy(projectId = project.id) } },
                        shape = RoundedCornerShape(16.dp), color = if (project.id == conversion.projectId) AgendaColors.Mint else AgendaColors.Surface) {
                        Text(project.title, Modifier.heightIn(min = 48.dp).padding(16.dp))
                    }
                }
            }
        }
        Text(if (isTask) "Impacto" else "Importancia", style = MaterialTheme.typography.titleMedium)
        RecoveryChoices(AgendaLevel.entries.map { it.value to if (isTask) it.impact else it.importance }, conversion.level.value, editable) { level ->
            model.editIdeaConversion { it.copy(level = AgendaLevel.read(level)) }
        }
        if (model.ideaSubmitted && !model.busy) Text("Reintenta con los mismos datos para evitar duplicados.")
        AgendaAction(if (model.ideaSubmitted) "Reintentar conversión" else if (isTask) "Crear tarea" else "Crear proyecto",
            !model.busy && conversion.title.isNotBlank() && (!isTask || conversion.projectId != null), model::convertIdea)
        return
    }
    PageHeading("Ideas", "Guarda tus ideas y conviértelas en trabajo cuando estés listo.")
    if (model.ideas == null) Text("No se pudieron cargar las ideas. Actualiza para intentarlo de nuevo.")
    else {
        AgendaAction("Guardar nueva idea", !model.busy, model::startIdea)
        if (model.ideas!!.isEmpty()) Text("Aún no tienes ideas pendientes de organizar.")
        model.ideas!!.forEach { item ->
            SummaryCard(indicator = "✦", title = item.content.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty(),
                subtitle = "Idea guardada", caption = "Toca para organizar", indicatorBackground = AgendaColors.Mint,
                accent = AgendaColors.Action, onClick = { model.startIdeaConversion(item) })
        }
    }
    RefreshAction(model)
}

@Composable
private fun WeekScreen(model: PersonalAgendaModel) {
    val draft = model.goalDraft
    BackButton(!model.busy) { if (draft != null) model.closeGoal() else model.navigate(AgendaPage.TODAY) }
    if (draft != null) {
        PageHeading(if (draft.version == 0) "Nuevo objetivo" else "Editar objetivo", "Define lo que quieres lograr esta semana.")
        OutlinedTextField(value = draft.title, onValueChange = { if (it.codePointCount(0, it.length) <= 120) model.setGoalTitle(it) },
            label = { Text("Objetivo semanal") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            enabled = !model.busy && !model.goalSubmitted)
        if (model.goalSubmitted && !model.busy) Text("Reintenta con el mismo envío para evitar duplicados.")
        AgendaAction(if (model.goalSubmitted) "Reintentar guardado" else "Guardar objetivo", !model.busy && draft.title.isNotBlank(), model::saveGoal)
        return
    }
    val start = model.weekStart
    val end = start.plusDays(6)
    val format = DateTimeFormatter.ofPattern("d MMM yyyy", Chile)
    PageHeading("Semana", "${start.format(format)} – ${end.format(format)}")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { model.changeWeek(-1) }, enabled = !model.busy) { Text("← Anterior") }
        TextButton(onClick = { model.changeWeek(1) }, enabled = !model.busy) { Text("Siguiente →") }
    }
    Text("Objetivos", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
    if (model.weeklyGoals == null) Text("No se pudieron cargar los objetivos. Actualiza para intentarlo de nuevo.")
    else {
        if (model.weeklyGoals!!.isEmpty()) Text("Aún no hay objetivos para esta semana.")
        model.weeklyGoals!!.forEach { goal ->
            SummaryCard(indicator = "◎", title = goal.title, subtitle = "Objetivo semanal", caption = "Toca para editar",
                indicatorBackground = AgendaColors.Mint, accent = AgendaColors.Action, onClick = { model.startGoal(goal) })
        }
        AgendaAction("Agregar objetivo", !model.busy) { model.startGoal() }
    }
    val snapshot = model.data
    val today = LocalDate.now(AgendaTimeZone)
    val all = snapshot?.let { it.tasks + it.futureTasks + it.completedTasks }.orEmpty()
    for (offset in 0L..6L) {
        val date = start.plusDays(offset)
        Text(date.format(DateLabel).replaceFirstChar { it.titlecase(Chile) }, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        val tasks = all.filter { task ->
            val planned = LocalDate.parse(task.plannedOn)
            val effective = if (!task.completed && planned.isBefore(today)) today else planned
            effective == date
        }
        if (tasks.isEmpty()) Text("Sin tareas", color = AgendaColors.Muted, style = MaterialTheme.typography.bodyMedium)
        else TaskRows(tasks, model::selectTask)
    }
    RefreshAction(model)
}

@Composable
private fun PriorityScreen(model: PersonalAgendaModel) {
    val ids = model.priorityDraft ?: return
    val snapshot = model.data ?: return
    val all = snapshot.tasks + snapshot.completedTasks.filter { it.completedToday }
    val selected = ids.mapNotNull { id -> all.find { it.id == id } }
    BackButton(!model.busy, model::closePriorities)
    PageHeading("Prioridades de hoy", "Elige y ordena tus tareas; las sugerencias usan importancia e impacto.")
    if (selected.isEmpty()) Text("Todavía no elegiste prioridades.", style = MaterialTheme.typography.bodyMedium)
    selected.forEachIndexed { index, task ->
        val importance = snapshot.projects.find { it.id == task.projectId }?.importance?.importance?.lowercase() ?: "desconocida"
        AgendaPanel {
            Text("${index + 1}. ${task.title}", style = MaterialTheme.typography.titleMedium)
            Text("${task.projectTitle} · Importancia $importance · Impacto ${task.impact.impact.lowercase()}${if (task.completed) " · Completada" else ""}", style = MaterialTheme.typography.bodyMedium)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { model.movePriority(task.id, -1) }, modifier = Modifier.semantics { contentDescription = "Subir ${task.title}" }, enabled = !model.busy && !model.prioritySubmitted && index > 0) { Text("Subir") }
                TextButton(onClick = { model.movePriority(task.id, 1) }, modifier = Modifier.semantics { contentDescription = "Bajar ${task.title}" }, enabled = !model.busy && !model.prioritySubmitted && index < selected.lastIndex) { Text("Bajar") }
                TextButton(onClick = { model.togglePriority(task.id) }, modifier = Modifier.semantics { contentDescription = "Quitar ${task.title} de prioridades" }, enabled = !model.busy && !model.prioritySubmitted) { Text("Quitar") }
            }
        }
    }
    val suggestions = snapshot.tasks.filterNot { it.id in ids }
    if (suggestions.isNotEmpty()) {
        Text("Sugerencias", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        suggestions.forEach { task ->
            val importance = snapshot.projects.find { it.id == task.projectId }?.importance?.importance?.lowercase() ?: "desconocida"
            AgendaPanel {
                Text(task.title, style = MaterialTheme.typography.titleMedium)
                Text("${task.projectTitle} · Importancia $importance · Impacto ${task.impact.impact.lowercase()}", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { model.togglePriority(task.id) }, modifier = Modifier.semantics { contentDescription = "Añadir ${task.title} a prioridades" }, enabled = !model.busy && !model.prioritySubmitted) { Text("Añadir a prioridades") }
            }
        }
    }
    if (model.prioritySubmitted && !model.busy) Text("Reintenta el mismo envío o vuelve para actualizar.")
    AgendaAction(if (model.prioritySubmitted) "Reintentar guardado" else "Guardar prioridades", !model.busy, model::savePriorities)
}

@Composable
private fun ProjectList(model: PersonalAgendaModel) {
    val snapshot = model.data
    val projects = snapshot?.projects.orEmpty().sortedBy { it.importance.ordinal }
    PageHeading("Proyectos", if (projects.size == 1) "1 proyecto activo" else "${projects.size} proyectos activos")
    val first = projects.firstOrNull()
    if (first != null && snapshot != null) {
        val firstCount = snapshot.tasks.count { it.projectId == first.id }
        AgendaPanel {
            Text("EN FOCO", style = MaterialTheme.typography.labelMedium, color = AgendaColors.Action)
            Text(first.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                "Importancia ${first.importance.importance.lowercase()} · $firstCount ${if (firstCount == 1) "pendiente" else "pendientes"} para hoy",
                style = MaterialTheme.typography.labelLarge,
                color = AgendaColors.Action,
            )
            AgendaAction("Abrir proyecto", !model.busy) { model.navigate(AgendaPage.PROJECT, first.id) }
        }
        if (projects.size > 1) Text("Otros proyectos", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        projects.drop(1).forEachIndexed { index, project ->
            val count = snapshot.tasks.count { it.projectId == project.id }
            SummaryCard(
                indicator = project.title.take(2).uppercase(),
                title = project.title,
                subtitle = if (count == 1) "1 pendiente para hoy" else "$count pendientes para hoy",
                caption = "Importancia ${project.importance.importance.lowercase()}",
                indicatorBackground = if (index % 2 == 0) AgendaColors.Sky else AgendaColors.Canvas,
                accent = if (index % 2 == 0) AgendaColors.Blue else AgendaColors.Muted,
                onClick = { model.navigate(AgendaPage.PROJECT, project.id) },
            )
        }
    } else if (snapshot != null) {
        Text("Crea tu primer proyecto para empezar a organizar tareas.", style = MaterialTheme.typography.bodyMedium)
    }
    if (snapshot != null) AgendaAction("Nuevo proyecto", !model.busy) { model.openForm() }
    RefreshAction(model)
}

@Composable
private fun ProjectDetail(model: PersonalAgendaModel, openTask: (AgendaTask) -> Unit) {
    val project = model.data?.projects?.find { it.id == model.projectId }
    BackButton(!model.busy) { model.navigate(AgendaPage.PROJECTS) }
    PageHeading(project?.title ?: "Proyecto", "Tareas pendientes para hoy")
    if (project != null) {
        if (project.description.isNotEmpty()) {
            Text("Idea original", style = MaterialTheme.typography.titleMedium)
            Text(project.description, style = MaterialTheme.typography.bodyMedium)
        }
        val tasks = model.data?.tasks.orEmpty().filter { it.projectId == project.id }
        AgendaPanel {
            Text("Importancia ${project.importance.importance.lowercase()}", color = AgendaColors.Action)
            Text(if (tasks.size == 1) "1 tarea pendiente" else "${tasks.size} tareas pendientes", style = MaterialTheme.typography.titleMedium)
            AgendaAction("Nueva tarea", !model.busy) { model.openForm(project.id) }
            AgendaAction("Editar proyecto", !model.busy, model::editProject)
        }
        if (tasks.isNotEmpty()) TaskRows(tasks, openTask)
        val future = model.data?.futureTasks.orEmpty().filter { it.projectId == project.id }
        if (future.isNotEmpty()) {
            Text("Próximas", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            TaskRows(future, openTask)
        }
        val completed = model.data?.completedTasks.orEmpty().filter { it.projectId == project.id }
        if (completed.isNotEmpty()) {
            Text("Completadas", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            TaskRows(completed, openTask)
        }
    }
    RefreshAction(model)
}

@Composable
private fun TaskDetail(model: PersonalAgendaModel, task: AgendaTask) {
    BackButton(!model.busy) { model.selectTask(null) }
    PageHeading(task.title, task.projectTitle)
    AgendaPanel {
        Text(if (task.completed) "Completada" else "Pendiente", color = AgendaColors.Action)
        Text(if (task.completedToday) "Completada hoy" else if (task.completed) "Tarea completada" else if (task.carriedOver) "Pendiente desde ${task.plannedOn}" else "Planificada para ${task.plannedOn}", style = MaterialTheme.typography.headlineSmall)
        if (task.plannedTime != null && !task.carriedOver) Text("Hora: ${task.plannedTime}", style = MaterialTheme.typography.bodyMedium)
        Text("Impacto ${task.impact.impact.lowercase()}", style = MaterialTheme.typography.bodyMedium)
        if (task.blockedBy != null) Text("Bloqueada por ${if (task.blockedBy == "codex") "Codex" else "Gemini"}", style = MaterialTheme.typography.bodyMedium)
    }
    if (task.description.isNotEmpty()) {
        Text("Idea original", style = MaterialTheme.typography.titleMedium)
        Text(task.description, style = MaterialTheme.typography.bodyMedium)
    }
    Text("Bloqueo por IA", style = MaterialTheme.typography.titleMedium)
    RecoveryChoices(listOf("none" to "Sin bloqueo", "codex" to "Codex", "gemini" to "Gemini"), task.blockedBy ?: "none", !model.busy) {
        model.setBlockedBy(if (it == "none") null else it)
    }
    if (!task.completed && task.plannedTime != null && !task.carriedOver) {
        var enabled by remember(task.id) { mutableStateOf(model.reminders.enabled(task.id)) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Recordatorio", modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { enabled = it; model.reminders.setEnabled(task.id, it) }, enabled = !model.busy)
        }
        Text("Zona horaria: America/Santiago", style = MaterialTheme.typography.bodyMedium)
    }
    AgendaAction(if (task.completed) "Volver a pendiente" else "Marcar como completada", !model.busy) {
        model.setCompleted(!task.completed)
    }
    AgendaAction("Editar tarea", !model.busy, model::editTask)
    RefreshAction(model)
}

@Composable
private fun TaskRows(tasks: List<AgendaTask>, open: (AgendaTask) -> Unit) {
    tasks.forEachIndexed { index, task ->
        SummaryCard(
            indicator = if (task.completed) "✓" else (index + 1).toString().padStart(2, '0'),
            title = task.title,
            subtitle = if (task.carriedOver) "${task.projectTitle} · Pendiente desde ${task.plannedOn}" else "${task.projectTitle} · ${task.plannedOn}${task.plannedTime?.let { " · $it" } ?: ""}",
            caption = if (task.completed) "Completada" else "Impacto ${task.impact.impact.lowercase()}${task.blockedBy?.let { " · Bloqueada por ${if (it == "codex") "Codex" else "Gemini"}" } ?: ""}",
            indicatorBackground = AgendaColors.Mint,
            accent = AgendaColors.Action,
            onClick = { open(task) },
        )
    }
}

@Composable
private fun AgendaForm(model: PersonalAgendaModel, draft: AgendaDraft) {
    val isProject = draft.projectId == null
    val project = model.data?.projects?.find { it.id == draft.projectId }
    val canEdit = !model.busy && !draft.submitted
    BackButton(!model.busy, model::closeForm)
    PageHeading(
        if (draft.originalProject != null) "Editar proyecto" else if (isProject) "Nuevo proyecto" else if (draft.original != null) "Editar tarea" else "Crear tarea",
        if (isProject) "Dale un nombre y elige su importancia." else if (draft.original != null) "Actualiza los datos de tu tarea." else "Elige el día y, si quieres, una hora.",
    )
    if (!isProject && draft.original != null) {
        Text("Proyecto", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            model.data?.projects.orEmpty().forEach { option ->
                Surface(
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = option.id == draft.projectId, enabled = canEdit, role = Role.RadioButton,
                        onClick = { model.setProject(option.id) },
                    ),
                    shape = RoundedCornerShape(16.dp),
                    color = if (option.id == draft.projectId) AgendaColors.Mint else AgendaColors.Surface,
                ) {
                    Text(option.title, Modifier.heightIn(min = 48.dp).padding(16.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    } else if (!isProject) {
        Surface(shape = RoundedCornerShape(16.dp), color = AgendaColors.Surface) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Proyecto", style = MaterialTheme.typography.labelMedium, color = AgendaColors.Muted)
                Text(project?.title ?: "Proyecto", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    OutlinedTextField(
        value = draft.title,
        onValueChange = { if (it.codePointCount(0, it.length) <= 120) model.setTitle(it) },
        label = { Text(if (isProject) "Nombre" else "Título") },
        modifier = Modifier.fillMaxWidth(),
        enabled = canEdit,
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = AgendaColors.Surface,
            unfocusedContainerColor = AgendaColors.Surface,
            disabledContainerColor = AgendaColors.Surface,
        ),
    )
    Text(if (isProject) "Importancia" else "Impacto", style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AgendaLevel.entries.forEach { level ->
            val selected = draft.level == level
            Surface(
                modifier = Modifier.weight(1f)
                    .selectable(selected, enabled = canEdit, role = Role.RadioButton) { model.setLevel(level) },
                shape = RoundedCornerShape(16.dp),
                color = if (selected) AgendaColors.Action else AgendaColors.Mint,
            ) {
                Column(
                    Modifier.heightIn(min = 48.dp).padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (isProject) level.importance else level.impact,
                        color = if (selected) AgendaColors.Surface else AgendaColors.Action,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
    if (!isProject) {
        val context = LocalContext.current
        val date = LocalDate.parse(draft.plannedOn)
        Text("Planificación", style = MaterialTheme.typography.titleMedium)
        TextButton(enabled = canEdit, onClick = {
            DatePickerDialog(context, { _, year, month, day -> model.setDate(LocalDate.of(year, month + 1, day).toString()) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
        }) { Text("Día: ${date.format(DateLabel)}") }
        if (draft.original?.carriedOver == true && draft.plannedOn == draft.original.plannedOn) {
            Text("La hora anterior ya venció. Cambia el día para asignar una nueva.", style = MaterialTheme.typography.bodyMedium)
        } else {
            TextButton(enabled = canEdit, onClick = {
                val time = draft.plannedTime?.let { LocalTime.parse(it) } ?: LocalTime.of(9, 0)
                TimePickerDialog(context, { _, hour, minute -> model.setTime("%02d:%02d".format(Locale.ROOT, hour, minute)) }, time.hour, time.minute, true).show()
            }) { Text("Hora: ${draft.plannedTime ?: "sin hora"}") }
            if (draft.plannedTime != null) TextButton(enabled = canEdit, onClick = { model.setTime(null) }) { Text("Quitar hora") }
        }
    }
    if (draft.submitted && !model.busy) {
        Text("Reintenta con el mismo envío para evitar duplicados.", style = MaterialTheme.typography.bodyMedium)
    }
    AgendaAction(
        if (draft.submitted) "Reintentar guardado" else if (draft.originalProject != null) "Guardar cambios" else if (isProject) "Crear proyecto" else if (draft.original != null) "Guardar cambios" else "Crear tarea",
        enabled = !model.busy && draft.title.isNotBlank(),
        onClick = model::save,
    )
}

@Composable
private fun PageHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.displaySmall)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AgendaColors.Muted)
    }
}

@Composable
private fun BackButton(enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Image(painterResource(R.drawable.agenda_volver), "Volver")
    }
}

@Composable
private fun AgendaPanel(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(AgendaColors.Mint, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun AgendaAction(title: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun RefreshAction(model: PersonalAgendaModel) {
    HorizontalDivider(color = AgendaColors.Mint)
    TextButton(onClick = model::refresh, enabled = !model.busy) { Text("Actualizar") }
}

@Composable
private fun RecoveryScreen(model: PersonalAgendaModel, now: Long) {
    val draft = model.recoveryDraft
    BackButton(!model.busy) { if (draft != null) model.closeRecovery() else model.navigate(AgendaPage.TODAY) }
    if (draft == null) {
        PageHeading("Recuperación de IA", "Horas previstas registradas manualmente. La detección automática no está disponible.")
        val active = model.recoveries.orEmpty().filterNot { it.cancelled }
        if (model.recoveries != null && active.isEmpty()) Text("Aún no hay recuperaciones registradas.")
        active.forEach { item ->
            val date = Instant.parse(item.expectedAt).atZone(AgendaTimeZone)
            val dateText = date.format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Chile))
            SummaryCard(indicator = if (item.provider == "codex") "C" else "G",
                title = if (item.provider == "codex") "Codex" else "Gemini",
                subtitle = "$dateText · ${if (item.window == "short") "Ventana corta" else "Ventana semanal"}",
                caption = if (Instant.parse(item.expectedAt).toEpochMilli() > now) "Hora prevista · Registro manual" else "Hora prevista alcanzada · Sin verificar",
                indicatorBackground = AgendaColors.Sky, accent = AgendaColors.Blue, onClick = { model.startRecovery(item) })
        }
        if (model.recoveries != null) AgendaAction("Registrar recuperación", !model.busy) { model.startRecovery() }
        RefreshAction(model)
        return
    }
    val context = LocalContext.current
    val canEdit = !model.busy && !model.recoverySubmitted
    val chosen = Instant.parse(draft.expectedAt).atZone(AgendaTimeZone)
    val instantFormat = remember { DateTimeFormatterBuilder().appendInstant(3).toFormatter() }
    var duration by remember(draft.id) { mutableStateOf("") }
    PageHeading(if (model.recoveryIsNew) "Registrar recuperación" else "Editar recuperación", "Anota la hora que te indicó la herramienta.")
    if (draft.createdAt.isNotEmpty()) {
        val registered = Instant.parse(draft.createdAt).atZone(AgendaTimeZone)
            .format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Chile))
        Text("Registro manual: $registered · America/Santiago", style = MaterialTheme.typography.bodyMedium)
    }
    Text("Herramienta", style = MaterialTheme.typography.titleMedium)
    RecoveryChoices(listOf("codex" to "Codex", "gemini" to "Gemini"), draft.provider, canEdit) { provider ->
        model.editRecovery { it.copy(provider = provider, alternative = if (it.decision == "continue") if (provider == "codex") "gemini" else "codex" else null) }
    }
    Text("Ventana", style = MaterialTheme.typography.titleMedium)
    RecoveryChoices(listOf("short" to "Corta", "weekly" to "Semanal"), draft.window, canEdit) { value -> model.editRecovery { it.copy(window = value) } }
    Text("Recuperación prevista · America/Santiago", style = MaterialTheme.typography.titleMedium)
    TextButton(enabled = canEdit, onClick = {
        DatePickerDialog(context, { _, year, month, day ->
            val date = LocalDate.of(year, month + 1, day)
            TimePickerDialog(context, { _, hour, minute ->
                val time = "%02d:%02d".format(Locale.ROOT, hour, minute)
                val millis = reminderInstant(date.toString(), time)
                if (millis == null) android.widget.Toast.makeText(context, "Hora inexistente o ambigua; elige otra.", android.widget.Toast.LENGTH_LONG).show()
                else model.editRecovery { it.copy(expectedAt = instantFormat.format(Instant.ofEpochMilli(millis))) }
            }, chosen.hour, chosen.minute, true).show()
        }, chosen.year, chosen.monthValue - 1, chosen.dayOfMonth).show()
    }) { Text(chosen.format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Chile))) }
    OutlinedTextField(value = duration, onValueChange = { if (it.length <= 5 && it.all(Char::isDigit)) duration = it },
        modifier = Modifier.fillMaxWidth(), label = { Text("O ingresar espera en minutos") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = canEdit, singleLine = true)
    TextButton(enabled = canEdit && (duration.toIntOrNull() ?: 0) > 0, onClick = {
        val minutes = duration.toLongOrNull() ?: return@TextButton
        model.editRecovery { it.copy(expectedAt = instantFormat.format(Instant.now().plusSeconds(minutes * 60))) }
    }) { Text("Aplicar duración") }
    Text("Mientras tanto", style = MaterialTheme.typography.titleMedium)
    RecoveryChoices(listOf("wait" to "Esperar", "continue" to "Cambiar IA"), draft.decision, canEdit) { decision ->
        model.editRecovery { it.copy(decision = decision, alternative = if (decision == "continue") if (it.provider == "codex") "gemini" else "codex" else null) }
    }
    if (draft.decision == "continue") Text("Continuar con ${if (draft.alternative == "codex") "Codex" else "Gemini"}; el aviso original se mantiene.")
    Text("Tarea relacionada (opcional)", style = MaterialTheme.typography.titleMedium)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val tasks = model.data?.let { it.tasks + it.futureTasks + it.completedTasks }.orEmpty()
        (listOf(null to "Sin tarea") + tasks.map { it.id to it.title }).forEach { (id, title) ->
            Surface(modifier = Modifier.fillMaxWidth().selectable(id == draft.taskId, enabled = canEdit, role = Role.RadioButton) {
                model.editRecovery { it.copy(taskId = id) }
            }, color = if (id == draft.taskId) AgendaColors.Mint else AgendaColors.Surface, shape = RoundedCornerShape(16.dp)) {
                Text(title, Modifier.heightIn(min = 48.dp).padding(16.dp))
            }
        }
    }
    if (model.recoverySubmitted && !model.busy) Text("Reintenta con los mismos datos para evitar duplicados.")
    AgendaAction(if (model.recoverySubmitted) "Reintentar guardado" else "Guardar y programar aviso", !model.busy) { model.saveRecovery() }
    if (!model.recoveryIsNew && !model.recoverySubmitted) TextButton(enabled = !model.busy, onClick = { model.saveRecovery(cancel = true) }) { Text("Cancelar recuperación") }
}

@Composable
private fun RecoveryChoices(options: List<Pair<String, String>>, selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val active = value == selected
            Surface(modifier = Modifier.weight(1f).selectable(active, enabled = enabled, role = Role.RadioButton) { onSelect(value) },
                color = if (active) AgendaColors.Action else AgendaColors.Mint, shape = RoundedCornerShape(16.dp)) {
                Text(label, Modifier.heightIn(min = 48.dp).padding(16.dp), color = if (active) AgendaColors.Surface else AgendaColors.Action)
            }
        }
    }
}
