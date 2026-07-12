package com.gitdroid.app.ui.workspace

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gitdroid.app.GitDroidApp
import com.gitdroid.app.R
import com.gitdroid.app.databinding.ActivityRepoWorkspaceBinding
import com.gitdroid.app.data.api.NetUtil
import com.gitdroid.app.git.GitManager
import com.gitdroid.app.ui.common.GitProgressDialog
import kotlinx.coroutines.launch
import java.io.File

class RepoWorkspaceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRepoWorkspaceBinding
    private lateinit var viewModel: RepoWorkspaceViewModel
    private lateinit var adapter: FileStatusAdapter

    private lateinit var repoDir: File

    private var progressDialog: GitProgressDialog? = null

    private fun showProgress(title: String, indeterminate: Boolean = true) {
        if (progressDialog == null) progressDialog = GitProgressDialog(this)
        progressDialog?.apply {
            setTitle(title)
            setIndeterminate(indeterminate)
            if (!isShowing) show()
        }
    }

    private fun dismissProgress() {
        progressDialog?.takeIf { it.isShowing }?.dismiss()
    }

    override fun onDestroy() {
        dismissProgress()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRepoWorkspaceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val parentPath = intent.getStringExtra(EXTRA_PARENT_DIR)
        val repoPathSegment = intent.getStringExtra(EXTRA_REPO_SEGMENT) ?: ""
        val baseDir = if (parentPath != null) File(parentPath) else {
            File(getExternalFilesDir(null) ?: filesDir, "GitDroid")
        }
        repoDir = File(baseDir, repoPathSegment.substringAfter("/"))

        setupToolbar()
        setupRecyclerView()
        setupButtons()
        observeViewModel()

        if (!GitManager.isGitRepo(repoDir)) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.tvEmpty.text = "该仓库尚未克隆到本地，请先克隆"
            binding.btnStageAll.isEnabled = false
            binding.btnCommit.isEnabled = false
            binding.btnPush.isEnabled = false
            return
        }

        viewModel = ViewModelProvider(this)[RepoWorkspaceViewModel::class.java]
        viewModel.init(repoDir, GitDroidApp.instance.authManager.getToken())
    }

    private fun setupToolbar() {
        binding.toolbar.title = intent.getStringExtra(EXTRA_REPO_NAME) ?: "工作区"
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = FileStatusAdapter { entry ->
            if (::viewModel.isInitialized) viewModel.toggleStage(entry)
        }
        binding.rvFiles.layoutManager = LinearLayoutManager(this)
        binding.rvFiles.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnRefresh.setOnClickListener {
            if (::viewModel.isInitialized) viewModel.loadStatus()
        }
        binding.btnStageAll.setOnClickListener {
            if (::viewModel.isInitialized) viewModel.stageAll()
        }
        binding.btnCommit.setOnClickListener {
            if (::viewModel.isInitialized) showCommitDialog()
        }
        binding.btnPush.setOnClickListener {
            if (::viewModel.isInitialized) {
                if (!NetUtil.isOnline(this)) {
                    showOfflineDialog()
                    return@setOnClickListener
                }
                AlertDialog.Builder(this)
                    .setTitle(R.string.push)
                    .setMessage(R.string.push_confirm)
                    .setPositiveButton(R.string.push) { _, _ ->
                        showProgress(getString(R.string.push))
                        viewModel.push()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun showOfflineDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.error_offline_title)
            .setMessage(R.string.error_offline_message)
            .setPositiveButton(R.string.retry, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showCommitDialog() {
        val edit = EditText(this).apply {
            hint = getString(R.string.commit_message_hint)
            minLines = 2
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.commit)
            .setView(edit)
            .setPositiveButton(R.string.commit) { _, _ ->
                viewModel.commit(edit.text.toString())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun observeViewModel() {
        if (!::viewModel.isInitialized) return

        lifecycleScope.launch {
            viewModel.branch.collect { binding.tvBranch.text = it }
        }
        lifecycleScope.launch {
            viewModel.entries.collect { list ->
                adapter.submitList(list)
                binding.tvEmpty.visibility =
                    if (list.isEmpty() && !viewModel.isLoading.value) View.VISIBLE else View.GONE
            }
        }
        lifecycleScope.launch {
            viewModel.isLoading.collect { binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE }
        }
        lifecycleScope.launch {
            viewModel.isBusy.collect { busy ->
                val enabled = !busy
                binding.btnStageAll.isEnabled = enabled
                binding.btnCommit.isEnabled = enabled
                binding.btnPush.isEnabled = enabled
                binding.btnRefresh.isEnabled = enabled
            }
        }
        lifecycleScope.launch {
            viewModel.progressText.collect { text ->
                binding.tvProgressText.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
                binding.tvProgressText.text = text
                if (text == null) {
                    dismissProgress()
                } else if (text == "正在推送…") {
                    // handled by dialog creation; switch to determinate on first numeric update
                } else if (text.endsWith("%")) {
                    val percent = text.substringAfterLast("- ").substringBefore("%").trim().toIntOrNull()
                        ?: text.substringBefore("%").trim().toIntOrNull() ?: 0
                    val task = if (text.contains("- ")) text.substringBefore(" - ") else ""
                    progressDialog?.setIndeterminate(false)
                    progressDialog?.update(task, percent)
                }
            }
        }
        lifecycleScope.launch {
            viewModel.message.collect { msg ->
                if (!msg.isNullOrEmpty()) {
                    dismissProgress()
                    com.google.android.material.snackbar.Snackbar.make(
                        binding.root, msg, com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).show()
                    viewModel.clearMessage()
                }
            }
        }
    }

    companion object {
        const val EXTRA_REPO_NAME = "extra_repo_name"
        const val EXTRA_PARENT_DIR = "extra_parent_dir"
        const val EXTRA_REPO_SEGMENT = "extra_repo_segment"
    }
}
