package com.gitdroid.app.ui.repo

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gitdroid.app.data.model.GitHubRepo
import com.gitdroid.app.databinding.ItemRepoBinding

class RepoAdapter(
    private val onItemClick: (GitHubRepo) -> Unit
) : ListAdapter<GitHubRepo, RepoAdapter.RepoViewHolder>(RepoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RepoViewHolder {
        val binding = ItemRepoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RepoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RepoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RepoViewHolder(
        private val binding: ItemRepoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(repo: GitHubRepo) {
            binding.tvRepoName.text = repo.name
            binding.tvRepoDescription.text = repo.description ?: "暂无描述"
            binding.tvLanguage.text = repo.language ?: ""
            binding.tvStars.text = "★ ${repo.stargazersCount}"

            binding.root.setOnClickListener {
                onItemClick(repo)
            }
        }
    }

    class RepoDiffCallback : DiffUtil.ItemCallback<GitHubRepo>() {
        override fun areItemsTheSame(oldItem: GitHubRepo, newItem: GitHubRepo): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: GitHubRepo, newItem: GitHubRepo): Boolean {
            return oldItem == newItem
        }
    }
}
