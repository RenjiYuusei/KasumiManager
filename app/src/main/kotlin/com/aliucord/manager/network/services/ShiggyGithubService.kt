package com.aliucord.manager.network.services

import com.aliucord.manager.network.models.GithubRelease
import com.aliucord.manager.network.utils.ApiResponse
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders

class ShiggyGithubService(
        private val http: HttpService,
) {
    /** Fetches all the Manager releases with a 60s local cache. */
    suspend fun getManagerReleases(): ApiResponse<List<GithubRelease>> {
        return http.request {
            url("https://api.github.com/repos/$ORG/$MANAGER_REPO/releases")
            header(HttpHeaders.CacheControl, "public, max-age=60, s-maxage=60")
        }
    }

    /** Fetches the latest Xposed release with a 60s local cache, with fallback to upstream ShiggyXposed if repo has no releases yet. */
    suspend fun getLatestXposedRelease(): ApiResponse<GithubRelease> {
        val primary = http.request<GithubRelease> {
            url("https://api.github.com/repos/$XPOSED_ORG/$XPOSED_REPO/releases/latest")
            header(HttpHeaders.CacheControl, "public, max-age=60, s-maxage=60")
        }

        if (primary is ApiResponse.Success) {
            return primary
        }

        // Fallback to upstream Xposed release if fork has no releases yet
        return http.request {
            url("https://api.github.com/repos/$XPOSED_FALLBACK_ORG/$XPOSED_FALLBACK_REPO/releases/latest")
            header(HttpHeaders.CacheControl, "public, max-age=60, s-maxage=60")
        }
    }

    companion object {
        const val ORG = "RenjiYuusei"
        const val MANAGER_REPO = "KasumiManager"
        const val XPOSED_ORG = "RenjiYuusei"
        const val XPOSED_REPO = "KasumiXposed"
        const val XPOSED_FALLBACK_ORG = "kmmiio99o"
        const val XPOSED_FALLBACK_REPO = "ShiggyXposed"
    }
}
