package com.gitdroid.app.ui.login

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gitdroid.app.GitDroidApp
import com.gitdroid.app.R
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
                Toast.makeText(this, R.string.login_failed, Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE
                binding.btnLogin.isEnabled = true
            }
        }
    }

    private fun exchangeCodeForToken(code: String) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnLogin.isEnabled = false

            val result = authManager.exchangeCodeForToken(code)
            result.fold(
                onSuccess = {
                    Toast.makeText(this@LoginActivity, "登录成功", Toast.LENGTH_SHORT).show()
                    navigateToRepoList()
                },
                onFailure = { e ->
                    Toast.makeText(
                        this@LoginActivity,
                        "${getString(R.string.login_failed)}: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                }
            )
        }
    }

    private fun navigateToRepoList() {
        startActivity(Intent(this, RepoListActivity::class.java))
        finish()
    }
}
