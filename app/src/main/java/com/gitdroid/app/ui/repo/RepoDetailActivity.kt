package com.gitdroid.app.ui.repo

import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gitdroid.app.GitDroidApp
import com.gitdroid.app.R
import com.gitdroid.app.databinding.ActivityRepoDetailBinding
import com.gitdroid.app.git.GitManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RepoDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRepoDetailBinding

    private lateinit var repoName: String
    private lateinit var cloneUrl: String
    private var description: String? = null
    private var language: String? = null
    private var stars: Int = 0
    private var branch: String = "main"
    private var targetPath: File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "GitDroid"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRepoDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        extractIntentExtras()
        setupToolbar()
        populateRepoInfo()
        setupButtons()
    }

    private fun extractIntentExtras() {
        repoName = intent.getStringExtra(EXTRA_REPO_NAME) ?: ""
        cloneUrl = intent.getStringExtra(EXTRA_REPO_CLONE_URL) ?: ""
        description = intent.getStringExtra(EXTRA_REPO_DESCRIPTION)
        language = intent.getStringExtra(EXTRA_REPO_LANGUAGE)
        stars = intent.getIntExtra(EXTRA_REPO_STARS, 0)
        branch = intent.getStringExtra(EXTRA_REPO_BRANCH) ?: "main"
    }

    private fun setupToolbar() {
        binding.toolbar.title = repoName
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun populateRepoInfo() {
        binding.tvRepoName.text = repoName
        binding.tvRepoDescription.text = description ?: "暂无描述"
        binding.tvRepoLanguage.text = language ?: ""
        binding.tvRepoStars.text = "★ $stars"
        updateTargetPath()
    }

    private fun setupButtons() {
        binding.btnSelectPath.setOnClickListener {
            targetPath = File(targetPath, repoName.substringAfter("/"))
            updateTargetPath()
        }

        binding.btnClone.setOnClickListener {
            performClone()
        }

        binding.btnPull.setOnClickListener {
            performPull()
        }
    }

    private fun updateTargetPath() {
        val repoDir = File(targetPath, repoName.substringAfter("/"))
        binding.tvTargetPath.text = repoDir.absolutePath
    }

    private fun performClone() {
        val token = GitDroidApp.instance.authManager.getToken()
        if (token == null) {
            Toast.makeText(this, "未登录，请先登录", Toast.LENGTH_SHORT).show()
            return
        }

        val repoDir = File(targetPath, repoName.substringAfter("/"))

        binding.progressBar.visibility = View.VISIBLE
        binding.btnClone.isEnabled = false
        binding.btnPull.isEnabled = false
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = getString(R.string.cloning)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                GitManager.cloneRepository(
                    cloneUrl = cloneUrl,
                    targetDir = repoDir,
                    token = token,
                    branch = branch,
                    onProgress = { task, percent ->
                        withContext(Dispatchers.Main) {
                            binding.tvStatus.text = if (task.isNotEmpty()) {
                                "$task - $percent%"
                            } else {
                                "$percent%"
                            }
                        }
                    }
                )
            }

            binding.progressBar.visibility = View.GONE
            binding.btnClone.isEnabled = true
            binding.btnPull.isEnabled = true

            result.fold(
                onSuccess = {
                    binding.tvStatus.text = getString(R.string.clone_success)
                    Toast.makeText(this@RepoDetailActivity, R.string.clone_success, Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    binding.tvStatus.text = "${getString(R.string.clone_failed)}: ${e.message}"
                    Toast.makeText(
                        this@RepoDetailActivity,
                        "${getString(R.string.clone_failed)}: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    private fun performPull() {
        val token = GitDroidApp.instance.authManager.getToken()
        if (token == null) {
            Toast.makeText(this, "未登录，请先登录", Toast.LENGTH_SHORT).show()
            return
        }

        val repoDir = File(targetPath, repoName.substringAfter("/"))

        if (!GitManager.isGitRepo(repoDir)) {
            Toast.makeText(this, "仓库尚未克隆，请先克隆", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnClone.isEnabled = false
        binding.btnPull.isEnabled = false
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = getString(R.string.pulling)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                GitManager.pullRepository(
                    repoDir = repoDir,
                    token = token,
                    onProgress = { task, percent ->
                        withContext(Dispatchers.Main) {
                            binding.tvStatus.text = if (task.isNotEmpty()) {
                                "$task - $percent%"
                            } else {
                                "$percent%"
                            }
                        }
                    }
                )
            }

            binding.progressBar.visibility = View.GONE
            binding.btnClone.isEnabled = true
            binding.btnPull.isEnabled = true

            result.fold(
                onSuccess = { status ->
                    binding.tvStatus.text = "${getString(R.string.pull_success)}: $status"
                    Toast.makeText(this@RepoDetailActivity, status, Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    binding.tvStatus.text = "${getString(R.string.pull_failed)}: ${e.message}"
                    Toast.makeText(
                        this@RepoDetailActivity,
                        "${getString(R.string.pull_failed)}: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    companion object {
        const val EXTRA_REPO_NAME = "extra_repo_name"
        const val EXTRA_REPO_CLONE_URL = "extra_repo_clone_url"
        const val EXTRA_REPO_DESCRIPTION = "extra_repo_description"
        const val EXTRA_REPO_LANGUAGE = "extra_repo_language"
        const val EXTRA_REPO_STARS = "extra_repo_stars"
        const val EXTRA_REPO_BRANCH = "extra_repo_branch"
    }
}
