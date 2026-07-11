package com.gitdroid.app.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

object GitManager {

    fun cloneRepository(
        cloneUrl: String,
        targetDir: File,
        token: String,
        branch: String = "main",
        onProgress: (String, Int) -> Unit = { _, _ -> }
    ): Result<File> {
        return try {
            if (targetDir.exists()) {
                return Result.failure(Exception("目标目录已存在: ${targetDir.absolutePath}"))
            }

            val credentialsProvider = UsernamePasswordCredentialsProvider(token, "")

            val cloneCommand = Git.cloneRepository()
                .setURI(cloneUrl)
                .setDirectory(targetDir)
                .setBranch(branch)
                .setCredentialsProvider(credentialsProvider)
                .setProgressMonitor(object : org.eclipse.jgit.lib.ProgressMonitor {
                    private var totalWork = 0
                    private var completed = 0

                    override fun start(totalTasks: Int) {}

                    override fun beginTask(title: String, totalWork: Int) {
                        this.totalWork = totalWork
                        this.completed = 0
                        onProgress(title, 0)
                    }

                    override fun update(completed: Int) {
                        this.completed += completed
                        if (totalWork > 0) {
                            val percent = (this.completed * 100) / totalWork
                            onProgress("", percent)
                        }
                    }

                    override fun endTask() {}

                    override fun isCancelled(): Boolean = false

                    override fun showDuration(enabled: Boolean) {}
                })

            val git = cloneCommand.call()
            git.close()

            Result.success(targetDir)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun pullRepository(
        repoDir: File,
        token: String,
        onProgress: (String, Int) -> Unit = { _, _ -> }
    ): Result<String> {
        return try {
            if (!File(repoDir, ".git").exists()) {
                return Result.failure(Exception("不是有效的 Git 仓库: ${repoDir.absolutePath}"))
            }

            val git = Git.open(repoDir)
            val credentialsProvider = UsernamePasswordCredentialsProvider(token, "")

            val pullResult = git.pull()
                .setCredentialsProvider(credentialsProvider)
                .call()

            git.close()

            if (pullResult.isSuccessful) {
                val mergeResult = pullResult.mergeResult
                val status = when (mergeResult.mergeStatus) {
                    org.eclipse.jgit.api.MergeResult.MergeStatus.ALREADY_UP_TO_DATE -> "已是最新"
                    org.eclipse.jgit.api.MergeResult.MergeStatus.FAST_FORWARD -> "快进合并成功"
                    org.eclipse.jgit.api.MergeResult.MergeStatus.MERGED -> "合并成功"
                    else -> "拉取完成"
                }
                Result.success(status)
            } else {
                Result.failure(Exception("拉取失败: ${pullResult.mergeResult.mergeStatus}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isGitRepo(dir: File): Boolean {
        return File(dir, ".git").exists()
    }

    fun getRepoDefaultDir(repoName: String, baseDir: File): File {
        return File(baseDir, repoName)
    }
}
