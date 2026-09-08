package com.example.todaytodo

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

private val AppBackground = Color(0xFFF3F7FD)
private val HeaderBackground = Color(0xFFF3F7FD)
private val Aqua = Color(0xFF2F8FEF)
private val AquaSoft = Color(0xFFDCEEFF)
private val Lilac = Color(0xFF2F8FEF)
private val Steel = Color(0xFF7D91A6)
private val Grey = Color(0xFF7D91A6)
private val Ink = Color(0xFF17263A)
private val Line = Color(0xFFDFEAF5)
private val Card = Color(0xFFFFFFFF)

private val TitleFontFamily = FontFamily(Font(R.font.cafe24_ssurround))
private val BodyFontFamily = FontFamily(
    Font(R.font.nanum_square_round_regular, FontWeight.Normal),
    Font(R.font.nanum_square_round_bold, FontWeight.Bold),
)

data class TodoItem(
    val id: String,
    val title: String,
    val date: LocalDate,
    val completed: Boolean = false,
    val seriesId: String? = null,
)

data class DdayItem(val title: String, val date: LocalDate)

data class TodoBackup(val todos: List<TodoItem>, val ddays: List<DdayItem>)

private enum class TodoFilter(val label: String) {
    ALL("전체"), ACTIVE("진행 중"), DONE("완료")
}

private enum class RepeatType(val label: String) {
    DAILY("매일"), WEEKLY("매주"), MONTHLY("매월")
}

private enum class RepeatDateTarget { START, END }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TodoReminder.createNotificationChannel(this)
        TodoReminder.scheduleNext(this)
        TodoWidgetProvider.updateAll(this)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        }
        setContent { TodayTodoApp() }
    }

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST = 1001
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayTodoApp() {
    val context = LocalContext.current
    val store = remember { TodoStore(context.applicationContext) }
    val todos = remember { mutableStateListOf<TodoItem>().apply { addAll(store.load()) } }
    var filter by remember { mutableStateOf(TodoFilter.ALL) }
    var input by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    val ddays = remember { mutableStateListOf<DdayItem>().apply { addAll(store.loadDdays()) } }
    var showDdayManager by remember { mutableStateOf(false) }
    var showDdayDialog by remember { mutableStateOf(false) }
    var showDdayDatePicker by remember { mutableStateOf(false) }
    var editingDdayIndex by remember { mutableStateOf<Int?>(null) }
    var ddayTitleInput by remember { mutableStateOf("") }
    var ddayDateInput by remember { mutableStateOf(LocalDate.now()) }
    var todoToDelete by remember { mutableStateOf<TodoItem?>(null) }
    var showRepeatDialog by remember { mutableStateOf(false) }
    var showRepeatDatePicker by remember { mutableStateOf(false) }
    var repeatDateTarget by remember { mutableStateOf(RepeatDateTarget.START) }
    var repeatTitle by remember { mutableStateOf("") }
    var repeatType by remember { mutableStateOf(RepeatType.DAILY) }
    var repeatStartDate by remember { mutableStateOf(LocalDate.now()) }
    var repeatEndDate by remember { mutableStateOf(LocalDate.now().plusMonths(1)) }
    val repeatWeekdays = remember { mutableStateListOf(LocalDate.now().dayOfWeek) }

    fun persist() = store.save(todos)
    fun addTodo() {
        val title = input.trim()
        if (title.isEmpty()) return
        todos.add(0, TodoItem(UUID.randomUUID().toString(), title, selectedDate))
        input = ""
        persist()
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(store.createBackup(todos))
                } ?: error("백업 파일을 열 수 없습니다.")
            }.onSuccess {
                Toast.makeText(context, "백업이 저장됐어요", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "백업 저장에 실패했어요", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val backupText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                    it.readText()
                } ?: error("백업 파일을 열 수 없습니다.")
                store.readBackup(backupText)
            }.onSuccess { backup ->
                val merged = (todos + backup.todos).distinctBy { it.id }
                todos.clear()
                todos.addAll(merged)
                persist()
                if (backup.ddays.isNotEmpty()) {
                    ddays.clear()
                    ddays.addAll(backup.ddays.take(2))
                    store.saveDdays(ddays)
                }
                Toast.makeText(
                    context,
                    "${backup.todos.size}개 항목을 복원했어요",
                    Toast.LENGTH_SHORT,
                ).show()
            }.onFailure {
                Toast.makeText(context, "올바른 TodayTodo 백업 파일이 아니에요", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val selectedTodos = todos.filter { it.date == selectedDate }
    val visibleTodos = selectedTodos.filter {
        when (filter) {
            TodoFilter.ALL -> true
            TodoFilter.ACTIVE -> !it.completed
            TodoFilter.DONE -> it.completed
        }
    }
    val remaining = selectedTodos.count { !it.completed }
    val today = LocalDate.now()
    val weekStart = selectedDate.minusDays((selectedDate.dayOfWeek.value % 7).toLong())
    val weekDates = (0L..6L).map { weekStart.plusDays(it) }

    if (showDatePicker) {
        val datePickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.toEpochDay() * MILLIS_PER_DAY,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            selectedDate = LocalDate.ofEpochDay(it / MILLIS_PER_DAY)
                            filter = TodoFilter.ALL
                        }
                        showDatePicker = false
                    }
                ) { Text("선택") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("취소") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showDdayDatePicker) {
        val ddayPickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = ddayDateInput.toEpochDay() * MILLIS_PER_DAY,
        )
        DatePickerDialog(
            onDismissRequest = {
                showDdayDatePicker = false
                showDdayDialog = true
            },
            confirmButton = {
                TextButton(onClick = {
                    ddayPickerState.selectedDateMillis?.let {
                        ddayDateInput = LocalDate.ofEpochDay(it / MILLIS_PER_DAY)
                    }
                    showDdayDatePicker = false
                    showDdayDialog = true
                }) { Text("선택") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDdayDatePicker = false
                    showDdayDialog = true
                }) { Text("취소") }
            },
        ) { DatePicker(state = ddayPickerState) }
    }

    if (showDdayManager) {
        AlertDialog(
            onDismissRequest = { showDdayManager = false },
            title = { Text("디데이 관리") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ddays.isEmpty()) {
                        Text("등록된 디데이가 없어요", color = Grey)
                    }
                    ddays.forEachIndexed { index, item ->
                        OutlinedButton(
                            onClick = {
                                editingDdayIndex = index
                                ddayTitleInput = item.title
                                ddayDateInput = item.date
                                showDdayManager = false
                                showDdayDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.Start,
                            ) {
                                Text(item.title, fontWeight = FontWeight.Bold)
                                Text(formatDate(item.date), color = Grey, fontSize = 12.sp)
                            }
                        }
                    }
                    if (ddays.size < 2) {
                        Button(
                            onClick = {
                                editingDdayIndex = null
                                ddayTitleInput = ""
                                ddayDateInput = LocalDate.now()
                                showDdayManager = false
                                showDdayDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("새 디데이 추가") }
                    } else {
                        Text("디데이는 최대 2개까지 설정할 수 있어요", color = Grey, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDdayManager = false }) { Text("닫기") }
            },
        )
    }

    if (showDdayDialog) {
        AlertDialog(
            onDismissRequest = { showDdayDialog = false },
            title = { Text("디데이 설정") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = ddayTitleInput,
                        onValueChange = { ddayTitleInput = it },
                        label = { Text("디데이 이름") },
                        placeholder = { Text("예: 여행 가는 날") },
                        singleLine = true,
                    )
                    OutlinedButton(
                        onClick = {
                            showDdayDialog = false
                            showDdayDatePicker = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(formatDate(ddayDateInput))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = ddayTitleInput.isNotBlank(),
                    onClick = {
                        val newDday = DdayItem(ddayTitleInput.trim(), ddayDateInput)
                        val editIndex = editingDdayIndex
                        if (editIndex == null && ddays.size < 2) {
                            ddays.add(newDday)
                        } else if (editIndex != null && editIndex in ddays.indices) {
                            ddays[editIndex] = newDday
                        }
                        store.saveDdays(ddays)
                        showDdayDialog = false
                    },
                ) { Text("저장") }
            },
            dismissButton = {
                Row {
                    if (editingDdayIndex != null) {
                        TextButton(onClick = {
                            editingDdayIndex?.takeIf { it in ddays.indices }?.let { ddays.removeAt(it) }
                            store.saveDdays(ddays)
                            showDdayDialog = false
                        }) { Text("삭제", color = Grey) }
                    }
                    TextButton(onClick = { showDdayDialog = false }) { Text("취소") }
                }
            },
        )
    }

    todoToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { todoToDelete = null },
            title = { Text("할 일 삭제") },
            text = {
                Text(
                    if (target.seriesId == null) {
                        "‘${target.title}’ 항목을 삭제하시겠습니까?"
                    } else {
                        "‘${target.title}’은 반복 일정이에요. 하나만 삭제하거나 같은 반복 일정을 모두 삭제할 수 있어요."
                    }
                )
            },
            confirmButton = {
                Row {
                    if (target.seriesId != null) {
                        TextButton(onClick = {
                            todos.removeAll { it.seriesId == target.seriesId }
                            persist()
                            todoToDelete = null
                        }) { Text("전체 삭제", color = Grey) }
                    }
                    TextButton(onClick = {
                        todos.removeAll { it.id == target.id }
                        persist()
                        todoToDelete = null
                    }) {
                        Text(if (target.seriesId == null) "삭제" else "이 일정만 삭제", color = Grey)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { todoToDelete = null }) { Text("취소") }
            },
        )
    }

    if (showRepeatDatePicker) {
        val initialDate = when (repeatDateTarget) {
            RepeatDateTarget.START -> repeatStartDate
            RepeatDateTarget.END -> repeatEndDate
        }
        val repeatPickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = initialDate.toEpochDay() * MILLIS_PER_DAY,
        )
        DatePickerDialog(
            onDismissRequest = {
                showRepeatDatePicker = false
                showRepeatDialog = true
            },
            confirmButton = {
                TextButton(onClick = {
                    repeatPickerState.selectedDateMillis?.let {
                        val picked = LocalDate.ofEpochDay(it / MILLIS_PER_DAY)
                        when (repeatDateTarget) {
                            RepeatDateTarget.START -> {
                                repeatStartDate = picked
                                if (repeatEndDate.isBefore(picked)) repeatEndDate = picked
                            }
                            RepeatDateTarget.END -> repeatEndDate = picked
                        }
                    }
                    showRepeatDatePicker = false
                    showRepeatDialog = true
                }) { Text("선택") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRepeatDatePicker = false
                    showRepeatDialog = true
                }) { Text("취소") }
            },
        ) { DatePicker(state = repeatPickerState) }
    }

    if (showRepeatDialog) {
        AlertDialog(
            onDismissRequest = { showRepeatDialog = false },
            title = { Text("반복 일정 등록") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = repeatTitle,
                        onValueChange = { repeatTitle = it },
                        label = { Text("할 일") },
                        placeholder = { Text("예: 운동하기") },
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RepeatType.entries.forEach { option ->
                            FilterChip(
                                selected = repeatType == option,
                                onClick = { repeatType = option },
                                label = { Text(option.label) },
                            )
                        }
                    }
                    if (repeatType == RepeatType.WEEKLY) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            listOf(
                                listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY),
                                listOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                            ).forEach { weekRow ->
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    weekRow.forEach { day ->
                                        FilterChip(
                                            selected = day in repeatWeekdays,
                                            onClick = {
                                                if (day in repeatWeekdays) {
                                                    if (repeatWeekdays.size > 1) repeatWeekdays.remove(day)
                                                } else repeatWeekdays.add(day)
                                            },
                                            label = { Text(day.koreanShortName()) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        text = when (repeatType) {
                            RepeatType.DAILY -> "시작일부터 종료일까지 매일 등록됩니다."
                            RepeatType.WEEKLY -> "선택한 요일마다 등록됩니다."
                            RepeatType.MONTHLY -> "매월 ${repeatStartDate.dayOfMonth}일에 등록됩니다."
                        },
                        color = Grey,
                        fontSize = 12.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                repeatDateTarget = RepeatDateTarget.START
                                showRepeatDialog = false
                                showRepeatDatePicker = true
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("시작 ${repeatStartDate.monthValue}/${repeatStartDate.dayOfMonth}") }
                        OutlinedButton(
                            onClick = {
                                repeatDateTarget = RepeatDateTarget.END
                                showRepeatDialog = false
                                showRepeatDatePicker = true
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("종료 ${repeatEndDate.monthValue}/${repeatEndDate.dayOfMonth}") }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = repeatTitle.isNotBlank() && !repeatEndDate.isBefore(repeatStartDate),
                    onClick = {
                        val generated = createRecurringTodos(
                            repeatTitle.trim(),
                            repeatType,
                            repeatStartDate,
                            repeatEndDate,
                            repeatWeekdays.toSet(),
                        )
                        todos.addAll(0, generated)
                        persist()
                        showRepeatDialog = false
                        Toast.makeText(context, "${generated.size}개 일정을 등록했어요", Toast.LENGTH_SHORT).show()
                    },
                ) { Text("등록") }
            },
            dismissButton = {
                TextButton(onClick = { showRepeatDialog = false }) { Text("취소") }
            },
        )
    }

    val defaultTypography = MaterialTheme.typography
    val appTypography = defaultTypography.copy(
        displayLarge = defaultTypography.displayLarge.copy(fontFamily = BodyFontFamily),
        displayMedium = defaultTypography.displayMedium.copy(fontFamily = BodyFontFamily),
        displaySmall = defaultTypography.displaySmall.copy(fontFamily = BodyFontFamily),
        headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = BodyFontFamily),
        headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = BodyFontFamily),
        headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = BodyFontFamily),
        titleLarge = defaultTypography.titleLarge.copy(fontFamily = BodyFontFamily),
        titleMedium = defaultTypography.titleMedium.copy(fontFamily = BodyFontFamily),
        titleSmall = defaultTypography.titleSmall.copy(fontFamily = BodyFontFamily),
        bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = BodyFontFamily),
        bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = BodyFontFamily),
        bodySmall = defaultTypography.bodySmall.copy(fontFamily = BodyFontFamily),
        labelLarge = defaultTypography.labelLarge.copy(fontFamily = BodyFontFamily),
        labelMedium = defaultTypography.labelMedium.copy(fontFamily = BodyFontFamily),
        labelSmall = defaultTypography.labelSmall.copy(fontFamily = BodyFontFamily),
    )

    MaterialTheme(
        typography = appTypography,
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Aqua,
            secondary = Lilac,
            background = AppBackground,
            surface = Card,
            onPrimary = Ink,
            onSurface = Ink,
            outline = Steel,
        )
    ) {
        ProvideTextStyle(ComposeTextStyle(fontFamily = BodyFontFamily)) {
        Scaffold(containerColor = AppBackground) { contentPadding ->
            if (showSettingsScreen) {
                SettingsScreen(
                    modifier = Modifier.padding(contentPadding),
                    onBack = { showSettingsScreen = false },
                    onRepeat = {
                        repeatTitle = ""
                        repeatType = RepeatType.DAILY
                        repeatStartDate = selectedDate
                        repeatEndDate = selectedDate.plusMonths(1)
                        repeatWeekdays.clear()
                        repeatWeekdays.add(selectedDate.dayOfWeek)
                        showRepeatDialog = true
                    },
                    onDday = { showDdayManager = true },
                    onBackup = { backupLauncher.launch("TodayTodo-backup-${LocalDate.now()}.json") },
                    onRestore = { restoreLauncher.launch(arrayOf("application/json", "text/plain")) },
                )
            } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .background(AppBackground)
                    .padding(horizontal = 18.dp),
            ) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TODO",
                            color = Ink,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = TitleFontFamily,
                        )
                        Text(
                            text = when {
                                remaining > 0 -> "오늘 남은 할 일 ${remaining}개"
                                selectedTodos.isEmpty() -> "등록된 할 일이 없어요"
                                else -> "오늘 할 일을 모두 마쳤어요"
                            },
                            color = Grey,
                            fontSize = 13.sp,
                        )
                    }
                    Surface(
                        color = Card,
                        shape = CircleShape,
                        shadowElevation = 3.dp,
                    ) {
                        IconButton(onClick = { showSettingsScreen = true }) {
                            Text("⚙", color = Ink, fontSize = 22.sp)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Surface(
                    color = Card,
                    shape = RoundedCornerShape(22.dp),
                    shadowElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${selectedDate.monthValue}월",
                                color = Ink,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                formatDate(selectedDate),
                                color = Grey,
                                fontSize = 11.sp,
                                modifier = Modifier.clickable { showDatePicker = true },
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            weekDates.forEach { date ->
                                val selected = date == selectedDate
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(58.dp)
                                        .background(
                                            if (selected) Aqua else Color.Transparent,
                                            RoundedCornerShape(16.dp),
                                        )
                                        .clickable { selectedDate = date },
                                ) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        color = if (selected) Color.White else Ink,
                                        fontSize = 18.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    Text(
                                        text = date.dayOfWeek.koreanShortName(),
                                        color = when {
                                            selected -> Color.White.copy(alpha = 0.9f)
                                            date.dayOfWeek == DayOfWeek.SUNDAY -> Color(0xFFFF6B6B)
                                            else -> Grey
                                        },
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = selectedDate != today) {
                    TextButton(onClick = { selectedDate = today }, modifier = Modifier.fillMaxWidth()) {
                        Text("오늘로 돌아가기", color = Aqua, fontSize = 12.sp)
                    }
                }

                if (ddays.isNotEmpty()) {
                    Spacer(Modifier.height(if (selectedDate == today) 10.dp else 0.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ddays.forEach { item -> DdayCard(item, Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("할 일을 입력하세요") },
                        singleLine = true,
                        keyboardActions = KeyboardActions(onDone = { addTodo() }),
                        shape = RoundedCornerShape(20.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Card,
                            unfocusedContainerColor = Card,
                            focusedIndicatorColor = Aqua,
                            unfocusedIndicatorColor = Line,
                        ),
                        modifier = Modifier.weight(1f).height(54.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { addTodo() },
                        enabled = input.isNotBlank(),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Aqua,
                            contentColor = Color.White,
                            disabledContainerColor = Line,
                        ),
                        modifier = Modifier.size(54.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Text("+", fontSize = 30.sp, fontWeight = FontWeight.Light)
                    }
                }

                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TodoFilter.entries.forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text(option.label) },
                            shape = RoundedCornerShape(18.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Card,
                                labelColor = Grey,
                                selectedContainerColor = Aqua,
                                selectedLabelColor = Color.White,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = filter == option,
                                borderColor = Line,
                                selectedBorderColor = Aqua,
                            ),
                        )
                    }
                }

                AnimatedVisibility(visible = visibleTodos.isEmpty()) { EmptyState(filter) }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    items(visibleTodos, key = { it.id }) { todo ->
                        TodoRow(
                            todo = todo,
                            onToggle = {
                                val index = todos.indexOfFirst { it.id == todo.id }
                                if (index >= 0) {
                                    todos[index] = todo.copy(completed = !todo.completed)
                                    persist()
                                }
                            },
                            onDelete = { todoToDelete = todo },
                        )
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                }

                Surface(
                    color = Card,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    shadowElevation = 5.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        BottomNavItem("✓", "할 일", true) { }
                        BottomNavItem("▦", "달력", false) { showDatePicker = true }
                        BottomNavItem("⚙", "설정", false) { showSettingsScreen = true }
                    }
                }
            }
            }
        }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onRepeat: () -> Unit,
    onDday: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Card, shape = CircleShape, shadowElevation = 3.dp) {
                IconButton(onClick = onBack) {
                    Text("‹", color = Ink, fontSize = 32.sp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = "설정",
                    color = Ink,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = TitleFontFamily,
                )
                Text("TodayTodo를 내 방식대로 관리하세요", color = Grey, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(26.dp))
        Text("일정 관리", color = Grey, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        SettingsItem("↻", "반복 일정 등록", "매일 또는 원하는 요일의 할 일을 미리 등록해요", onRepeat)
        Spacer(Modifier.height(10.dp))
        SettingsItem("D", "디데이 설정", "홈 화면에 표시할 디데이를 최대 2개 관리해요", onDday)

        Spacer(Modifier.height(24.dp))
        Text("데이터 관리", color = Grey, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        SettingsItem("⇩", "백업 파일 저장", "할 일과 디데이를 파일로 안전하게 보관해요", onBackup)
        Spacer(Modifier.height(10.dp))
        SettingsItem("⇧", "백업 파일 복원", "저장해 둔 파일에서 데이터를 불러와요", onRestore)

        Spacer(Modifier.weight(1f))
        Surface(
            color = Card,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            shadowElevation = 5.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                BottomNavItem("✓", "할 일", false, onBack)
                BottomNavItem("▦", "달력", false, onBack)
                BottomNavItem("⚙", "설정", true) { }
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: String,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        color = Card,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(42.dp).background(AquaSoft, CircleShape),
            ) {
                Text(icon, color = Aqua, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(description, color = Grey, fontSize = 11.sp)
            }
            Text("›", color = Steel, fontSize = 24.sp)
        }
    }
}

@Composable
private fun DdayCard(item: DdayItem, modifier: Modifier = Modifier) {
    val days = ChronoUnit.DAYS.between(LocalDate.now(), item.date)
    val ddayText = when {
        days > 0 -> "D-$days"
        days < 0 -> "D+${-days}"
        else -> "D-DAY"
    }
    val description = when {
        days > 0 -> "${days}일 남음"
        days < 0 -> "${-days}일 지남"
        else -> "오늘"
    }

    Surface(
        color = Card,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 3.dp,
        modifier = modifier.height(94.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = item.title,
                color = Ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = ddayText,
                    color = Aqua,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${item.date.monthValue}.${item.date.dayOfMonth} · $description",
                    color = Grey,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun TodoRow(todo: TodoItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    Surface(
        color = Card,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .background(if (todo.completed) Aqua else Color.Transparent, CircleShape)
                    .border(2.dp, if (todo.completed) Aqua else Steel, CircleShape)
                    .clickable { onToggle() },
            ) {
                if (todo.completed) {
                    Text("✓", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                text = todo.title,
                color = if (todo.completed) Grey else Ink,
                fontSize = 16.sp,
                textDecoration = if (todo.completed) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
            )
            TextButton(onClick = onDelete) {
                Text("⌫", color = Grey, fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun BottomNavItem(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Text(
            text = icon,
            color = if (selected) Aqua else Grey,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = if (selected) Aqua else Grey,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun EmptyState(filter: TodoFilter) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 54.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(62.dp).background(AquaSoft.copy(alpha = 0.42f), CircleShape),
        ) {
            Text("✓", color = Grey, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = when (filter) {
                TodoFilter.ALL -> "이 날짜에 할 일을 추가해보세요"
                TodoFilter.ACTIVE -> "진행 중인 할 일이 없어요"
                TodoFilter.DONE -> "완료한 할 일이 없어요"
            },
            color = Grey,
            fontSize = 15.sp,
        )
    }
}

internal class TodoStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("today_todo", Context.MODE_PRIVATE)

    fun load(): List<TodoItem> = runCatching {
        decodeTodos(JSONArray(preferences.getString("todos", "[]")))
    }.getOrDefault(emptyList())

    fun loadDdays(): List<DdayItem> = runCatching {
        preferences.getString("ddays", null)?.let { decodeDdays(JSONArray(it)) }
            ?: preferences.getString("dday", null)?.let {
                listOf(decodeDday(JSONObject(it)))
            }
            ?: emptyList()
    }.getOrDefault(emptyList()).take(2)

    fun saveDdays(ddays: List<DdayItem>) {
        preferences.edit()
            .putString("ddays", encodeDdays(ddays.take(2)).toString())
            .remove("dday")
            .apply()
    }

    fun createBackup(todos: List<TodoItem>): String = JSONObject()
        .put("app", "TodayTodo")
        .put("version", 2)
        .put("createdAt", java.time.ZonedDateTime.now().toString())
        .put("todos", encodeTodos(todos))
        .put("ddays", encodeDdays(loadDdays()))
        .toString(2)

    fun readBackup(text: String): TodoBackup {
        val backup = JSONObject(text)
        require(backup.optString("app") == "TodayTodo")
        val restoredDdays = when {
            backup.has("ddays") -> decodeDdays(backup.getJSONArray("ddays"))
            backup.optJSONObject("dday") != null -> listOf(decodeDday(backup.getJSONObject("dday")))
            else -> emptyList()
        }
        return TodoBackup(decodeTodos(backup.getJSONArray("todos")), restoredDdays.take(2))
    }

    private fun encodeDday(dday: DdayItem): JSONObject = JSONObject()
        .put("title", dday.title)
        .put("date", dday.date.toString())

    private fun decodeDday(item: JSONObject): DdayItem =
        DdayItem(item.getString("title"), LocalDate.parse(item.getString("date")))

    private fun encodeDdays(ddays: List<DdayItem>): JSONArray = JSONArray().apply {
        ddays.forEach { put(encodeDday(it)) }
    }

    private fun decodeDdays(array: JSONArray): List<DdayItem> = buildList {
        for (index in 0 until array.length()) add(decodeDday(array.getJSONObject(index)))
    }

    private fun decodeTodos(array: JSONArray): List<TodoItem> =
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    TodoItem(
                        id = item.getString("id"),
                        title = item.getString("title"),
                        date = item.optString("date")
                            .takeIf { it.isNotBlank() }
                            ?.let(LocalDate::parse)
                            ?: LocalDate.now(),
                        completed = item.optBoolean("completed"),
                        seriesId = item.optString("seriesId")
                            .takeIf { it.isNotBlank() && it != "null" },
                    )
                )
            }
        }

    fun save(todos: List<TodoItem>) {
        preferences.edit().putString("todos", encodeTodos(todos).toString()).apply()
        TodoReminder.scheduleNext(context)
        TodoWidgetProvider.updateAll(context)
    }

    private fun encodeTodos(todos: List<TodoItem>): JSONArray {
        val array = JSONArray()
        todos.forEach { todo ->
            array.put(
                JSONObject()
                    .put("id", todo.id)
                    .put("title", todo.title)
                    .put("date", todo.date.toString())
                    .put("completed", todo.completed)
                    .put("seriesId", todo.seriesId ?: JSONObject.NULL)
            )
        }
        return array
    }
}

private const val MILLIS_PER_DAY = 86_400_000L

private fun formatDate(date: LocalDate): String {
    val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
    return date.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일")) + " ($dayOfWeek)"
}

private fun DayOfWeek.koreanShortName(): String = when (this) {
    DayOfWeek.MONDAY -> "월"
    DayOfWeek.TUESDAY -> "화"
    DayOfWeek.WEDNESDAY -> "수"
    DayOfWeek.THURSDAY -> "목"
    DayOfWeek.FRIDAY -> "금"
    DayOfWeek.SATURDAY -> "토"
    DayOfWeek.SUNDAY -> "일"
}

private fun createRecurringTodos(
    title: String,
    repeatType: RepeatType,
    startDate: LocalDate,
    endDate: LocalDate,
    weekdays: Set<DayOfWeek>,
): List<TodoItem> {
    val seriesId = UUID.randomUUID().toString()
    val dates = when (repeatType) {
        RepeatType.DAILY -> generateSequence(startDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(endDate) }
        RepeatType.WEEKLY -> generateSequence(startDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(endDate) }
            .filter { it.dayOfWeek in weekdays }
        RepeatType.MONTHLY -> {
            val targetDay = startDate.dayOfMonth
            generateSequence(YearMonth.from(startDate)) { it.plusMonths(1) }
                .map { month -> month.atDay(targetDay.coerceAtMost(month.lengthOfMonth())) }
                .dropWhile { it.isBefore(startDate) }
                .takeWhile { !it.isAfter(endDate) }
        }
    }
    return dates.take(MAX_RECURRING_OCCURRENCES).map { date ->
        TodoItem(UUID.randomUUID().toString(), title, date, seriesId = seriesId)
    }.toList()
}

private const val MAX_RECURRING_OCCURRENCES = 5_000
