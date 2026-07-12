package com.gitdroid.app.ui.login

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.gitdroid.app.GitDroidApp
import com.gitdroid.app.R
import com.gitdroid.app.data.api.NetUtil
import com.gitdroid.app.databinding.ActivityLoginBinding
import com.gitdroid.app.ui.repo.RepoListActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val authManager by lazy { GitDroidApp.instance.authManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (authManager.isLoggedIn()) {
            navigateToRepoList()
            return
        }

        setupViews()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun setupViews() {
        binding.btnLogin.setOnClickListener {
            if (!NetUtil.isOnline(this)) {
                Snackbar.make(binding.root, R.string.error_no_network, Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            binding.progressBar.visibility = View.VISIBLE
            binding.btnLogin.isEnabled = false
            authManager.launchOAuth(this)
        }
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "easygit" && uri.host == "oauth") {
            val code = uri.getQueryParameter("code")
            if (code != null) {
                exchangeCodeForToken(code)
            } else {
                showError(getString(R.string.login_failed))
            }
        }
    }

    private fun showError(msg: String) {
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
        binding.progressBar.visibility = View.GONE
        binding.btnLogin.isEnabled = true
    }

    private fun exchangeCodeForToken(code: String) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnLogin.isEnabled = false

            val result = authManager.exchangeCodeForToken(code)
            result.fold(
                onSuccess = {
                    Snackbar.make(binding.root, "登录成功", Snackbar.LENGTH_SHORT).show()
                    navigateToRepoList()
                },
                onFailure = { e ->
                    showError(NetUtil.friendlyMessage(this@LoginActivity, e))
                }
            )
        }
    }

    private fun navigateToRepoList() {
        startActivity(Intent(this, RepoListActivity::class.java))
        finish()
    }
}
