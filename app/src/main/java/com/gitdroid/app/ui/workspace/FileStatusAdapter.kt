package com.gitdroid.app.ui.workspace

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gitdroid.app.R
import com.gitdroid.app.git.GitFileCategory
import com.gitdroid.app.git.GitFileEntry
import com.gitdroid.app.databinding.ItemFileStatusBinding

class FileStatusAdapter(
    private val onToggleStage: (GitFileEntry) -> Unit
) : ListAdapter<GitFileEntry, FileStatusAdapter.FileStatusViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileStatusViewHolder {
        val binding = ItemFileStatusBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return FileStatusViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileStatusViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FileStatusViewHolder(
        private val binding: ItemFileStatusBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: GitFileEntry) {
            binding.tvFilePath.text = entry.path
            val statusText = buildString {
                append(entry.category.label)
                append(" · ")
                append(if (entry.staged) "已暂存" else "未暂存")
            }
            binding.tvFileStatus.text = statusText

            val (badgeText, badgeColor, statusColor) = stylingFor(entry)
            binding.tvBadge.text = badgeText
            binding.tvBadge.setBackgroundColor(badgeColor)
            binding.tvFileStatus.setTextColor(statusColor)

            binding.swStaged.isChecked = entry.staged
            binding.swStaged.setOnCheckedChangeListener(null)
            binding.swStaged.setOnCheckedChangeListener { _, _ ->
                onToggleStage(entry)
            }
            binding.root.setOnClickListener {
                binding.swStaged.toggle()
            }
        }

        private fun stylingFor(entry: GitFileEntry): Triple<String, Int, Int> {
            val ctx = binding.root.context
            val badgeColor = when (entry.category) {
                GitFileCategory.MODIFIED -> R.color.badge_modified
                GitFileCategory.ADDED -> R.color.badge_added
                GitFileCategory.DELETED -> R.color.badge_deleted
                GitFileCategory.UNTRACKED -> R.color.badge_untracked
                GitFileCategory.CONFLICTED -> R.color.badge_conflicted
            }
            val statusColorRes = if (entry.staged) R.color.staged_text else R.color.unstaged_text
            val badgeText = when (entry.category) {
                GitFileCategory.MODIFIED -> "M"
                GitFileCategory.ADDED -> "A"
                GitFileCategory.DELETED -> "D"
                GitFileCategory.UNTRACKED -> "?"
                GitFileCategory.CONFLICTED -> "!"
            }
            return Triple(badgeText, ctx.getColor(badgeColor), ctx.getColor(statusColorRes))
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<GitFileEntry>() {
        override fun areItemsTheSame(oldItem: GitFileEntry, newItem: GitFileEntry): Boolean =
            oldItem.path == newItem.path && oldItem.staged == newItem.staged

        override fun areContentsTheSame(oldItem: GitFileEntry, newItem: GitFileEntry): Boolean =
            oldItem == newItem
    }
}
