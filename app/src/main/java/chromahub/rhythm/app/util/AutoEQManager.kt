/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import android.content.Context
import chromahub.rhythm.app.shared.data.model.AutoEQDatabase
import chromahub.rhythm.app.shared.data.model.AutoEQProfile
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Manager for loading and accessing AutoEQ profiles
 */
class AutoEQManager(private val context: Context) {
    
    private var database: AutoEQDatabase? = null
    private val gson = Gson()
    private var customProfiles: List<AutoEQProfile> = emptyList()

    fun setCustomProfiles(profiles: List<AutoEQProfile>) {
        this.customProfiles = profiles
    }
    
    /**
     * Load AutoEQ profiles from assets
     */
    suspend fun loadProfiles(): Result<AutoEQDatabase> = withContext(Dispatchers.IO) {
        try {
            cachedDatabase?.let {
                database = it
                return@withContext Result.success(it)
            }
            
            val loadedDatabase = synchronized(lock) {
                cachedDatabase ?: run {
                    val jsonString = context.assets.open("autoeq_profiles.json").use { inputStream ->
                        inputStream.bufferedReader().use { it.readText() }
                    }
                    gson.fromJson(jsonString, AutoEQDatabase::class.java).also {
                        cachedDatabase = it
                    }
                }
            }
            database = loadedDatabase
            Result.success(loadedDatabase)
            
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getCombinedProfiles(): List<AutoEQProfile> {
        val base = database?.profiles ?: emptyList()
        if (customProfiles.isEmpty()) return base
        val customNames = customProfiles.map { it.name.lowercase() }.toSet()
        return customProfiles + base.filter { it.name.lowercase() !in customNames }
    }
    
    /**
     * Get all available profiles
     */
    fun getAllProfiles(): List<AutoEQProfile> {
        return getCombinedProfiles()
    }
    
    /**
     * Search profiles by name or brand
     */
    fun searchProfiles(query: String): List<AutoEQProfile> {
        val combined = getCombinedProfiles()
        if (query.isBlank()) return combined
        val lowerQuery = query.lowercase()
        return combined.filter { profile ->
            profile.name.lowercase().contains(lowerQuery) ||
            profile.brand.lowercase().contains(lowerQuery) ||
            profile.type.lowercase().contains(lowerQuery)
        }
    }
    
    /**
     * Get profiles by brand
     */
    fun getProfilesByBrand(brand: String): List<AutoEQProfile> {
        return getCombinedProfiles().filter { it.brand.equals(brand, ignoreCase = true) }
    }
    
    /**
     * Get profiles by type (Over-Ear, In-Ear, On-Ear)
     */
    fun getProfilesByType(type: String): List<AutoEQProfile> {
        return getCombinedProfiles().filter { it.type.equals(type, ignoreCase = true) }
    }
    
    /**
     * Get all available brands
     */
    fun getAllBrands(): List<String> {
        return getCombinedProfiles().map { it.brand }.distinct().sorted()
    }
    
    /**
     * Get all available types
     */
    fun getAllTypes(): List<String> {
        return getCombinedProfiles().map { it.type }.distinct().sorted()
    }
    
    /**
     * Find a profile by exact name match
     */
    fun findProfileByName(name: String): AutoEQProfile? {
        return getCombinedProfiles().find { it.name.equals(name, ignoreCase = true) }
    }
    
    /**
     * Get recommended profiles (top popular models)
     */
    fun getRecommendedProfiles(): List<AutoEQProfile> {
        val recommended = listOf(
            "Sony WH-1000XM4",
            "AirPods Pro",
            "Sennheiser HD 600",
            "Bose QuietComfort 45",
            "Samsung Galaxy Buds Pro"
        )
        
        return getCombinedProfiles().filter { it.name in recommended }
    }

    companion object {
        @Volatile
        private var cachedDatabase: AutoEQDatabase? = null
        private val lock = Any()

        fun clearCache() {
            synchronized(lock) {
                cachedDatabase = null
            }
        }
    }
}
