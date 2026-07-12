package com.gitdroid.app.ui.repo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gitdroid.app.GitDroidApp
import com.gitdroid.app.data.api.NetUtil
import com.gitdroid.app.data.model.GitHubRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RepoListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GitDroidApp.instance.repository

    private val _repos = MutableStateFlow<List<GitHubRepo>>(emptyList())
    val repos: StateFlow<List<GitHubRepo>> = _repos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun loadRepos() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val result = repository.getRepos()
            result.fold(
                onSuccess = { repoList ->
                    _repos.value = repoList
                },
                onFailure = { e ->
                    _errorMessage.value = NetUtil.friendlyMessage(getApplication(), e)
                }
            )

            _isLoading.value = false
        }
    }
}
