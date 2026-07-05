package com.gitdroid.app.data.model

import com.google.gson.annotations.SerializedName

data class GitHubRepo(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("description") val description: String?,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("clone_url") val cloneUrl: String,
    @SerializedName("ssh_url") val sshUrl: String,
    @SerializedName("language") val language: String?,
    @SerializedName("stargazers_count") val stargazersCount: Int,
    @SerializedName("forks_count") val forksCount: Int,
    @SerializedName("default_branch") val defaultBranch: String,
    @SerializedName("private") val isPrivate: Boolean,
    @SerializedName("owner") val owner: GitHubUser
)

data class GitHubUser(
    @SerializedName("login") val login: String,
    @SerializedName("avatar_url") val avatarUrl: String,
    @SerializedName("id") val id: Long
)

data class GitHubToken(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("scope") val scope: String?
)
