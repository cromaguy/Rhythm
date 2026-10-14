/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * GitHub Release model that matches the GitHub API release response format
 */
data class GitHubRelease(
    val id: Long,
    val tag_name: String,
    val name: String,
    val body: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val published_at: String,
    val html_url: String,
    val assets: List<GitHubAsset>
)

/**
 * GitHub Asset model for release assets
 */
data class GitHubAsset(
    val id: Long,
    val name: String,
    val browser_download_url: String,
    val content_type: String,
    val size: Long,
    val state: String,
    val download_count: Long = 0,
    val digest: String? = null
)

/**
 * Retrofit service interface for GitHub API
 */
interface GitHubApiService {
    /**
     * Fetch all releases for a repository
     * 
     * @param owner The GitHub username of the repository owner
     * @param repo The repository name
     * @param perPage Number of results per page (max 100)
     * @return List of releases
     */
    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 10
    ): Response<List<GitHubRelease>>
    
    /**
     * Fetch only the latest release
     * This excludes pre-releases and drafts
     * 
     * @param owner The GitHub username of the repository owner
     * @param repo The repository name
     * @return The latest release
     */
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<GitHubRelease>
    
    /**
     * Fetch a specific release by its ID
     * 
     * @param owner The GitHub username of the repository owner
     * @param repo The repository name
     * @param releaseId The ID of the release
     * @return The requested release
     */
    @GET("repos/{owner}/{repo}/releases/{release_id}")
    suspend fun getRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("release_id") releaseId: Long
    ): Response<GitHubRelease>
    
    /**
     * Fetch all releases with conditional request headers for smart polling
     * Supports HTTP 304 Not Modified responses when using If-None-Match and If-Modified-Since
     * 
     * @param owner The GitHub username of the repository owner
     * @param repo The repository name
     * @param perPage Number of results per page (max 100)
     * @param ifNoneMatch ETag from previous response for conditional request
     * @param ifModifiedSince Last-Modified timestamp from previous response
     * @return List of releases or 304 if not modified
     */
    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleasesWithHeaders(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 10,
        @Header("If-None-Match") ifNoneMatch: String? = null,
        @Header("If-Modified-Since") ifModifiedSince: String? = null
    ): Response<List<GitHubRelease>>
    
    /**
     * Fetch only the latest release with conditional request headers for smart polling
     * Supports HTTP 304 Not Modified responses
     * 
     * @param owner The GitHub username of the repository owner
     * @param repo The repository name
     * @param ifNoneMatch ETag from previous response for conditional request
     * @param ifModifiedSince Last-Modified timestamp from previous response
     * @return The latest release or 304 if not modified
     */
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestReleaseWithHeaders(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("If-None-Match") ifNoneMatch: String? = null,
        @Header("If-Modified-Since") ifModifiedSince: String? = null
    ): Response<GitHubRelease>

    /**
     * Fetch workflow runs for a repository workflow
     * 
     * @param owner The GitHub username of the repository owner
     * @param repo The repository name
     * @param workflowId The workflow ID (e.g. nightly.yml)
     * @param status Filter runs by status (e.g. success)
     * @param perPage Number of results per page (max 100)
     * @return Workflow runs response wrapper
     */
    @GET("repos/{owner}/{repo}/actions/workflows/{workflow_id}/runs")
    suspend fun getWorkflowRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflow_id") workflowId: String,
        @Query("status") status: String = "success",
        @Query("per_page") perPage: Int = 10
    ): Response<GitHubWorkflowRunsResponse>

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/artifacts")
    suspend fun getRunArtifacts(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): Response<GitHubArtifactsResponse>
}

/**
 * GitHub Artifacts Response wrapper
 */
data class GitHubArtifactsResponse(
    val total_count: Int,
    val artifacts: List<GitHubArtifact>
)

/**
 * GitHub Artifact model
 */
data class GitHubArtifact(
    val id: Long,
    val name: String,
    val size_in_bytes: Long,
    val expired: Boolean
)

/**
 * GitHub Workflow Runs Response wrapper
 */
data class GitHubWorkflowRunsResponse(
    val total_count: Int,
    val workflow_runs: List<GitHubWorkflowRun>
)

/**
 * GitHub Workflow Run model
 */
data class GitHubWorkflowRun(
    val id: Long,
    val run_number: Int,
    val status: String,
    val conclusion: String?,
    val updated_at: String,
    val head_branch: String,
    val head_sha: String,
    val head_commit: GitHubHeadCommit?
)

/**
 * GitHub Head Commit model inside workflow run
 */
data class GitHubHeadCommit(
    val id: String,
    val message: String,
    val timestamp: String
) 
