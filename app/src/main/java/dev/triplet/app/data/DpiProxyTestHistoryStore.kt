package dev.triplet.app.data

import android.content.Context
import dev.triplet.app.core.DpiProxyTestConfig
import dev.triplet.app.core.DpiProxyTestResultSummary
import dev.triplet.app.core.DpiProxyTestRun
import dev.triplet.app.core.DpiProxyTestStrategy
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class DpiProxyTestHistoryStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val mutex = Mutex()

    suspend fun load(): List<DpiProxyTestRun> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!file.isFile) return@withLock emptyList()
            runCatching { DpiProxyTestHistoryCodec.decode(file.readText(StandardCharsets.UTF_8)) }
                .getOrDefault(emptyList())
                .take(MAX_RUNS)
        }
    }

    suspend fun save(runs: List<DpiProxyTestRun>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val bounded = runs.take(MAX_RUNS)
            val payload = DpiProxyTestHistoryCodec.encode(bounded)
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "$FILE_NAME.tmp")
            FileOutputStream(temp).use { output ->
                output.write(payload.toByteArray(StandardCharsets.UTF_8))
                output.fd.sync()
            }
            try {
                Files.move(
                    temp.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: Exception) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    companion object {
        const val MAX_RUNS = 10
        private const val FILE_NAME = "dpi_proxy_test_history_v1.json"
    }
}

internal object DpiProxyTestHistoryCodec {
    private const val VERSION = 1

    fun encode(runs: List<DpiProxyTestRun>): String = JSONObject()
        .put("version", VERSION)
        .put("runs", JSONArray().apply { runs.forEach { run -> put(encodeRun(run)) } })
        .toString()

    fun decode(raw: String): List<DpiProxyTestRun> {
        val root = JSONObject(raw)
        if (root.optInt("version", -1) != VERSION) return emptyList()
        val array = root.optJSONArray("runs") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                runCatching { decodeRun(item) }.getOrNull()?.let(::add)
            }
        }
    }

    private fun encodeRun(run: DpiProxyTestRun): JSONObject = JSONObject()
        .put("id", run.id)
        .put("createdAtEpochMs", run.createdAtEpochMs)
        .put(
            "selectedDomainIds",
            JSONArray().apply { run.selectedDomainIds.forEach { id -> put(id) } },
        )
        .put(
            "config",
            JSONObject()
                .put("attemptsPerHost", run.config.attemptsPerHost)
                .put("concurrency", run.config.concurrency)
                .put("timeoutSeconds", run.config.timeoutSeconds),
        )
        .put("results", JSONArray().apply { run.results.forEach { result -> put(encodeResult(result)) } })

    private fun decodeRun(json: JSONObject): DpiProxyTestRun {
        val configJson = json.getJSONObject("config")
        val domainsJson = json.getJSONArray("selectedDomainIds")
        val resultsJson = json.getJSONArray("results")
        return DpiProxyTestRun(
            id = json.getString("id"),
            createdAtEpochMs = json.getLong("createdAtEpochMs"),
            selectedDomainIds = buildSet {
                for (index in 0 until domainsJson.length()) add(domainsJson.getString(index))
            },
            config = DpiProxyTestConfig(
                attemptsPerHost = configJson.getInt("attemptsPerHost"),
                concurrency = configJson.getInt("concurrency"),
                timeoutSeconds = configJson.getInt("timeoutSeconds"),
            ),
            results = buildList {
                for (index in 0 until resultsJson.length()) add(decodeResult(resultsJson.getJSONObject(index)))
            },
        )
    }

    private fun encodeResult(result: DpiProxyTestResultSummary): JSONObject = JSONObject()
        .put("strategy", encodeStrategy(result.strategy))
        .put("backendStarted", result.backendStarted)
        .put("completed", result.completed)
        .put("hostCount", result.hostCount)
        .put("fullyWorkingHosts", result.fullyWorkingHosts)
        .put("totalSuccesses", result.totalSuccesses)
        .put("totalAttempts", result.totalAttempts)
        .apply { result.medianLatencyMs?.let { put("medianLatencyMs", it) } }

    private fun decodeResult(json: JSONObject): DpiProxyTestResultSummary = DpiProxyTestResultSummary(
        strategy = decodeStrategy(json.getJSONObject("strategy")),
        backendStarted = json.getBoolean("backendStarted"),
        completed = json.getBoolean("completed"),
        hostCount = json.getInt("hostCount"),
        fullyWorkingHosts = json.getInt("fullyWorkingHosts"),
        totalSuccesses = json.getInt("totalSuccesses"),
        totalAttempts = json.getInt("totalAttempts"),
        medianLatencyMs = if (json.has("medianLatencyMs")) json.getLong("medianLatencyMs") else null,
    )

    private fun encodeStrategy(strategy: DpiProxyTestStrategy): JSONObject = JSONObject()
        .put("id", strategy.id)
        .put("referenceIndex", strategy.referenceIndex)
        .put("command", strategy.command)
        .put("args", JSONArray().apply { strategy.args.forEach { arg -> put(arg) } })

    private fun decodeStrategy(json: JSONObject): DpiProxyTestStrategy {
        val argsJson = json.getJSONArray("args")
        return DpiProxyTestStrategy(
            id = json.getString("id"),
            referenceIndex = json.getInt("referenceIndex"),
            command = json.getString("command"),
            args = buildList {
                for (index in 0 until argsJson.length()) add(argsJson.getString(index))
            },
        )
    }
}
