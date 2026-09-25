package com.stailegrow.maxstrike.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.stailegrow.maxstrike.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Проверка обновлений через GitHub Releases открытого репозитория
 * (github.com/stailegrow/lifevpn-android, куда публикуются готовые APK) —
 * по прямой просьбе пользователя: у приложения нет ни Google Play, ни
 * своего сервера обновлений, только GitHub-релизы, поэтому и проверять
 * новую версию естественно там же.
 *
 * API GitHub отдаёт JSON последнего релиза (tag_name, body-описание,
 * список assets) без авторизации — публичный репозиторий, обычного лимита
 * в 60 запросов/час с IP с большим запасом хватает на проверку раз за
 * запуск приложения плюс редкие ручные нажатия в Настройках.
 *
 * По прямой просьбе пользователя обновление не просто открывает страницу
 * релиза в браузере, а качает APK и сразу предлагает установить —
 * см. downloadApkBlocking()/installUpdate() ниже.
 */
object UpdateChecker {

    private const val RELEASES_API_URL = "https://api.github.com/repos/stailegrow/lifevpn-android/releases/latest"
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 15_000

    class UpdateException(message: String) : Exception(message)

    data class UpdateInfo(
        val version: String,
        val tagName: String,
        val notes: String,
        val downloadUrl: String,
        val apkSizeBytes: Long,
    )

    sealed class State {
        object Idle : State()
        object Checking : State()
        object UpToDate : State()
        data class Available(val info: UpdateInfo) : State()
        data class Downloading(val info: UpdateInfo, val progress: Int) : State()
        data class ReadyToInstall(val info: UpdateInfo, val apkFile: File) : State()
        data class Failed(val info: UpdateInfo?, val message: String, val duringDownload: Boolean) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    // Свой скоуп, а не переданный снаружи — загрузка APK должна пережить
    // экран, с которого её запустили (пользователь мог уйти с Настроек,
    // пока файл ещё качается), тот же приём, что у ServerStore.ioScope.
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Тихая проверка при каждом запуске приложения — как и у
     *  GeoAssets.refreshOnLaunchBlocking(), ошибку (нет сети, GitHub
     *  недоступен) никому не показываем, приложение просто остаётся в
     *  Idle: раздражать баннером на ровном месте не нужно, а как только
     *  реальная новая версия появится, эта же проверка её найдёт при
     *  следующем запуске. */
    fun checkOnLaunch() {
        _state.value = State.Checking
        ioScope.launch {
            try {
                val info = checkBlocking(BuildConfig.VERSION_NAME)
                _state.value = if (info != null) State.Available(info) else State.Idle
            } catch (e: Exception) {
                _state.value = State.Idle
            }
        }
    }

    /** Проверка по кнопке "Проверить обновления" в Настройках — в отличие
     *  от checkOnLaunch(), тут результат (и "версия последняя", и ошибку)
     *  нужно показать явно, раз пользователь сам попросил проверить. */
    fun checkManually() {
        _state.value = State.Checking
        ioScope.launch {
            try {
                val info = checkBlocking(BuildConfig.VERSION_NAME)
                _state.value = if (info != null) State.Available(info) else State.UpToDate
            } catch (e: Exception) {
                _state.value = State.Failed(
                    info = null,
                    message = e.message ?: L.t("Не удалось проверить обновления.", "Could not check for updates."),
                    duringDownload = false,
                )
            }
        }
    }

    fun startDownload(context: Context, info: UpdateInfo) {
        _state.value = State.Downloading(info, 0)
        val appContext = context.applicationContext
        ioScope.launch {
            try {
                val file = downloadApkBlocking(appContext, info) { progress ->
                    _state.value = State.Downloading(info, progress)
                }
                _state.value = State.ReadyToInstall(info, file)
            } catch (e: Exception) {
                _state.value = State.Failed(
                    info = info,
                    message = e.message ?: L.t("Не удалось скачать обновление.", "Could not download the update."),
                    duringDownload = true,
                )
            }
        }
    }

    /** Скрыть баннер/сбросить статус (например, после закрытия сообщения
     *  об ошибке) — саму найденную версию не забываем, при следующем
     *  запуске или ручной проверке она найдётся снова. */
    fun dismiss() {
        _state.value = State.Idle
    }

    /** Блокирующий вызов — звать только с Dispatchers.IO. Возвращает
     *  null, если релиз на GitHub не новее текущей версии. */
    fun checkBlocking(currentVersion: String): UpdateInfo? {
        val connection = try {
            URL(RELEASES_API_URL).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw UpdateException(L.t("Неверный адрес GitHub API.", "Invalid GitHub API address."))
        }
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.instanceFollowRedirects = true

        try {
            val code = try {
                connection.responseCode
            } catch (e: java.io.IOException) {
                throw UpdateException(L.t("Не удалось связаться с GitHub (${e.message ?: "таймаут"}).", "Could not reach GitHub (${e.message ?: "timeout"})."))
            }
            if (code !in 200..299) {
                throw UpdateException(L.t("GitHub вернул код $code.", "GitHub returned code $code."))
            }

            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = try {
                JSONObject(body)
            } catch (e: Exception) {
                throw UpdateException(L.t("Не удалось разобрать ответ GitHub.", "Could not parse GitHub's reply."))
            }

            val tagName = json.optString("tag_name", "")
            if (tagName.isEmpty()) {
                throw UpdateException(L.t("В ответе GitHub нет версии релиза.", "GitHub's reply has no release version."))
            }
            val version = tagName.removePrefix("v").removePrefix("V")
            val notes = json.optString("body", "")

            val assets: JSONArray = json.optJSONArray("assets") ?: JSONArray()
            var apkUrl: String? = null
            var apkSize = 0L
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url", "").ifEmpty { null }
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }
            if (apkUrl == null) {
                throw UpdateException(L.t("В релизе на GitHub нет прикреплённого APK.", "The GitHub release has no attached APK."))
            }

            if (!isNewerVersion(version, currentVersion)) return null

            return UpdateInfo(
                version = version,
                tagName = tagName,
                notes = notes,
                downloadUrl = apkUrl,
                apkSizeBytes = apkSize,
            )
        } finally {
            connection.disconnect()
        }
    }

    /** Простое сравнение версий вида "2.3.0" по числовым компонентам —
     *  ни SemVer с суффиксами (-beta и т.п.), ни версии другой длины тут
     *  не встречаются, у проекта всегда versionName вида "X.Y.Z". */
    private fun isNewerVersion(remote: String, current: String): Boolean {
        val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(r.size, c.size)
        for (i in 0 until len) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    /** Блокирующий вызов — звать только с Dispatchers.IO. Качает APK во
     *  временный файл в кэше приложения (через staging-файл — тот же
     *  приём, что у GeoAssets.fetch(), чтобы оборванная закачка не
     *  оставила на диске огрызок, который потом попытаются установить) и
     *  возвращает готовый файл. onProgress зовётся с процентом (0..100),
     *  если сервер прислал Content-Length — не всегда, тогда прогресс
     *  просто не обновляется до самого конца. */
    private fun downloadApkBlocking(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File {
        val url = try {
            URL(info.downloadUrl)
        } catch (e: Exception) {
            throw UpdateException(L.t("Неверная ссылка на APK.", "Invalid APK link."))
        }
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true

        try {
            val code = try {
                connection.responseCode
            } catch (e: java.io.IOException) {
                throw UpdateException(L.t("Не удалось подключиться к GitHub (${e.message ?: "таймаут"}).", "Could not connect to GitHub (${e.message ?: "timeout"})."))
            }
            if (code !in 200..299) {
                throw UpdateException(L.t("GitHub вернул код $code при скачивании.", "GitHub returned code $code while downloading."))
            }

            val total = connection.contentLengthLong
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val staging = File(dir, "update.apk.part")
            val destination = File(dir, "LifeVPN-${info.version}.apk")

            var readBytes = 0L
            try {
                connection.inputStream.use { input ->
                    staging.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n == -1) break
                            output.write(buffer, 0, n)
                            readBytes += n
                            if (total > 0) {
                                onProgress(((readBytes * 100) / total).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                }
            } catch (e: java.io.IOException) {
                staging.delete()
                throw UpdateException(L.t("Скачивание оборвалось (${e.message ?: "таймаут"}).", "The download was interrupted (${e.message ?: "timeout"})."))
            }

            if (total > 0 && readBytes < total) {
                staging.delete()
                throw UpdateException(L.t("Файл пришёл обрезанным.", "The file arrived truncated."))
            }

            if (destination.exists()) destination.delete()
            if (!staging.renameTo(destination)) {
                throw UpdateException(L.t("Не удалось сохранить APK на диск.", "Could not save the APK to disk."))
            }
            onProgress(100)
            return destination
        } finally {
            connection.disconnect()
        }
    }

    /** На Android 8+ система разрешает установку из конкретных источников
     *  индивидуально (per-app) — до неё это был один общий тумблер
     *  "Неизвестные источники", и попытка установки сама по себе
     *  провоцирует системный запрос, поэтому раньше проверять нечего. */
    fun canInstallPackages(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Запускает системный установщик пакетов на скачанном APK через
     *  FileProvider (content:// URI — начиная с Android 7 обычный
     *  file:// URI из другого приложения запрещён StrictMode). */
    fun installUpdate(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
