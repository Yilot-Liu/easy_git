package com.gitdroid.app

import android.app.Application
import com.gitdroid.app.data.api.GitHubAuthManager
import com.gitdroid.app.data.repo.GitHubRepository

class GitDroidApp : Application() {

    lateinit var authManager: GitHubAuthManager
        private set

    lateinit var repository: GitHubRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        authManager = GitHubAuthManager(this)
        repository = GitHubRepository(authManager)
    }

    companion object {
        lateinit var instance: GitDroidApp
            private set
    }
}
