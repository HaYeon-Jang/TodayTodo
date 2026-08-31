package com.example.todaytodo

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
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
import java.util.UUID

private val Cream = Color(0xFFFFF9F4)
private val Coral = Color(0xFFFF6B57)
private val Ink = Color(0xFF24211F)
private val Muted = Color(0xFF77706B)
private val Line = Color(0xFFECE3DC)
private val Card = Color(0xFFFFFFFF)

data class TodoItem(
    val id: String,
    val title: String,
    val completed: Boolean = false,
)

private enum class TodoFilter(val label: String) {
    ALL("전체"), ACTIVE("진행 중"), DONE("완료")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TodayTodoApp() }
    }
}

@Composable
private fun TodayTodoApp() {
    val context = LocalContext.current
    val store = remember { TodoStore(context.applicationContext) }
    val todos = remember { mutableStateListOf<TodoItem>().apply { addAll(store.load()) } }
    var filter by remember { mutableStateOf(TodoFilter.ALL) }
    var input by remember { mutableStateOf("") }

    fun persist() = store.save(todos)
    fun addTodo() {
        val title = input.trim()
        if (title.isEmpty()) return
        todos.add(0, TodoItem(UUID.randomUUID().toString(), title))
        input = ""
        persist()
    }

    val visibleTodos = todos.filter {
        when (filter) {
            TodoFilter.ALL -> true
            TodoFilter.ACTIVE -> !it.completed
            TodoFilter.DONE -> it.completed
        }
    }
    val remaining = todos.count { !it.completed }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Coral,
            background = Cream,
            surface = Card,
            onSurface = Ink,
        )
    ) {
        Scaffold(containerColor = Cream) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "오늘할일",
                    color = Ink,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = if (remaining == 0) "오늘 할 일을 모두 마쳤어요" else "남은 할 일 ${remaining}개",
                    color = Muted,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 22.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("새로운 할 일") },
                        singleLine = true,
                        keyboardActions = KeyboardActions(onDone = { addTodo() }),
                        shape = RoundedCornerShape(16.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Card,
                            unfocusedContainerColor = Card,
                            focusedIndicatorColor = Coral,
                            unfocusedIndicatorColor = Line,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { addTodo() },
                        enabled = input.isNotBlank(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Coral),
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
                                selectedContainerColor = Coral.copy(alpha = 0.14f),
                                selectedLabelColor = Coral,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = filter == option,
                                borderColor = Line,
                                selectedBorderColor = Coral.copy(alpha = 0.35f),
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
                    checkedColor = Coral,
                    uncheckedColor = Line,
                ),
            )
            Text(
                text = todo.title,
                color = if (todo.completed) Muted else Ink,
                fontSize = 16.sp,
                textDecoration = if (todo.completed) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            )
            TextButton(onClick = onDelete) {
                Text("삭제", color = Muted, fontSize = 13.sp)
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
            modifier = Modifier.size(62.dp).background(Coral.copy(alpha = 0.12f), CircleShape),
        ) {
            Text("✓", color = Coral, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = when (filter) {
                TodoFilter.ALL -> "첫 할 일을 추가해보세요"
                TodoFilter.ACTIVE -> "진행 중인 할 일이 없어요"
                TodoFilter.DONE -> "완료한 할 일이 없어요"
            },
            color = Muted,
            fontSize = 15.sp,
        )
    }
}

private class TodoStore(context: Context) {
    private val preferences = context.getSharedPreferences("today_todo", Context.MODE_PRIVATE)

    fun load(): List<TodoItem> = runCatching {
        val array = JSONArray(preferences.getString("todos", "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    TodoItem(
                        id = item.getString("id"),
                        title = item.getString("title"),
                        completed = item.optBoolean("completed"),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun save(todos: List<TodoItem>) {
        val array = JSONArray()
        todos.forEach { todo ->
            array.put(
                JSONObject()
                    .put("id", todo.id)
                    .put("title", todo.title)
                    .put("completed", todo.completed)
            )
        }
        preferences.edit().putString("todos", array.toString()).apply()
    }
}
