package com.gitdroid.app.data.api

import com.gitdroid.app.data.model.GitHubRepo
import com.gitdroid.app.data.model.GitHubToken
import com.gitdroid.app.data.model.GitHubUser
import retrofit2.Response
import retrofit2.http.*

interface GitHubApiService {

    @POST("https://github.com/login/oauth/access_token")
    @FormUrlEncoded
    suspend fun getAccessToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String
    ): Response<GitHubToken>

    @GET("user")
    suspend fun getAuthenticatedUser(
        @Header("Authorization") token: String
    ): Response<GitHubUser>

    @GET("user/repos")
    suspend fun getUserRepos(
        @Header("Authorization") token: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
        @Query("sort") sort: String = "updated",
        @Query("type") type: String = "all"
    ): Response<List<GitHubRepo>>

    @GET("users/{username}/repos")
    suspend fun getUserPublicRepos(
        @Path("username") username: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30
    ): Response<List<GitHubRepo>>
}
