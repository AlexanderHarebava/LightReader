/*
 * Copyright 2022 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.StrictMode
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.material.color.DynamicColors
import java.io.File
import java.util.Properties
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.readium.r2.testapp.AINEW.AIManager
import org.readium.r2.testapp.AINEW.AiProviderType
import org.readium.r2.testapp.BuildConfig.DEBUG
import org.readium.r2.testapp.ai.ChatRepository
import org.readium.r2.testapp.data.BookRepository
import org.readium.r2.testapp.data.db.AppDatabase
import org.readium.r2.testapp.domain.Bookshelf
import org.readium.r2.testapp.domain.CoverStorage
import org.readium.r2.testapp.domain.PublicationRetriever
import org.readium.r2.testapp.reader.ReaderRepository
import org.readium.r2.testapp.utils.tryOrLog
import timber.log.Timber

class Application : android.app.Application() {

    lateinit var aiManager: AIManager
        private set

    private val AI_CUSTOM_MODEL = "ai_custom_model"
    private val AI_CUSTOM_EMBEDDING_MODEL = "ai_custom_embedding_model"
    private lateinit var encryptedPrefs: SharedPreferences
    private val AI_API_KEY = "openrouter_api_key"

    // В классе Application добавьте:

    private val AI_PROVIDER_TYPE = "ai_provider_type"

    fun saveAiProviderType(type: String) {
        encryptedPrefs.edit().putString(AI_PROVIDER_TYPE, type).apply()
    }

    fun getAiCustomModel(): String? {
        return encryptedPrefs.getString(AI_CUSTOM_MODEL, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun getAiCustomEmbeddingModel(): String? {
        return encryptedPrefs.getString(AI_CUSTOM_EMBEDDING_MODEL, null)
            ?.takeIf { it.isNotBlank() }
    }

    private fun writeAiCredentials(
        key: String,
        providerType: AiProviderType,
        chatModel: String?,
        embeddingModel: String?
    ) {
        val editor = encryptedPrefs.edit()

        if (key.isBlank()) {
            editor.remove(AI_API_KEY)
            editor.remove(AI_PROVIDER_TYPE)
            editor.remove(AI_CUSTOM_MODEL)
            editor.remove(AI_CUSTOM_EMBEDDING_MODEL)
        } else {
            editor.putString(AI_API_KEY, key)
            editor.putString(AI_PROVIDER_TYPE, providerType.name)
            editor.putString(AI_CUSTOM_MODEL, chatModel?.trim().orEmpty())
            editor.putString(AI_CUSTOM_EMBEDDING_MODEL, embeddingModel?.trim().orEmpty())
        }

        editor.apply()
    }

    suspend fun saveAiCredentials(
        key: String,
        providerType: AiProviderType,
        chatModel: String?,
        embeddingModel: String?
    ) {
        writeAiCredentials(key, providerType, chatModel, embeddingModel)
        aiManager.updateApiKey(
            apiKey = key,
            providerType = providerType,
            customModel = chatModel,
            customEmbeddingModel = embeddingModel
        )
    }

    fun getAiProviderType(): AiProviderType {
        val typeName = encryptedPrefs.getString(AI_PROVIDER_TYPE, null) ?: return AiProviderType.UNKNOWN
        return try {
            AiProviderType.valueOf(typeName)
        } catch (e: Exception) {
            AiProviderType.UNKNOWN
        }
    }


    private val Context.aiApiKeyDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "ai-api-keys")

    lateinit var chatRepository: ChatRepository
        private set




    lateinit var apiKeyDataStore: DataStore<Preferences>
        private set

    lateinit var readium: Readium
        private set

    lateinit var storageDir: File

    lateinit var bookRepository: BookRepository
        private set

    lateinit var bookshelf: Bookshelf
        private set

    lateinit var readerRepository: ReaderRepository
        private set

    private val coroutineScope: CoroutineScope =
        MainScope()

    // Существующий делегат для навигации
    private val Context.navigatorPreferences: DataStore<Preferences>
        by preferencesDataStore(name = "navigator-preferences")

    override fun onCreate() {
        val themePrefs = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val isDark = themePrefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyLog()
                    .build()
            )
        }
        if (DEBUG) {
            enableStrictMode()
            Timber.plant(Timber.DebugTree())
        }

        super.onCreate()

        DynamicColors.applyToActivitiesIfAvailable(this)

        readium = Readium(this)

        storageDir = computeStorageDir()

        chatRepository = ChatRepository(applicationContext)

        val database = AppDatabase.getDatabase(this)

        bookRepository = BookRepository(database.booksDao())

        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        encryptedPrefs = EncryptedSharedPreferences.create(
            this,
            "ai_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // 🚀 Создаём синглтон и сразу подгружаем сохранённый ключ (если есть)
        aiManager = AIManager(applicationContext)
        getAiApiKey()?.let { savedKey ->
            coroutineScope.launch {
                aiManager.updateApiKey(
                    apiKey = savedKey,
                    providerType = getAiProviderType(),
                    customModel = getAiCustomModel(),
                    customEmbeddingModel = getAiCustomEmbeddingModel()
                )
            }
        }

        apiKeyDataStore = this.aiApiKeyDataStore

        val downloadsDir = File(cacheDir, "downloads")

        // Cleans the download dir.
        tryOrLog { downloadsDir.delete() }

        val publicationRetriever =
            PublicationRetriever(
                context = applicationContext,
                assetRetriever = readium.assetRetriever,
                bookshelfDir = storageDir,
                tempDir = downloadsDir,
                httpClient = readium.httpClient,
                lcpService = readium.lcpService.getOrNull()
            )

        bookshelf =
            Bookshelf(
                bookRepository,
                CoverStorage(storageDir, httpClient = readium.httpClient),
                readium.publicationOpener,
                readium.assetRetriever,
                publicationRetriever

            )

        readerRepository = ReaderRepository(
            this@Application,
            readium,
            bookRepository,
            navigatorPreferences
        )
    }

    fun getAiApiKey(): String? {
        // Возвращаем ключ только если он не пустой
        return encryptedPrefs.getString(AI_API_KEY, null)?.takeIf { it.isNotBlank() }
    }

    fun saveAiApiKey(key: String) {
        if (key.isBlank()) {
            writeAiCredentials(
                key = "",
                providerType = AiProviderType.UNKNOWN,
                chatModel = "",
                embeddingModel = ""
            )

            coroutineScope.launch {
                aiManager.updateApiKey(
                    apiKey = "",
                    providerType = AiProviderType.UNKNOWN,
                    customModel = null,
                    customEmbeddingModel = null
                )
            }
        } else {
            val providerType = AiProviderType.fromApiKey(key)
            val chatModel = getAiCustomModel()
            val embeddingModel = getAiCustomEmbeddingModel()

            writeAiCredentials(
                key = key,
                providerType = providerType,
                chatModel = chatModel,
                embeddingModel = embeddingModel
            )

            coroutineScope.launch {
                aiManager.updateApiKey(
                    apiKey = key,
                    providerType = providerType,
                    customModel = chatModel,
                    customEmbeddingModel = embeddingModel
                )
            }
        }
    }

    fun hasAiApiKey(): Boolean {
        return getAiApiKey() != null
    }


    private fun computeStorageDir(): File {
        val properties = Properties()
        val inputStream = assets.open("configs/config.properties")
        properties.load(inputStream)
        val useExternalFileDir =
            properties.getProperty("useExternalFileDir", "false")!!.toBoolean()

        return File(
            if (useExternalFileDir) {
                getExternalFilesDir(null)?.path + "/"
            } else {
                filesDir?.path + "/"
            }
        )
    }

    private fun enableStrictMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return
        }

        val executor = Executors.newSingleThreadExecutor()
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyListener(executor) { violation ->
                    Timber.e(violation, "Thread policy violation")
                }
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyListener(executor) { violation ->
                    Timber.e(violation, "VM policy violation")
                }
                .build()
        )
    }
}