package com.pablomariano.agenda

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val AgendaTimeZone = ZoneId.of("America/Santiago")
private val Chile = Locale.forLanguageTag("es-CL")
private val DateLabel = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Chile)

@Composable
internal fun PersonalAgendaScreen(model: PersonalAgendaModel) {
    val draft = model.draft
    val projectPage = model.page == AgendaPage.PROJECT
    val selectedTask = model.selectedTask

    BackHandler(enabled = model.signedIn && (draft != null || selectedTask != null || projectPage)) {
        if (draft != null) model.closeForm()
        else if (selectedTask != null) model.selectTask(null)
        else model.navigate(AgendaPage.PROJECTS)
    }
    Surface(Modifier.fillMaxSize(), color = AgendaColors.Canvas) {
        Column(
            Modifier.safeDrawingPadding().imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            key(model.signedIn, model.page, draft?.id) {
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
                        model.page == AgendaPage.TODAY -> PersonalToday(model, model::selectTask)
                        model.page == AgendaPage.PROJECTS -> ProjectList(model)
                        else -> ProjectDetail(model, model::selectTask)
                    }
                }
            }
            if (model.signedIn && draft == null && selectedTask == null && !projectPage) {
                TodayNavigation(
                    modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                    activeTab = if (model.page == AgendaPage.TODAY) 0 else 2,
                    projectsEnabled = true,
                    onNavigate = { tab ->
                        model.navigate(if (tab == 0) AgendaPage.TODAY else AgendaPage.PROJECTS)
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
private fun PersonalToday(model: PersonalAgendaModel, openTask: (AgendaTask) -> Unit) {
    val today = LocalDate.now(AgendaTimeZone).format(DateLabel).replaceFirstChar { it.titlecase(Chile) }
    PageHeading("Hoy", today)
    val tasks = model.data?.tasks
    val completed = model.data?.completedTasks.orEmpty().filter { it.completedToday }
    if (tasks != null) {
        AgendaPanel {
            Text("Tu plan de hoy", style = MaterialTheme.typography.labelLarge, color = AgendaColors.Action)
            Text(
                "${completed.size} de ${tasks.size + completed.size} tareas completadas",
                style = MaterialTheme.typography.headlineSmall,
            )
            if (tasks.isEmpty()) {
                Text("Crea una tarea en un proyecto para verla aquí.", style = MaterialTheme.typography.bodyMedium)
            }
            AgendaAction("Ir a Proyectos", enabled = !model.busy) { model.navigate(AgendaPage.PROJECTS) }
        }
        if (tasks.isNotEmpty()) {
            Text("Pendientes de hoy", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            TaskRows(tasks, openTask)
        }
        if (completed.isNotEmpty()) {
            Text("Completadas hoy", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            TaskRows(completed, openTask)
        }
        Text("Recuperación de IA", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        Text("Sin recuperaciones registradas.", color = AgendaColors.Muted, style = MaterialTheme.typography.bodyMedium)
    }
    ReminderPermissions(model.reminders)
    RefreshAction(model)
    TextButton(onClick = model::signOut, enabled = !model.busy) { Text("Cerrar sesión") }
}

@Composable
private fun ProjectList(model: PersonalAgendaModel) {
    val snapshot = model.data
    val projects = snapshot?.projects.orEmpty().sortedBy { it.importance.ordinal }
    PageHeading("Proyectos", if (projects.size == 1) "1 proyecto activo" else "${projects.size} proyectos activos")
    val first = projects.firstOrNull()
    if (first != null && snapshot != null) {
        AgendaPanel {
            Text("En foco", style = MaterialTheme.typography.labelLarge, color = AgendaColors.Action)
            Text(first.title, style = MaterialTheme.typography.headlineSmall)
            Text("Importancia ${first.importance.importance.lowercase()}", style = MaterialTheme.typography.bodyMedium)
            AgendaAction("Abrir proyecto", !model.busy) { model.navigate(AgendaPage.PROJECT, first.id) }
        }
        if (projects.size > 1) Text("Otros proyectos", style = MaterialTheme.typography.headlineSmall)
        projects.drop(1).forEach { project ->
            val count = snapshot.tasks.count { it.projectId == project.id }
            SummaryCard(
                indicator = project.title.take(2).uppercase(),
                title = project.title,
                subtitle = if (count == 1) "1 pendiente para hoy" else "$count pendientes para hoy",
                caption = "Importancia ${project.importance.importance.lowercase()}",
                indicatorBackground = AgendaColors.Sky,
                accent = AgendaColors.Blue,
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
        val tasks = model.data?.tasks.orEmpty().filter { it.projectId == project.id }
        AgendaPanel {
            Text("Importancia ${project.importance.importance.lowercase()}", color = AgendaColors.Action)
            Text(if (tasks.size == 1) "1 tarea pendiente" else "${tasks.size} tareas pendientes", style = MaterialTheme.typography.titleMedium)
            AgendaAction("Nueva tarea", !model.busy) { model.openForm(project.id) }
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
            caption = if (task.completed) "Completada" else "Impacto ${task.impact.impact.lowercase()}",
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
        if (isProject) "Nuevo proyecto" else if (draft.original != null) "Editar tarea" else "Crear tarea",
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
        if (draft.submitted) "Reintentar guardado" else if (isProject) "Crear proyecto" else if (draft.original != null) "Guardar cambios" else "Crear tarea",
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
