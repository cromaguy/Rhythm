/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package android.net

import android.os.Parcel

class TestUri(
    private val schemeStr: String,
    private val pathStr: String
) : Uri() {
    override fun isHierarchical(): Boolean = true
    override fun isRelative(): Boolean = false
    override fun getScheme(): String = schemeStr
    override fun getSchemeSpecificPart(): String = "//$pathStr"
    override fun getEncodedSchemeSpecificPart(): String = "//$pathStr"
    override fun getAuthority(): String? = null
    override fun getEncodedAuthority(): String? = null
    override fun getUserInfo(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getHost(): String? = null
    override fun getPort(): Int = -1
    override fun getPath(): String = pathStr
    override fun getEncodedPath(): String = pathStr
    override fun getQuery(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getFragment(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getPathSegments(): List<String> = pathStr.split("/").filter { it.isNotEmpty() }
    override fun getLastPathSegment(): String? = pathStr.substringAfterLast('/')
    override fun buildUpon(): Builder = throw UnsupportedOperationException()
    override fun toString(): String = "$schemeStr://$pathStr"
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) {}
}
