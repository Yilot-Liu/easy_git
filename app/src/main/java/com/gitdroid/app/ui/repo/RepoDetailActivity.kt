package com.gitdroid.app.ui.repo

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.gitdroid.app.GitDroidApp
import com.gitdroid.app.R
import com.gitdroid.app.databinding.ActivityRepoDetailBinding
import com.gitdroid.app.data.api.NetUtil
import com.gitdroid.app.git.GitManager
import com.gitdroid.app.ui.common.GitProgressDialog
import com.gitdroid.app.ui.workspace.RepoWorkspaceActivity
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

    private var selectedParentDir: File? = null

    private lateinit var pickDirectoryLauncher: ActivityResultLauncher<Uri?>

    private lateinit var manageStorageLauncher: ActivityResultLauncher<Intent>

    private lateinit var writeStorageLauncher: ActivityResultLauncher<String>

    private var pendingWriteAction: (() -> Unit)? = null

    private var progressDialog: GitProgressDialog? = null

    private fun ensureOnline(): Boolean {
        if (!NetUtil.isOnline(this)) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.error_offline_title)
                .setMessage(R.string.error_offline_message)
                .setPositiveButton(R.string.retry) { _, _ -> }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return false
        }
        return true
    }

    private fun ensureWritableTarget(): Boolean {
        val dir = currentParentDir()
        if (isPublicMediaDir(dir)) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.path_public_dir_unsupported)
                .setMessage(R.string.path_public_dir_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return false
        }
        if (isUnderSharedStorage(dir) && !hasAllFilesAccess()) {
            promptEnableAllFilesAccess()
            return false
        }
        if (needsWriteStoragePermission() && !hasWriteStoragePermission()) {
            pendingWriteAction = null
            Toast.makeText(this, R.string.write_storage_required, Toast.LENGTH_LONG).show()
            writeStorageLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return false
        }
        val probe = probeWritable(dir)
        if (!probe.writable) {
            val detail = getString(
                R.string.target_not_writable,
                dir.absolutePath,
                getReadableAccessState(),
                probe.reason
            )
            binding.tvStatus.visibility = View.VISIBLE
            binding.tvStatus.setTextColor(getColor(R.color.error))
            binding.tvStatus.text = detail
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.target_not_writable_title)
                .setMessage(detail)
                .setPositiveButton(R.string.select_directory) { _, _ -> pickDirectoryLauncher.launch(null) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return false
        }
        return true
    }

    private data class ProbeResult(val writable: Boolean, val reason: String)

    private fun probeWritable(dir: File): ProbeResult {
        return try {
            if (!dir.exists() && !dir.mkdirs()) {
                return ProbeResult(false, "mkdirs 返回 false（可能无权限或路径受限）")
            }
            val test = File(dir, ".gitdroid_probe_${System.currentTimeMillis()}")
            if (!test.createNewFile()) {
                return ProbeResult(false, "无法在该目录创建文件")
            }
            test.delete()
            ProbeResult(true, "")
        } catch (e: Exception) {
            ProbeResult(false, e.javaClass.simpleName + ": " + (e.message ?: ""))
        }
    }

    private fun getReadableAccessState(): String {
        val flags = mutableListOf<String>()
        flags += "Android API: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE ?: "?"})"
        flags += if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) "所有文件访问权限: 已授权" else "所有文件访问权限: 未授权"
        } else {
            "所有文件访问权限: 不适用(API<30)"
        }
        return flags.joinToString("\n")
    }

    private fun showProgress(title: String) {
        if (progressDialog == null) {
            progressDialog = GitProgressDialog(this)
        }
        progressDialog?.apply {
            setTitle(title)
            setIndeterminate(true)
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
        binding = ActivityRepoDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pickDirectoryLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                val picked = resolveSafPick(uri)
                if (picked != null) {
                    if (isPublicMediaDir(picked)) {
                        Toast.makeText(this, R.string.path_public_dir_unsupported, Toast.LENGTH_LONG).show()
                        selectedParentDir = null
                        updateTargetPath()
                        return@registerForActivityResult
                    }
                    selectedParentDir = picked
                    val inSharedStorage = isUnderSharedStorage(picked)
                    if (inSharedStorage && !hasAllFilesAccess()) {
                        promptEnableAllFilesAccess()
                    } else {
                        Toast.makeText(
                            this,
                            getString(R.string.selected_path, picked.absolutePath),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(this, R.string.path_resolve_failed, Toast.LENGTH_LONG).show()
                }
                updateTargetPath()
            }
        }

        manageStorageLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) {
            if (hasAllFilesAccess()) {
                Toast.makeText(this, R.string.storage_permission_granted, Toast.LENGTH_SHORT).show()
                updateTargetPath()
            } else {
                Toast.makeText(this, R.string.storage_permission_denied, Toast.LENGTH_LONG).show()
                selectedParentDir = null
                updateTargetPath()
            }
        }

        writeStorageLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                Toast.makeText(this, R.string.storage_permission_granted, Toast.LENGTH_SHORT).show()
                pendingWriteAction?.invoke()
            } else {
                Toast.makeText(this, R.string.write_storage_denied, Toast.LENGTH_LONG).show()
            }
            pendingWriteAction = null
        }

        extractIntentExtras()
        setupToolbar()
        populateRepoInfo()
        setupButtons()
    }

    private fun resolveSafPick(uri: Uri): File? {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        return pathFromSafUri(uri)
    }

    private fun pathFromSafUri(uri: Uri): File? {
        val docId = try {
            android.provider.DocumentsContract.getTreeDocumentId(uri)
        } catch (e: Exception) {
            return null
        }
        if (!docId.startsWith("primary:")) {
            val parts = docId.split(":")
            if (parts.size == 2) {
                val storage = Environment.getExternalStorageDirectory().absolutePath
                val rel = parts[1]
                return if (rel.isBlank()) File(storage) else File(storage, rel)
            }
            return null
        }
        val rel = docId.substringAfter("primary:", "")
        val external = Environment.getExternalStorageDirectory().absolutePath
        val path = if (rel.isBlank()) external else "$external/$rel"
        return File(path)
    }

    private fun isUnderSharedStorage(file: File): Boolean {
        val external = Environment.getExternalStorageDirectory().absolutePath
        return file.absolutePath.startsWith(external)
    }

    private fun isPublicMediaDir(file: File): Boolean {
        val external = Environment.getExternalStorageDirectory().absolutePath
        val rel = file.absolutePath.removePrefix(external).removePrefix("/")
        val top = rel.substringBefore("/").lowercase()
        return top in setOf(
            "download", "documents", "pictures", "dcim",
            "movies", "music", "audiobooks", "recordings"
        )
    }

    private fun hasAllFilesAccess(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }
        return true
    }

    private fun needsWriteStoragePermission(): Boolean {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && isUnderSharedStorage(currentParentDir())
    }

    private fun hasWriteStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun promptEnableAllFilesAccess() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.storage_permission_title)
            .setMessage(R.string.storage_permission_message)
            .setPositiveButton(R.string.grant) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        .setData(Uri.parse("package:$packageName"))
                    manageStorageLauncher.launch(intent)
                } catch (_: Exception) {
                    try {
                        manageStorageLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    } catch (_: Exception) {
                        Toast.makeText(this, R.string.storage_open_settings_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                selectedParentDir = null
                updateTargetPath()
            }
            .setCancelable(false)
            .show()
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
            try {
                pickDirectoryLauncher.launch(null)
            } catch (e: Exception) {
                Toast.makeText(this, "无法启动目录选择器: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnClone.setOnClickListener {
            performClone()
        }

        binding.btnPull.setOnClickListener {
            performPull()
        }

        binding.btnWorkspace.setOnClickListener {
            openWorkspace()
        }
    }

    private fun openWorkspace() {
        val intent = Intent(this, RepoWorkspaceActivity::class.java).apply {
            putExtra(RepoWorkspaceActivity.EXTRA_REPO_NAME, repoName)
            putExtra(RepoWorkspaceActivity.EXTRA_PARENT_DIR, currentParentDir().absolutePath)
            putExtra(RepoWorkspaceActivity.EXTRA_REPO_SEGMENT, repoName.substringAfter("/"))
        }
        startActivity(intent)
    }

    private fun currentParentDir(): File =
        selectedParentDir ?: File(getExternalFilesDir(null) ?: filesDir, "GitDroid")

    private fun currentRepoDir(): File =
        File(currentParentDir(), repoName.substringAfter("/"))

    private fun updateTargetPath() {
        binding.tvTargetPath.text = currentRepoDir().absolutePath
    }

    private fun performClone() {
        val token = GitDroidApp.instance.authManager.getToken()
        if (token == null) {
            Toast.makeText(this, "未登录，请先登录", Toast.LENGTH_SHORT).show()
            return
        }

        val repoDir = currentRepoDir()

        if (repoDir.exists() && (repoDir.listFiles()?.isNotEmpty() == true)) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("目录已存在")
                .setMessage("目标目录已存在且不为空：\n${repoDir.absolutePath}\n请选择操作")
                .setPositiveButton("覆盖克隆") { _, _ ->
                    executeClone(force = true)
                }
                .setNeutralButton("拉取更新") { _, _ ->
                    performPull()
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }

        executeClone(force = false)
    }

    private fun executeClone(force: Boolean) {
        if (!ensureOnline()) return
        if (!ensureWritableTarget()) return
        val token = GitDroidApp.instance.authManager.getToken() ?: return
        val repoDir = currentRepoDir()

        binding.btnClone.isEnabled = false
        binding.btnPull.isEnabled = false
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = getString(R.string.cloning)
        showProgress(getString(R.string.cloning))

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                GitManager.cloneRepository(
                    cloneUrl = cloneUrl,
                    targetDir = repoDir,
                    token = token,
                    branch = branch,
                    onProgress = { task, percent ->
                        runOnUiThread {
                            progressDialog?.setIndeterminate(false)
                            progressDialog?.update(task, percent)
                            binding.tvStatus.text = if (task.isNotEmpty()) {
                                "$task - $percent%"
                            } else {
                                "$percent%"
                            }
                        }
                    },
                    force = force
                )
            }

            dismissProgress()
            binding.btnClone.isEnabled = true
            binding.btnPull.isEnabled = true

            result.fold(
                onSuccess = {
                    binding.tvStatus.text = getString(R.string.clone_success)
                    binding.tvStatus.setTextColor(getColor(R.color.success))
                    Toast.makeText(this@RepoDetailActivity, R.string.clone_success, Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    val msg = NetUtil.friendlyMessage(this@RepoDetailActivity, e)
                    binding.tvStatus.text = "${getString(R.string.clone_failed)}: $msg"
                    binding.tvStatus.setTextColor(getColor(R.color.error))
                    Toast.makeText(this@RepoDetailActivity, msg, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun performPull() {
        if (!ensureOnline()) return
        if (!ensureWritableTarget()) return
        val token = GitDroidApp.instance.authManager.getToken()
        if (token == null) {
            Toast.makeText(this, "未登录，请先登录", Toast.LENGTH_SHORT).show()
            return
        }

        val repoDir = currentRepoDir()

        if (!GitManager.isGitRepo(repoDir)) {
            Toast.makeText(this, "仓库尚未克隆，请先克隆", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnClone.isEnabled = false
        binding.btnPull.isEnabled = false
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = getString(R.string.pulling)
        showProgress(getString(R.string.pulling))

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                GitManager.pullRepository(
                    repoDir = repoDir,
                    token = token,
                    onProgress = { task, percent ->
                        runOnUiThread {
                            progressDialog?.setIndeterminate(false)
                            progressDialog?.update(task, percent)
                            binding.tvStatus.text = if (task.isNotEmpty()) {
                                "$task - $percent%"
                            } else {
                                "$percent%"
                            }
                        }
                    }
                )
            }

            dismissProgress()
            binding.btnClone.isEnabled = true
            binding.btnPull.isEnabled = true

            result.fold(
                onSuccess = { status ->
                    binding.tvStatus.text = "${getString(R.string.pull_success)}: $status"
                    binding.tvStatus.setTextColor(getColor(R.color.success))
                    Toast.makeText(this@RepoDetailActivity, status, Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    val msg = NetUtil.friendlyMessage(this@RepoDetailActivity, e)
                    binding.tvStatus.text = "${getString(R.string.pull_failed)}: $msg"
                    binding.tvStatus.setTextColor(getColor(R.color.error))
                    Toast.makeText(this@RepoDetailActivity, msg, Toast.LENGTH_LONG).show()
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
