package com.example // パッケージ宣言

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add // スニペット追加用アイコン
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen // ファイルインポート用アイコン
import androidx.compose.material.icons.filled.Refresh // リセット用アイコン
import androidx.compose.material.icons.filled.DeleteSweep // スニペット全削除用アイコン
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.res.useResource
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import javax.swing.JFileChooser
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

// スニペットデータを表すデータクラス
@Serializable
data class Snippet(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val code: String,
)

// JSONシリアライズ/デシリアライズ用の設定
private val jsonFormat = Json { prettyPrint = true; ignoreUnknownKeys = true }
private val snippetsFilePath: Path = Paths.get(System.getProperty("user.home"), ".snippet_manager_data.json")

// スニペットをJSONファイルに保存する関数
fun saveSnippets(snippets: List<Snippet>) {
    try {
        val jsonString = jsonFormat.encodeToString(snippets)
        Files.writeString(snippetsFilePath, jsonString)
    } catch (e: Exception) {
        println("スニペットの保存に失敗しました: ${e.message}")
    }
}

// スニペットインポート処理の共通関数
fun importSnippetsFromFile(
    filePath: Path,
    currentSnippets: List<Snippet>,
    onResult: (newSnippets: List<Snippet>?, feedback: String) -> Unit
) {
    try {
        if (!Files.exists(filePath)) {
            onResult(null, "指定されたファイルが見つかりません: $filePath")
            return
        }
        val jsonString = Files.readString(filePath)
        val importedSnippetsRaw = jsonFormat.decodeFromString<List<Snippet>>(jsonString)
        val importedSnippetsWithNewIds = importedSnippetsRaw.map { it.copy(id = UUID.randomUUID().toString()) }
        val mergedSnippets = currentSnippets + importedSnippetsWithNewIds
        saveSnippets(mergedSnippets) // ファイルにも保存
        onResult(mergedSnippets, "${importedSnippetsWithNewIds.size}件のスニペットをインポートしました。")
        println("インポート成功: ${filePath.fileName}")
    } catch (ioe: java.io.IOException) {
        onResult(null, "ファイルの読み込みに失敗しました。パス: $filePath, 詳細: ${ioe.localizedMessage}")
        println("インポート失敗 (IO): ${ioe.message}")
    } catch (jsonEx: SerializationException) {
        onResult(null, "JSONファイルの形式が正しくありません。詳細: ${jsonEx.localizedMessage}")
        println("インポート失敗 (JSON): ${jsonEx.message}")
    } catch (secEx: SecurityException) {
        onResult(null, "ファイルへのアクセス権がありません。パス: $filePath, 詳細: ${secEx.localizedMessage}")
        println("インポート失敗 (Security): ${secEx.message}")
    } catch (e: Exception) { // その他の一般的な例外
        onResult(null, "スニペットのインポート中に予期せぬエラーが発生しました: ${e.localizedMessage}")
        println("インポート失敗 (General): ${e.message}")
    }
}

// リソース取得用のダミークラス
object ResourceLoader

// スニペットをJSONファイルから読み込む関数
fun loadSnippets(): List<Snippet> {
    try {
        if (Files.exists(snippetsFilePath)) {
            val jsonString = Files.readString(snippetsFilePath)
            return jsonFormat.decodeFromString<List<Snippet>>(jsonString)
        } else {
            println("ユーザーのスニペットファイルが見つかりません。デフォルトスニペットを読み込みます。")
            val defaultSnippetsJson = try {
                // クラスローダー経由でリソース取得、見つからない場合は ResourceLoader 経由で取得
                val inputStream = Thread.currentThread().contextClassLoader.getResourceAsStream("default_snippets.json")
                    ?: ResourceLoader::class.java.getResourceAsStream("/default_snippets.json")
                inputStream?.bufferedReader()?.use { it.readText() }
            } catch (e: Exception) {
                println("デフォルトスニペットの読み込みに失敗しました: ${e.message}")
                null
            }

            if (defaultSnippetsJson != null) {
                try {
                    val defaultSnippets = jsonFormat.decodeFromString<List<Snippet>>(defaultSnippetsJson)
                    saveSnippets(defaultSnippets)
                    println("${snippetsFilePath} にデフォルトスニペットを保存しました。")
                    return defaultSnippets
                } catch (e: Exception) {
                    println("デフォルトスニペットの解析または保存に失敗しました: ${e.message}")
                }
            }
            return emptyList()
        }
    } catch (e: Exception) {
        println("スニペットの読み込み処理中にエラーが発生しました: ${e.message}")
        return emptyList()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    var snippets by remember { mutableStateOf(loadSnippets()) }
    var searchQuery by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    var editingSnippet by remember { mutableStateOf<Snippet?>(null) }
    var showImportFeedback by remember { mutableStateOf<String?>(null) }
    var importFilePathInput by remember { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current

    val filteredSnippets = snippets.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
                it.code.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("コードスニペット管理") },
                actions = {
                    IconButton(onClick = {
                        editingSnippet = null
                        showDialog = true
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "スニペットを追加する")
                    }
                    IconButton(onClick = {
                        // JSONファイルのみをフィルタする JFileChooser
                        val chooser = JFileChooser().apply {
                            fileFilter = javax.swing.filechooser.FileNameExtensionFilter("JSONファイル", "json")
                        }
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            val file = chooser.selectedFile.toPath()
                            importSnippetsFromFile(file, snippets) { newSnippets, feedback ->
                                showImportFeedback = feedback
                                if (newSnippets != null) {
                                    snippets = newSnippets
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = "JSONファイルをインポートする")
                    }
                    IconButton(onClick = {
                        showResetDialog = true
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "スニペットを初期化する")
                    }
                    IconButton(onClick = { showClearAllDialog = true }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "すべてのスニペットを削除する")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            showImportFeedback?.let { feedback ->
                Text(
                    feedback,
                    color = if (feedback.contains("失敗")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("スニペットを検索...") },
                modifier = Modifier.fillMaxWidth()
            )

            // ファイルパス直接入力UIは不要なので削除

            if (filteredSnippets.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(if (snippets.isEmpty()) "スニペットがありません。\n「+」ボタンから追加するか、ファイルをインポートしてください。" else "検索結果がありません。")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredSnippets, key = { it.id }) { snippet ->
                        SnippetItem(
                            snippet = snippet,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(snippet.code))
                                println("${snippet.title} をクリップボードにコピーしました。")
                            },
                            onEdit = {
                                editingSnippet = snippet
                                showDialog = true
                            },
                            onDelete = {
                                snippets = snippets.filterNot { it.id == snippet.id }
                                saveSnippets(snippets)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        SnippetEditDialog(
            snippetToEdit = editingSnippet,
            onDismiss = { showDialog = false },
            onSave = { updatedSnippet ->
                if (editingSnippet == null) {
                    snippets = snippets + updatedSnippet
                } else {
                    snippets = snippets.map { if (it.id == updatedSnippet.id) updatedSnippet else it }
                }
                saveSnippets(snippets)
                showDialog = false
                editingSnippet = null
            }
        )
    }

    // リセット確認ダイアログ
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("確認") },
            text = { Text("スニペットを初期状態に戻します。よろしいですか？") },
            confirmButton = {
                TextButton(onClick = {
                    try { Files.deleteIfExists(snippetsFilePath) } catch (_: Exception) {}
                    snippets = loadSnippets()
                    showImportFeedback = "デフォルトスニペットにリセットしました。"
                    showResetDialog = false
                }) { Text("はい") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("いいえ") }
            }
        )
    }

    // 全スニペット削除確認ダイアログ
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("確認") },
            text = { Text("すべてのスニペットを削除します。よろしいですか？") },
            confirmButton = {
                TextButton(onClick = {
                    snippets = emptyList()
                    try { Files.deleteIfExists(snippetsFilePath) } catch (_: Exception) {}
                    showImportFeedback = "すべてのスニペットを削除しました。"
                    showClearAllDialog = false
                }) { Text("はい") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) { Text("いいえ") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetItem(
    snippet: Snippet,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(snippet.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = snippet.code.lines().firstOrNull() ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { Surface(shadowElevation = 4.dp, shape = MaterialTheme.shapes.small) { Text("コピー", modifier = Modifier.padding(4.dp)) } },
                    state = rememberTooltipState()
                ) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "コードをコピー")
                    }
                }
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { Surface(shadowElevation = 4.dp, shape = MaterialTheme.shapes.small) { Text("編集", modifier = Modifier.padding(4.dp)) } },
                    state = rememberTooltipState()
                ) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "スニペットを編集")
                    }
                }
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { Surface(shadowElevation = 4.dp, shape = MaterialTheme.shapes.small) { Text("削除", modifier = Modifier.padding(4.dp)) } },
                    state = rememberTooltipState()
                ) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "スニペットを削除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetEditDialog(
    snippetToEdit: Snippet?,
    onDismiss: () -> Unit,
    onSave: (Snippet) -> Unit
) {
    var title by remember { mutableStateOf(snippetToEdit?.title ?: "") }
    var code by remember { mutableStateOf(snippetToEdit?.code ?: "") }

    val isTitleValid = title.isNotBlank()
    val isCodeValid = code.isNotBlank()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.width(600.dp).wrapContentHeight(),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (snippetToEdit == null) "新しいスニペットを作成" else "スニペットを編集",
                    style = MaterialTheme.typography.headlineSmall
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("タイトル *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !isTitleValid && title.isNotEmpty()
                )

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("コード *") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 300.dp),
                    isError = !isCodeValid && code.isNotEmpty()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("キャンセル")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (isTitleValid && isCodeValid) {
                                onSave(
                                    Snippet(
                                        id = snippetToEdit?.id ?: UUID.randomUUID().toString(),
                                        title = title.trim(),
                                        code = code
                                    )
                                )
                            }
                        },
                        enabled = isTitleValid && isCodeValid
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "シンプルなコードスニペット管理",
        icon = BitmapPainter(
            useResource("snippetbutton.png") { loadImageBitmap(it) }
        )
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(modifier = Modifier.fillMaxSize()) {
                App()
            }
        }
    }
}
