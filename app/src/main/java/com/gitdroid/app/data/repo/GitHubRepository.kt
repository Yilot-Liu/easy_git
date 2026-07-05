package com.gitdroid.app.data.repo

import com.gitdroid.app.data.api.GitHubAuthManager
import com.gitdroid.app.data.model.GitHubRepo
import com.gitdroid.app.data.model.GitHubUser

class GitHubRepository(private val authManager: GitHubAuthManager) {

    suspend fun login(code: String) = authManager.exchangeCodeForToken(code)

    suspend fun getCurrentUser(): Result<GitHubUser> = authManager.getCurrentUser()

    suspend fun getRepos(page: Int = 1): Result<List<GitHubRepo>> =
        authManager.getUserRepos(page)

    fun isLoggedIn(): Boolean = authManager.isLoggedIn()

    fun logout() = authManager.logout()

    fun getToken(): String? = authManager.getToken()
}
