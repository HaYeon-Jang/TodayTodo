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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
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
)

private enum class TodoFilter(val label: String) {
    ALL("전체"), ACTIVE("진행 중"), DONE("완료")
}

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
            }.onSuccess { restoredTodos ->
                val merged = (todos + restoredTodos).distinctBy { it.id }
                todos.clear()
                todos.addAll(merged)
                persist()
                Toast.makeText(
                    context,
                    "${restoredTodos.size}개 항목을 복원했어요",
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
                        modifier = Modifier.weight(1f),
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
                        modifier = Modifier.weight(1f),
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
                        modifier = Modifier.height(56.dp),
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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
                                todos.removeAll { it.id == todo.id }
                                persist()
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
private fun TodoRow(todo: TodoItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    Surface(
        color = Card,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
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

    fun createBackup(todos: List<TodoItem>): String = JSONObject()
        .put("app", "TodayTodo")
        .put("version", 1)
        .put("createdAt", java.time.ZonedDateTime.now().toString())
        .put("todos", encodeTodos(todos))
        .toString(2)

    fun readBackup(text: String): List<TodoItem> {
        val backup = JSONObject(text)
        require(backup.optString("app") == "TodayTodo")
        return decodeTodos(backup.getJSONArray("todos"))
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
