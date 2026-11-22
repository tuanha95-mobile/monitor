package sh.measure.android.config

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import sh.measure.android.executors.MeasureExecutorService
import sh.measure.android.logger.LogLevel
import sh.measure.android.logger.Logger
import sh.measure.android.serialization.jsonSerializer
import sh.measure.android.storage.FileStorage
import sh.measure.android.tracing.InternalTrace

internal interface ConfigLoader {
    fun loadDynamicConfig(onLoaded: (DynamicConfig?) -> Unit)
}

/**
 * Loads dynamic config from the on-device cache/bundled file only.
 *
 * This SDK runs in local-only mode and never contacts a server, so there is no remote config
 * refresh. If no config file is present, callers fall back to the built-in defaults.
 */
internal class ConfigLoaderImpl(
    private val ioExecutor: MeasureExecutorService,
    private val fileStorage: FileStorage,
    private val logger: Logger,
) : ConfigLoader {
    override fun loadDynamicConfig(onLoaded: (DynamicConfig?) -> Unit) {
        loadConfigFromDisk(onLoaded)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun loadConfigFromDisk(onLoaded: (DynamicConfig?) -> Unit) {
        ioExecutor.submit {
            InternalTrace.trace(
                { "msr-load-config" },
                {
                    val file = fileStorage.getConfigFile()
                    if (file != null && file.exists() && file.length() > 0) {
                        try {
                            val config = file.inputStream().use { stream ->
                                jsonSerializer.decodeFromStream<DynamicConfig?>(stream)
                            }
                            logger.log(
                                LogLevel.Debug,
                                "ConfigLoader: Config loaded successfully from disk",
                            )
                            onLoaded(config)
                        } catch (e: Exception) {
                            logger.log(LogLevel.Error, "ConfigLoader: Failed to load config", e)
                            onLoaded(null)
                        }
                    } else {
                        logger.log(LogLevel.Debug, "ConfigLoader: No config found on disk, falling back to defaults")
                        onLoaded(null)
                    }
                },
            )
        }
    }
}
