package sh.measure.android.exporter

import sh.measure.android.executors.MeasureExecutorService
import sh.measure.android.logger.LogLevel
import sh.measure.android.logger.Logger
import sh.measure.android.logger.MonitorPrinter
import sh.measure.android.storage.Database
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

internal interface Exporter {
    /**
     * Local-only: monitors are already persisted in the database as they are collected. This
     * simply reports how many are currently retained.
     */
    fun export()

    /**
     * Same as [export] in local-only mode.
     */
    fun flush()
}

/**
 * Local-only exporter.
 *
 * This SDK never uploads data to a remote server. All collected monitors (events and spans) are
 * kept in the local database (bounded by the configured max disk usage) and printed to logcat as
 * they are collected. This class only logs a short summary of how many monitors are retained so
 * developers know where the data lives.
 */
internal class ExporterImpl(
    private val logger: Logger,
    private val database: Database,
    private val eventExportService: MeasureExecutorService,
) : Exporter {
    private val isExporting = AtomicBoolean(false)

    override fun export() = logRetainedSignalsAsync()

    override fun flush() = logRetainedSignalsAsync()

    private fun logRetainedSignalsAsync() {
        if (isExporting.compareAndSet(false, true)) {
            try {
                eventExportService.submit {
                    try {
                        logRetainedSignals()
                    } finally {
                        isExporting.set(false)
                    }
                }
            } catch (_: RejectedExecutionException) {
                isExporting.set(false)
            }
        }
    }

    private fun logRetainedSignals() {
        try {
            val events = database.getEventsCount()
            val spans = database.getSpansCount()
            MonitorPrinter.line(
                "Local mode: not uploading. Retained in DB -> $events events, $spans spans.",
            )
        } catch (e: Exception) {
            logger.log(LogLevel.Error, "Exporter: failed to read retained signal counts", e)
        }
    }
}
