package com.gitdroid.app.data.api

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.gitdroid.app.BuildConfig
import com.gitdroid.app.data.model.GitHubToken
import com.gitdroid.app.data.model.GitHubUser
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class GitHubAuthManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val apiService: GitHubApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()
            .create(GitHubApiService::class.java)
    }

    fun getAuthUrl(): String {
        return "https://github.com/login/oauth/authorize" +
                "?client_id=${BuildConfig.GITHUB_CLIENT_ID}" +
                "&redirect_uri=${Uri.encode(BuildConfig.GITHUB_REDIRECT_URI)}" +
                "&scope=repo" +
                "&state=${System.currentTimeMillis()}"
    }

    fun launchOAuth(context: Context) {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        customTabsIntent.launchUrl(context, Uri.parse(getAuthUrl()))
    }

    suspend fun exchangeCodeForToken(code: String): Result<GitHubToken> {
        return try {
            val response = apiService.getAccessToken(
                clientId = BuildConfig.GITHUB_CLIENT_ID,
                clientSecret = BuildConfig.GITHUB_CLIENT_SECRET,
                code = code,
                redirectUri = BuildConfig.GITHUB_REDIRECT_URI
            )
            if (response.isSuccessful && response.body() != null) {
                val token = response.body()!!
                saveToken(token.accessToken)
                Result.success(token)
            } else {
                Result.failure(Exception("Failed to get access token: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCurrentUser(): Result<GitHubUser> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("Not authenticated"))
            val response = apiService.getAuthenticatedUser("token $token")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get user: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserRepos(page: Int = 1): Result<List<com.gitdroid.app.data.model.GitHubRepo>> {
        return try {
            val token = getToken() ?: return Result.failure(Exception("Not authenticated"))
            val response = apiService.getUserRepos("token $token", page = page)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get repos: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return prefs.getString(KEY_ACCESS_TOKEN, null)
    }

    fun isLoggedIn(): Boolean {
        return getToken() != null
    }

    fun logout() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "gitdroid_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
    }
}
