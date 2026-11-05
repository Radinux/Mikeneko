package app.mikeneko.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Les mises à jour de Mikeneko, depuis les releases GitHub du dépôt public Radinux/Mikeneko-releases
 * (le code reste dans un dépôt privé : aucun jeton dans l'APK).
 *
 * Au lancement (une fois par jour au plus), on demande la dernière release. Plus récente que la
 * version installée : son APK se télécharge en fond, puis s'installe **sans appui** (Android 12+,
 * l'appli se met à jour elle-même, même clé de signature) quand on quitte l'appli et que rien ne
 * joue (l'installation ferme l'appli). Il faut seulement avoir autorisé une fois Mikeneko à installer
 * des applis (« sources inconnues ») ; avant Android 12, Android demande une confirmation.
 */
object AppUpdates {
    private const val LATEST = "https://api.github.com/repos/Radinux/Mikeneko-releases/releases/latest"
    private const val DAY_MS = 24 * 60 * 60_000L

    data class Release(val version: String, val notes: String, val page: String, val apkUrl: String)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val version: String) : State
        data class Downloading(val release: Release, val progress: Float) : State
        /** Téléchargée : s'installe en quittant l'appli (ou tout de suite depuis les réglages). */
        data class Ready(val release: Release, val file: File) : State
        data object Installing : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    }

    fun installedVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /** [a] est plus récente que [b] (« 2.1.0 » contre « 2.0.4 », chiffre par chiffre). */
    fun newer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    private fun prefs(context: Context) = context.getSharedPreferences("mises_a_jour", Context.MODE_PRIVATE)

    /** Au lancement : une vérification par jour au plus. */
    fun checkIfDue(context: Context) {
        val app = context.applicationContext
        val last = prefs(app).getLong("derniere", 0L)
        if (System.currentTimeMillis() - last < DAY_MS) return
        check(app)
    }

    /** Vérifie maintenant (et télécharge si une version plus récente existe). */
    fun check(context: Context) {
        val app = context.applicationContext
        val busy = _state.value
        if (busy is State.Checking || busy is State.Downloading || busy is State.Installing) return
        _state.value = State.Checking
        scope.launch {
            val result = runCatching {
                prefs(app).edit().putLong("derniere", System.currentTimeMillis()).apply()
                latest()
            }
            val release = result.getOrElse {
                _state.value = State.Failed(it.message ?: it.javaClass.simpleName)
                return@launch
            }
            val installed = installedVersion(app)
            if (release == null || !newer(release.version, installed)) {
                _state.value = State.UpToDate(installed)
                return@launch
            }
            download(app, release)
        }
    }

    /** La dernière release (null : aucune publiée). */
    private fun latest(): Release? {
        val request = Request.Builder().url(LATEST).header("Accept", "application/vnd.github+json").header("User-Agent", "Mikeneko").build()
        http.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            check(response.isSuccessful) { "GitHub : ${response.code}" }
            val json = JSONObject(response.body!!.string())
            val assets = json.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") } ?: return null
            return Release(
                version = json.optString("tag_name").removePrefix("v"),
                notes = json.optString("body"),
                page = json.optString("html_url"),
                apkUrl = apk.getString("browser_download_url"),
            )
        }
    }

    private fun download(context: Context, release: Release) {
        val dir = File(context.cacheDir, "mises_a_jour").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // les anciennes
        val file = File(dir, "Mikeneko-${release.version}.apk")
        runCatching {
            _state.value = State.Downloading(release, 0f)
            val request = Request.Builder().url(release.apkUrl).header("User-Agent", "Mikeneko").build()
            http.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "GitHub : ${response.code}" }
                val body = response.body!!
                val total = body.contentLength().takeIf { it > 0 }
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            done += n
                            if (total != null) _state.value = State.Downloading(release, done.toFloat() / total)
                        }
                    }
                }
            }
        }.onSuccess {
            _state.value = State.Ready(release, file)
        }.onFailure {
            file.delete()
            _state.value = State.Failed(it.message ?: it.javaClass.simpleName)
        }
    }

    /** Mikeneko a le droit d'installer des applis (réglage Android « sources inconnues »). */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** En quittant l'appli : la mise à jour prête s'installe, si on a le droit et que rien ne joue. */
    fun installIfReady(context: Context, playing: Boolean) {
        val ready = _state.value as? State.Ready ?: return
        if (playing || !canInstall(context)) return
        install(context, ready.file)
    }

    /** Installe l'APK téléchargé (sans appui sur Android 12+ : c'est une mise à jour de l'appli elle-même). */
    fun install(context: Context, file: File) {
        val app = context.applicationContext
        _state.value = State.Installing
        scope.launch {
            runCatching {
                val installer = app.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(app.packageName)
                    if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    session.openWrite("mikeneko.apk", 0, file.length()).use { output ->
                        file.inputStream().use { it.copyTo(output) }
                        session.fsync(output)
                    }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    val result = PendingIntent.getBroadcast(app, id, Intent(app, UpdateReceiver::class.java), flags)
                    session.commit(result.intentSender)
                }
            }.onFailure { _state.value = State.Failed(it.message ?: it.javaClass.simpleName) }
        }
    }

    internal fun failed(message: String) {
        _state.value = State.Failed(message)
    }
}

/** La réponse d'Android à l'installation : une confirmation à montrer (avant Android 12), ou une erreur. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // l'appli est remplacée (et fermée) par la nouvelle version
            else -> AppUpdates.failed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "installation refusée")
        }
    }
}
