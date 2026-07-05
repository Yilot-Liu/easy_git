package com.gitdroid.app.ui.repo

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gitdroid.app.GitDroidApp
import com.gitdroid.app.R
import com.gitdroid.app.data.model.GitHubRepo
import com.gitdroid.app.databinding.ActivityRepoListBinding
import com.gitdroid.app.ui.login.LoginActivity
import kotlinx.coroutines.launch

class RepoListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRepoListBinding
    private lateinit var viewModel: RepoListViewModel
    private lateinit var adapter: RepoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRepoListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[RepoListViewModel::class.java]

        setupRecyclerView()
        setupSwipeRefresh()
        setupFab()
        observeViewModel()

        viewModel.loadRepos()
    }

    private fun setupRecyclerView() {
        adapter = RepoAdapter { repo ->
            val intent = Intent(this, RepoDetailActivity::class.java).apply {
                putExtra(RepoDetailActivity.EXTRA_REPO_NAME, repo.fullName)
                putExtra(RepoDetailActivity.EXTRA_REPO_CLONE_URL, repo.cloneUrl)
                putExtra(RepoDetailActivity.EXTRA_REPO_DESCRIPTION, repo.description)
                putExtra(RepoDetailActivity.EXTRA_REPO_LANGUAGE, repo.language)
                putExtra(RepoDetailActivity.EXTRA_REPO_STARS, repo.stargazersCount)
                putExtra(RepoDetailActivity.EXTRA_REPO_BRANCH, repo.defaultBranch)
            }
            startActivity(intent)
        }

        binding.rvRepos.layoutManager = LinearLayoutManager(this)
        binding.rvRepos.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadRepos()
        }
    }

    private fun setupFab() {
        binding.fabLogout.setOnClickListener {
            GitDroidApp.instance.repository.logout()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.repos.collect { repos ->
                adapter.submitList(repos)
                binding.tvEmpty.visibility = if (repos.isEmpty() && !viewModel.isLoading.value) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }

        lifecycleScope.launch {
            viewModel.errorMessage.collect { message ->
                message?.let {
                    Toast.makeText(this@RepoListActivity, it, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
