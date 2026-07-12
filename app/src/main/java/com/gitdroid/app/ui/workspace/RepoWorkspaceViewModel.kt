package com.gitdroid.app.ui.workspace

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gitdroid.app.data.api.NetUtil
import com.gitdroid.app.git.GitFileEntry
import com.gitdroid.app.git.GitManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RepoWorkspaceViewModel(application: Application) : AndroidViewModel(application) {

    private val _repoDir = MutableStateFlow<File?>(null)
    val repoDir: StateFlow<File?> = _repoDir.asStateFlow()

    private val _branch = MutableStateFlow("")
    val branch: StateFlow<String> = _branch.asStateFlow()

    private val _entries = MutableStateFlow<List<GitFileEntry>>(emptyList())
    val entries: StateFlow<List<GitFileEntry>> = _entries.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _progressText = MutableStateFlow<String?>(null)
    val progressText: StateFlow<String?> = _progressText.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var token: String? = null

    fun init(repoDir: File, token: String?) {
        _repoDir.value = repoDir
        this.token = token
        loadStatus()
    }

    fun loadStatus() {
        val dir = _repoDir.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val branchResult = withContext(Dispatchers.IO) { GitManager.getCurrentBranch(dir) }
            branchResult.fold(
                onSuccess = { _branch.value = it },
                onFailure = { _branch.value = "未知" }
            )
            val statusResult = withContext(Dispatchers.IO) { GitManager.getStatusSummary(dir) }
            statusResult.fold(
                onSuccess = { _entries.value = it },
                onFailure = { emit(it.message ?: "读取状态失败") }
            )
            _isLoading.value = false
        }
    }

    fun toggleStage(entry: GitFileEntry) {
        val dir = _repoDir.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val result = withContext(Dispatchers.IO) {
                if (entry.staged) {
                    GitManager.unstageFile(dir, entry.path)
                } else {
                    GitManager.stageFile(dir, entry.path)
                }
            }
            handleResult(result) { loadStatusImmediate(dir) }
            _isBusy.value = false
        }
    }

    fun stageAll() {
        val dir = _repoDir.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val result = withContext(Dispatchers.IO) { GitManager.stageAll(dir) }
            handleResult(result) { loadStatusImmediate(dir) }
            _isBusy.value = false
        }
    }

    fun commit(message: String) {
        val dir = _repoDir.value ?: return
        if (message.isBlank()) {
            emit("提交信息不能为空")
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            _progressText.value = "正在提交…"
            val result = withContext(Dispatchers.IO) { GitManager.commitIfStaged(dir, message) }
            _progressText.value = null
            handleResult(result) {
                emit("提交成功")
                loadStatusImmediate(dir)
            }
            _isBusy.value = false
        }
    }

    fun push() {
        val dir = _repoDir.value ?: return
        val tkn = token ?: run { emit("未登录，无法推送"); return }
        viewModelScope.launch {
            _isBusy.value = true
            _progressText.value = "正在推送…"
            val result = withContext(Dispatchers.IO) {
                GitManager.push(dir, tkn) { task, percent ->
                    _progressText.value = if (task.isNotEmpty()) "$task - $percent%" else "$percent%"
                }
            }
            _progressText.value = null
            handleResult(result) { emit(it) }
            _isBusy.value = false
        }
    }

    private fun loadStatusImmediate(dir: File) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { GitManager.getStatusSummary(dir) }
            r.onSuccess { _entries.value = it }
            val br = withContext(Dispatchers.IO) { GitManager.getCurrentBranch(dir) }
            br.onSuccess { _branch.value = it }
        }
    }

    private fun handleResult(result: Result<*>, onSuccess: (msg: String) -> Unit) {
        result.fold(
            onSuccess = { onSuccess(result.getOrNull()?.toString() ?: "操作成功") },
            onFailure = { emit(NetUtil.friendlyMessage(getApplication(), it)) }
        )
    }

    private fun emit(text: String) {
        _message.value = text
    }

    fun clearMessage() {
        _message.value = null
    }
}
