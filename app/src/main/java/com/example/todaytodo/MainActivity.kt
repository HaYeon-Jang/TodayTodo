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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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

private val AppBackground = Color(0xFFF4FBFB)
private val Aqua = Color(0xFF6DD6DA)
private val AquaSoft = Color(0xFF95D9DA)
private val Lilac = Color(0xFFAE8CA3)
private val Steel = Color(0xFFA2ABB5)
private val Grey = Color(0xFF817F82)
private val Ink = Color(0xFF343336)
private val Line = Color(0xFFD8E0E4)
private val Card = Color(0xFFFFFFFF)

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
    var showAppMenu by remember { mutableStateOf(false) }
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

    MaterialTheme(
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
        Scaffold(containerColor = AppBackground) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "TODO",
                        color = Ink,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        IconButton(onClick = { showAppMenu = true }) {
                            Text(
                                text = "⋮",
                                color = Grey,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        DropdownMenu(
                            expanded = showAppMenu,
                            onDismissRequest = { showAppMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("반복 일정 등록") },
                                onClick = {
                                    showAppMenu = false
                                    repeatTitle = ""
                                    repeatType = RepeatType.DAILY
                                    repeatStartDate = selectedDate
                                    repeatEndDate = selectedDate.plusMonths(1)
                                    repeatWeekdays.clear()
                                    repeatWeekdays.add(selectedDate.dayOfWeek)
                                    showRepeatDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("디데이 설정") },
                                onClick = {
                                    showAppMenu = false
                                    showDdayManager = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("백업 파일 저장") },
                                onClick = {
                                    showAppMenu = false
                                    backupLauncher.launch("TodayTodo-backup-${LocalDate.now()}.json")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("백업 파일 복원") },
                                onClick = {
                                    showAppMenu = false
                                    restoreLauncher.launch(arrayOf("application/json", "text/plain"))
                                },
                            )
                        }
                    }
                }
                Text(
                    text = when {
                        remaining > 0 -> "남은 할 일 ${remaining}개"
                        selectedTodos.isEmpty() -> "등록된 할 일이 없어요"
                        else -> "이날의 할 일을 모두 마쳤어요"
                    },
                    color = Grey,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                )

                if (ddays.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ddays.forEach { item ->
                            DdayCard(item, Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { selectedDate = selectedDate.minusDays(1) }) {
                        Text("‹ 이전", color = Grey)
                    }
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(50.dp),
                    ) {
                        Text(
                            text = formatDate(selectedDate),
                            color = if (selectedDate == today) Lilac else Ink,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    TextButton(onClick = { selectedDate = selectedDate.plusDays(1) }) {
                        Text("다음 ›", color = Grey)
                    }
                }

                AnimatedVisibility(visible = selectedDate != today) {
                    TextButton(
                        onClick = { selectedDate = today },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    ) {
                        Text("오늘로 돌아가기", color = Lilac)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("${selectedDate.monthValue}/${selectedDate.dayOfMonth} 할 일") },
                        singleLine = true,
                        keyboardActions = KeyboardActions(onDone = { addTodo() }),
                        shape = RoundedCornerShape(16.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Card,
                            unfocusedContainerColor = Card,
                            focusedIndicatorColor = Aqua,
                            unfocusedIndicatorColor = Line,
                        ),
                        modifier = Modifier.weight(1f).height(50.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { addTodo() },
                        enabled = input.isNotBlank(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Aqua,
                            contentColor = Ink,
                        ),
                        modifier = Modifier.height(50.dp),
                    ) {
                        Text("추가", fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TodoFilter.entries.forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text(option.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Lilac.copy(alpha = 0.22f),
                                selectedLabelColor = Grey,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = filter == option,
                                borderColor = Line,
                                selectedBorderColor = Lilac,
                            ),
                        )
                    }
                }

                AnimatedVisibility(visible = visibleTodos.isEmpty()) {
                    EmptyState(filter)
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.fillMaxSize(),
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
                            onDelete = {
                                todoToDelete = todo
                            },
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
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
        color = Lilac.copy(alpha = 0.18f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Text(
                text = item.title,
                color = Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = ddayText,
                    color = Grey,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${item.date.monthValue}.${item.date.dayOfMonth} · $description",
                    color = Grey,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TodoRow(todo: TodoItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    Surface(
        color = Card,
        shape = RoundedCornerShape(15.dp),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 6.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
        ) {
            Checkbox(
                checked = todo.completed,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Aqua,
                    checkmarkColor = Ink,
                    uncheckedColor = Line,
                ),
            )
            Text(
                text = todo.title,
                color = if (todo.completed) Grey else Ink,
                fontSize = 16.sp,
                textDecoration = if (todo.completed) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            )
            TextButton(onClick = onDelete) {
                Text("삭제", color = Grey, fontSize = 13.sp)
            }
        }
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
