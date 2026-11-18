package com.pablomariano.agenda

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private enum class Details { PRIORITIES, PROPOSAL, PROTOTYPE, AI, ABOUT }

private data class SampleTask(
    val title: String,
    val project: String,
    val time: String,
    val impact: String,
    val details: Details? = null,
    val completed: Boolean = false,
) {
    val schedule get() = "$project · $time"
}

// shortcut: datos de demostración, hasta integrar el estado real de la agenda.
private val SampleTasks = listOf(
    SampleTask("Preparar propuesta", "Lanzamiento", "10:00", "Impacto alto", Details.PROPOSAL),
    SampleTask("Revisar prototipo", "Agenda personal", "Sin horario", "Impacto medio", Details.PROTOTYPE),
    SampleTask("Ajustar objetivos", "Agenda personal", "Sin horario", "Impacto bajo", completed = true),
)

private val SpanishChile = Locale.forLanguageTag("es-CL")
private val ShortDate = DateTimeFormatter.ofPattern("EEE d", SpanishChile)
private val FullDate = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", SpanishChile)

@Composable
internal fun TodayScreen() {
    val today = remember { LocalDate.now() }
    val dateLabel = remember(today) {
        today.format(FullDate).replaceFirstChar { it.titlecase(SpanishChile) }
    }
    val codex = "Codex · Hoy, 15:30"
    val gemini = remember(today) {
        val monday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        "Gemini · ${monday.format(ShortDate).replaceFirstChar { it.titlecase(SpanishChile) }}, 09:00"
    }
    var details by rememberSaveable { mutableStateOf<Details?>(null) }
    val typography = MaterialTheme.typography
    val completed = SampleTasks.count { it.completed }

    Surface(Modifier.fillMaxSize(), color = AgendaColors.Canvas) {
        Column(
            Modifier.safeDrawingPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(dateLabel, style = typography.labelLarge, color = AgendaColors.Muted)
                        Text("Hoy", Modifier.semantics { heading() }, style = typography.displaySmall)
                    }
                    Text(
                        "Datos de ejemplo",
                        Modifier.padding(horizontal = 8.dp),
                        style = typography.labelMedium,
                        color = AgendaColors.Action,
                        textAlign = TextAlign.End,
                    )
                    IconButton(onClick = { details = Details.ABOUT }, modifier = Modifier.size(48.dp)) {
                        Image(painterResource(R.drawable.hoy_ajustes), "Acerca de esta versión")
                    }
                }
                TodaySummary(completed, SampleTasks.size) { details = Details.PRIORITIES }
                SectionTitle("Prioridades", "Ver todas") { details = Details.PRIORITIES }

                SampleTasks.filterNot { it.completed }.forEachIndexed { index, task ->
                    val first = index == 0
                    SummaryCard(
                        indicator = (index + 1).toString().padStart(2, '0'),
                        title = task.title,
                        subtitle = task.schedule,
                        caption = task.impact,
                        indicatorBackground = if (first) AgendaColors.Mint else AgendaColors.Canvas,
                        accent = if (first) AgendaColors.Action else AgendaColors.Muted,
                        onClick = { details = task.details },
                    )
                }
                SectionTitle("Recuperación de IA", "Ver avisos") { details = Details.AI }
                SummaryCard(
                    indicator = "C",
                    title = codex,
                    subtitle = gemini,
                    caption = "Horas previstas",
                    indicatorBackground = AgendaColors.Sky,
                    accent = AgendaColors.Blue,
                    onClick = { details = Details.AI },
                )
            }
            TodayNavigation(
                Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
        }
    }

    details?.let { selected ->
        val (title, body) = when (selected) {
            Details.PRIORITIES -> "Prioridades de hoy" to SampleTasks.map { task ->
                val state = if (task.completed) "Completada" else "Pendiente"
                "${task.title}\n${task.schedule} · ${task.impact} · $state"
            }
            Details.PROPOSAL, Details.PROTOTYPE -> {
                val task = SampleTasks.first { it.details == selected }
                task.title to listOf(task.schedule, "${task.impact} · Pendiente")
            }
            Details.AI -> "Recuperación de IA" to listOf(
                "$codex\nVentana corta · Hora prevista",
                "$gemini\nVentana semanal · Hora prevista",
                "Estas horas son ejemplos de registro manual.",
            )
            Details.ABOUT -> "Agenda de prueba" to listOf(
                "Esta versión muestra la pantalla Hoy con datos de ejemplo.",
                "Puedes abrir las prioridades, los detalles de cada tarea y los avisos de IA.",
            )
        }
        AlertDialog(
            onDismissRequest = { details = null },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    body.forEach { Text(it, style = typography.bodyMedium) }
                }
            },
            confirmButton = { TextButton(onClick = { details = null }) { Text("Cerrar") } },
        )
    }
}

@Composable
private fun TodaySummary(completed: Int, total: Int, openPriorities: () -> Unit) {
    val progress = completed.toFloat() / total
    val typography = MaterialTheme.typography
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 189.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF164957), Color(0xFF086451)))),
    ) {
        Box(Modifier.matchParentSize()) {
            HeroAsset(R.drawable.hoy_orbita_1, 210, 78, -46)
            HeroAsset(R.drawable.hoy_orbita_2, 164, 55, -23)
            HeroAsset(R.drawable.hoy_orbita_3, 118, 32, 0)
            HeroAsset(R.drawable.hoy_orbita_4, 72, 9, 23)
            HeroAsset(R.drawable.hoy_punto, 10, -55, 38)
        }
        Column(
            Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("TU PLAN DE HOY", style = typography.labelMedium, color = Color.White)
            Text("$completed de $total", style = typography.displaySmall, color = Color.White)
            Text("prioridades completadas", style = typography.bodyMedium, color = Color.White)
            Box(
                Modifier
                    .widthIn(max = 145.dp)
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(AgendaColors.Blue)
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) },
            ) {
                Box(Modifier.fillMaxWidth(progress).height(7.dp).background(Color.White, CircleShape))
            }
            SoftButton("Planificar hoy", openPriorities)
        }
    }
}

@Composable
private fun BoxScope.HeroAsset(@DrawableRes drawable: Int, size: Int, x: Int, y: Int) {
    Image(
        painterResource(drawable),
        contentDescription = null,
        modifier = Modifier.align(Alignment.TopEnd)
            .offset(x.dp, y.dp)
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .size(size.dp),
    )
}

@Composable
private fun SectionTitle(title: String, action: String, onClick: () -> Unit) {
    val heading: @Composable (Modifier) -> Unit = { modifier ->
        Text(title, modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
    }
    if (LocalDensity.current.fontScale >= 1.4f) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            heading(Modifier.fillMaxWidth())
            SoftButton(action, onClick)
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            heading(Modifier.weight(1f))
            SoftButton(action, onClick)
        }
    }
}

@Composable
private fun SoftButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AgendaColors.Mint,
            contentColor = AgendaColors.Action,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SummaryCard(
    indicator: String,
    title: String,
    subtitle: String,
    caption: String,
    indicatorBackground: Color,
    accent: Color,
    onClick: () -> Unit,
) {
    val typography = MaterialTheme.typography
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().agendaShadow(shape),
        shape = shape,
        color = AgendaColors.Surface,
    ) {
        Row(
            Modifier.heightIn(min = 100.dp).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .background(indicatorBackground, RoundedCornerShape(12.dp))
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Text(indicator, style = typography.labelLarge, color = accent)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = typography.titleMedium)
                Text(subtitle, style = typography.bodyMedium, color = AgendaColors.Muted)
                Text(caption, style = typography.labelMedium, color = accent)
            }
            Text(
                "›",
                Modifier.clearAndSetSemantics {},
                style = typography.headlineSmall,
                color = AgendaColors.Muted,
            )
        }
    }
}

private val PreviewTabs = listOf(
    "Hoy" to R.drawable.nav_hoy,
    "Semana" to R.drawable.nav_semana,
    "Proyectos" to R.drawable.nav_proyectos,
    "Ideas" to R.drawable.nav_ideas,
)

@Composable
private fun TodayNavigation(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    val columns = if (LocalDensity.current.fontScale >= 1.4f) 2 else 4
    Surface(modifier.agendaShadow(shape), shape = shape, color = AgendaColors.Surface) {
        Column(
            Modifier.selectableGroup().padding(horizontal = 7.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PreviewTabs.chunked(columns).forEach { tabs ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tabs.forEach { (label, icon) ->
                        PreviewTab(label, icon, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewTab(label: String, @DrawableRes icon: Int, modifier: Modifier) {
    val active = icon == R.drawable.nav_hoy
    Column(
        modifier.heightIn(min = 56.dp)
            .selectable(selected = active, enabled = active, role = Role.Tab, onClick = {}),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Image(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(50.dp, 28.dp)
                .background(if (active) AgendaColors.Mint else Color.Transparent, CircleShape),
        )
        Text(
            label,
            color = if (active) AgendaColors.Ink else AgendaColors.Muted,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
    }
}

private fun Modifier.agendaShadow(shape: RoundedCornerShape) = shadow(
    elevation = 3.dp,
    shape = shape,
    clip = false,
    ambientColor = Color(0x390E3D36),
    spotColor = Color(0x520E3D36),
)
