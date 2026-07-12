package com.gitdroid.app.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.Status
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

enum class GitFileCategory(val label: String) {
    MODIFIED("修改"),
    ADDED("新增"),
    DELETED("删除"),
    UNTRACKED("未跟踪"),
    CONFLICTED("冲突")
}

data class GitFileEntry(
    val path: String,
    val category: GitFileCategory,
    val staged: Boolean
) {
    val isFullyTrackedAndClean: Boolean get() = false
}

object GitManager {

    fun cloneRepository(
        cloneUrl: String,
        targetDir: File,
        token: String,
        branch: String = "main",
        onProgress: (String, Int) -> Unit = { _, _ -> },
        force: Boolean = false
    ): Result<File> {
        return try {
            if (force && targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            if (targetDir.exists() && (targetDir.listFiles()?.isNotEmpty() == true)) {
                return Result.failure(Exception("目标目录已存在且非空: ${targetDir.absolutePath}"))
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

    private fun openOrError(repoDir: File): Result<Git> {
        return try {
            if (!isGitRepo(repoDir)) {
                return Result.failure(Exception("不是有效的 Git 仓库: ${repoDir.absolutePath}"))
            }
            Result.success(Git.open(repoDir))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getStatusSummary(repoDir: File): Result<List<GitFileEntry>> {
        return try {
            val git = Git.open(repoDir)
            try {
                val status: Status = git.status().call()
                Result.success(buildFileEntries(status))
            } finally {
                git.close()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildFileEntries(status: Status): List<GitFileEntry> {
        val entries = mutableListOf<GitFileEntry>()

        fun add(paths: Collection<String>, category: GitFileCategory, staged: Boolean) {
            paths.forEach { entries.add(GitFileEntry(it, category, staged)) }
        }

        // 已暂存区（index 与 HEAD 的差异）
        add(status.added, GitFileCategory.ADDED, true)
        add(status.changed, GitFileCategory.MODIFIED, true)
        add(status.removed, GitFileCategory.DELETED, true)

        // 工作区（working tree 与 index 的差异）
        add(status.modified, GitFileCategory.MODIFIED, false)
        add(status.untracked, GitFileCategory.UNTRACKED, false)
        add(status.missing, GitFileCategory.DELETED, false)
        add(status.conflicting, GitFileCategory.CONFLICTED, false)

        // 去重：同一 path+staged+category 保留一条；冲突优先
        return entries
            .distinctBy { it.path + it.staged + it.category.name }
            .sortedWith(compareBy({ !it.staged }, { it.path }))
    }

    fun stageFile(repoDir: File, path: String): Result<Unit> {
        return try {
            val git = Git.open(repoDir)
            try {
                git.add().addFilepattern(path).call()
                Result.success(Unit)
            } finally {
                git.close()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun stageAll(repoDir: File): Result<Unit> {
        return try {
            val git = Git.open(repoDir)
            try {
                git.add().addFilepattern(".").call()
                Result.success(Unit)
            } finally {
                git.close()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun unstageFile(repoDir: File, path: String): Result<Unit> {
        return try {
            val git = Git.open(repoDir)
            try {
                git.reset()
                    .setRef("HEAD")
                    .setMode(ResetCommand.ResetType.MIXED)
                    .addPath(path)
                    .call()
                Result.success(Unit)
            } finally {
                git.close()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun commit(
        repoDir: File,
        message: String,
        authorName: String? = null,
        authorEmail: String? = null
    ): Result<String> {
        return try {
            val git = Git.open(repoDir)
            try {
                val cmd = git.commit().setMessage(message)
                if (!authorName.isNullOrBlank()) cmd.setAuthor(authorName, authorEmail ?: "")
                val rev = cmd.call()
                Result.success(rev.id.name)
            } finally {
                git.close()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun hasStagedChanges(repoDir: File): Boolean {
        val status = getStatusSummary(repoDir).getOrNull() ?: return false
        return status.any { it.staged }
    }

    fun commitIfStaged(repoDir: File, message: String): Result<String> {
        if (!hasStagedChanges(repoDir)) {
            return Result.failure(Exception("没有已暂存的更改，请先暂存后再提交"))
        }
        return commit(repoDir, message)
    }

    fun push(
        repoDir: File,
        token: String,
        onProgress: (String, Int) -> Unit = { _, _ -> }
    ): Result<String> {
        return try {
            val git = Git.open(repoDir)
            try {
                val credentialsProvider = UsernamePasswordCredentialsProvider(token, "")
                val pushResult = git.push()
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
                    })
                    .call()

                val messages = pushResult.mapNotNull { it.messages }
                val summary = if (messages.isEmpty()) "推送完成" else messages.joinToString("\n")
                Result.success(summary)
            } finally {
                git.close()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCurrentBranch(repoDir: File): Result<String> {
        return try {
            val git = Git.open(repoDir)
            try {
                val ref = git.repository.findRef("HEAD")
                val name = ref?.target?.name?.substringAfterLast("refs/heads/") ?: "HEAD"
                Result.success(name)
            } finally {
                git.close()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getBranches(repoDir: File): Result<List<String>> {
        return try {
            val git = Git.open(repoDir)
            try {
                val current = git.repository.findRef("HEAD")?.target?.name
                    ?.substringAfterLast("refs/heads/")
                val local = git.branchList().call().map { it.name.substringAfterLast("refs/heads/") }
                val sorted = local.sortedBy { it != current }
                Result.success(sorted)
            } finally {
                git.close()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun checkoutBranch(repoDir: File, branchName: String): Result<Unit> {
        return try {
            val git = Git.open(repoDir)
            try {
                git.checkout().setName(branchName).call()
                Result.success(Unit)
            } finally {
                git.close()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
