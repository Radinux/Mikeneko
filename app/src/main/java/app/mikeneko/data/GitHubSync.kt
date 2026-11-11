package app.mikeneko.data

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Le compte GitHub (2.19.0), facultatif : il sert de coffre pour synchroniser ses appareils **partout**,
 * pas seulement sur le même réseau (la synchro par PIN, [LocalSync], reste là).
 *
 * - **Connexion** : la connexion « par appareil » de GitHub ([signIn]) : l'appli montre un code, on le
 *   tape sur github.com/login/device. Ni mot de passe ni secret dans l'appli (seul l'identifiant public
 *   [CLIENT_ID]) ; le jeton reçu ne peut toucher qu'aux Gists (portée `gist`) et reste chiffré sur le
 *   téléphone (clé du Keystore Android).
 * - **La synchro** : la sauvegarde ([MikenekoBackup]) va dans un Gist **secret** du compte
 *   (`mikeneko-sync.json`), **chiffrée avant l'envoi** (AES-GCM) avec la **clé de synchro** : un code de
 *   20 caractères tiré sur le premier appareil. GitHub ne voit que du charabia. Un autre appareil a
 *   besoin de cette clé : il la reçoit tout seul d'un appareil lié par PIN, sinon on la tape.
 *   Chaque synchro : lire le Gist, fusionner (sans rien effacer), réécrire le tout. Au retour dans
 *   l'appli et en la quittant ([syncIfDue]), et sur demande.
 * - **Le profil** : on peut reprendre son nom et sa photo GitHub ([importProfile]).
 */
object GitHubSync {
    /** L'identifiant public de l'appli OAuth « Mikeneko » (connexion par appareil, sans secret). */
    const val CLIENT_ID = "Ov23li382cqk2S1o0ScM"
    private const val FILE = "mikeneko-sync.json"
    private const val DESCRIPTION = "Mikeneko sync (encrypted)"
    private const val API = "https://api.github.com"
    /** Au retour dans l'appli : une synchro si la dernière date de plus d'une minute. */
    private const val MIN_GAP_MS = 60_000L
    /** Appli ouverte : on va voir toutes les 3 min ce que les autres appareils ont envoyé (2.19.2). */
    private const val POLL_MS = 3 * 60_000L
    private const val VAULT_ALIAS = "mikeneko-github"
    /** L'alphabet de la clé de synchro : sans 0/O ni 1/I/L, pour la recopier sans erreur. */
    private const val KEY_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private const val KEY_LENGTH = 20

    data class Account(val login: String, val name: String?, val avatarUrl: String?)

    sealed interface Login {
        data object Out : Login
        /** On attend que la personne tape [userCode] sur [uri]. */
        data class Code(val userCode: String, val uri: String) : Login
        data class In(val account: Account) : Login
    }

    sealed interface Status {
        data object Idle : Status
        data object Running : Status
        data class Done(val added: MikenekoBackup.Added, val at: Long) : Status
        /** Le coffre existe déjà, chiffré par un autre appareil : il faut sa clé ([wrong] : celle tapée ne va pas). */
        data class NeedKey(val wrong: Boolean) : Status
        data class Failed(val reason: Reason) : Status
    }

    enum class Reason { Network, Expired, Denied, TimedOut }

    private val _login = MutableStateFlow<Login>(Login.Out)
    val login: StateFlow<Login> = _login.asStateFlow()

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _lastSync = MutableStateFlow(0L)
    val lastSync: StateFlow<Long> = _lastSync.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = Mutex()
    private val random = SecureRandom()
    private var signInJob: Job? = null
    private var loaded = false

    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("github", Context.MODE_PRIVATE)

    fun load(context: Context) {
        synchronized(this) {
            if (loaded) return
            loaded = true
            val p = prefs(context)
            val token = token(context)
            val login = p.getString("login", null)
            _login.value = if (token != null && login != null) Login.In(Account(login, p.getString("nom", null), p.getString("photo", null))) else Login.Out
            _lastSync.value = p.getLong("derniere", 0L)
        }
    }

    // ------------------------------------------------------------------ connexion

    /** Lance la connexion par appareil : [login] passe à [Login.Code], puis à [Login.In] une fois le code tapé. */
    fun signIn(context: Context) {
        val app = context.applicationContext
        signInJob?.cancel()
        signInJob = scope.launch {
            val outcome = runCatching { deviceFlow(app) }.getOrElse { Reason.Network }
            if (outcome != null) {
                _login.value = Login.Out
                _status.value = Status.Failed(outcome)
            }
        }
    }

    fun cancelSignIn() {
        signInJob?.cancel()
        signInJob = null
        if (_login.value is Login.Code) _login.value = Login.Out
    }

    /** null : connecté ; sinon pourquoi ça n'a pas marché. */
    private suspend fun deviceFlow(context: Context): Reason? {
        val start = postForm("https://github.com/login/device/code", mapOf("client_id" to CLIENT_ID, "scope" to "gist"))
        val deviceCode = start.getString("device_code")
        _status.value = Status.Idle
        _login.value = Login.Code(start.getString("user_code"), start.optString("verification_uri", "https://github.com/login/device"))
        var interval = start.optLong("interval", 5L).coerceAtLeast(5L)
        val deadline = System.currentTimeMillis() + start.optLong("expires_in", 900L) * 1000
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000)
            val answer = runCatching {
                postForm(
                    "https://github.com/login/oauth/access_token",
                    mapOf("client_id" to CLIENT_ID, "device_code" to deviceCode, "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"),
                )
            }.getOrNull() ?: continue // un raté de réseau : on réessaie au tour suivant
            val token = answer.optString("access_token")
            if (token.isNotEmpty()) {
                val user = getJson(token, "$API/user")
                val account = Account(user.getString("login"), user.optString("name").ifBlank { null }, user.optString("avatar_url").ifBlank { null })
                saveToken(context, token)
                prefs(context).edit().putString("login", account.login).putString("nom", account.name).putString("photo", account.avatarUrl).apply()
                _login.value = Login.In(account)
                syncNow(context)
                return null
            }
            when (answer.optString("error")) {
                "authorization_pending" -> Unit
                "slow_down" -> interval = answer.optLong("interval", interval + 5)
                "access_denied" -> return Reason.Denied
                "expired_token" -> return Reason.TimedOut
                else -> return Reason.Network
            }
        }
        return Reason.TimedOut
    }

    /**
     * Se déconnecte : le jeton est oublié sur ce téléphone. Le Gist reste sur GitHub (pour les autres
     * appareils) ; l'accès se retire dans GitHub › Settings › Applications.
     */
    fun signOut(context: Context) {
        cancelSignIn()
        prefs(context).edit().remove("jeton").remove("login").remove("nom").remove("photo").remove("gist").apply()
        _login.value = Login.Out
        _status.value = Status.Idle
    }

    // ------------------------------------------------------------------ la synchro

    private var pollJob: Job? = null

    /**
     * L'appli est à l'écran ([visible]) : on relève le coffre régulièrement, pour recevoir ce que les
     * autres appareils y mettent (ce qu'on change ici part tout seul, voir [AutoSync]).
     */
    fun foreground(context: Context, visible: Boolean) {
        val app = context.applicationContext
        pollJob?.cancel()
        pollJob = null
        if (!visible) return
        pollJob = scope.launch {
            while (true) {
                syncIfDue(app)
                delay(POLL_MS)
            }
        }
    }

    /** Au retour dans l'appli, puis régulièrement : une synchro si la dernière date de plus d'une minute. */
    fun syncIfDue(context: Context) {
        load(context)
        if (_login.value !is Login.In || _status.value is Status.NeedKey) return
        if (System.currentTimeMillis() - _lastSync.value < MIN_GAP_MS) return
        syncNow(context)
    }

    fun syncNow(context: Context) {
        val app = context.applicationContext
        scope.launch { sync(app) }
    }

    /** La clé de synchro tapée (celle d'un autre appareil) : gardée, puis une synchro pour l'essayer. */
    fun enterKey(context: Context, typed: String) {
        val code = normalizeKey(typed) ?: run {
            _status.value = Status.NeedKey(wrong = true)
            return
        }
        saveKey(context, code)
        syncNow(context)
    }

    /** La clé de synchro de cet appareil, à recopier sur un autre (null : pas encore de coffre). */
    fun syncKey(context: Context): String? = key(context)?.chunked(5)?.joinToString("-")

    /** Reçue d'un appareil lié par PIN ([LocalSync]) : adoptée si on n'en a pas encore. */
    internal fun receiveKey(context: Context, code: String?) {
        val normalized = code?.let(::normalizeKey) ?: return
        if (key(context) != null) return
        saveKey(context, normalized)
        if (_status.value is Status.NeedKey) syncNow(context)
    }

    internal fun keyToShare(context: Context): String? = key(context)

    private suspend fun sync(context: Context) {
        if (!running.tryLock()) return
        try {
            val token = token(context) ?: return
            _status.value = Status.Running
            _status.value = runCatching { syncWith(context, token) }.getOrElse { error ->
                if (error is Unauthorized) {
                    // Le jeton ne marche plus (retiré sur GitHub, ou expiré) : il faut se reconnecter
                    signOut(context)
                    Status.Failed(Reason.Expired)
                } else {
                    Status.Failed(Reason.Network)
                }
            }
        } finally {
            running.unlock()
        }
    }

    private suspend fun syncWith(context: Context, token: String): Status {
        val p = prefs(context)
        // Le coffre : celui qu'on connaît (s'il existe encore), sinon on le cherche parmi les Gists du compte
        val known = p.getString("gist", null)
        val knownContent = known?.let { id -> readOrMissing(token, id) }
        val gistId: String?
        val remote: String?
        if (known != null && knownContent != MISSING) {
            gistId = known
            remote = knownContent
        } else {
            gistId = findGist(token)
            remote = gistId?.let { readGist(token, it) }
        }

        var added = MikenekoBackup.Added(0, 0, 0, 0)
        var theirs: ByteArray? = null
        if (remote != null) {
            val code = key(context) ?: return Status.NeedKey(wrong = false)
            val plain = open(code, remote) ?: return Status.NeedKey(wrong = true)
            theirs = plain
            val content = MikenekoBackup.read(String(plain, Charsets.UTF_8))
            added = withContext(Dispatchers.Main) { MikenekoBackup.import(context, content, sync = true) }
        }
        // Le premier appareil tire la clé
        val code = key(context) ?: newKey().also { saveKey(context, it) }
        // L'empreinte est prise avec ce qu'on écrit : un changement qui arrive pendant l'envoi repartira
        val print = AutoSync.fingerprint(context)
        val ours = ByteArrayOutputStream().also { MikenekoBackup.export(context, it, appVersion(context)) }.toByteArray()
        // Le coffre contient déjà exactement ça (un simple relevé, 2.19.2) : on ne le réécrit pas
        val written = if (gistId != null && theirs != null && theirs.contentEquals(ours)) {
            gistId
        } else {
            val body = seal(code, ours)
            if (gistId != null && writeGistOrMissing(token, gistId, body)) gistId else createGist(token, body)
        }
        val now = System.currentTimeMillis()
        p.edit().putString("gist", written).putLong("derniere", now).apply()
        _lastSync.value = now
        AutoSync.markGitHub(print)
        return Status.Done(added, now)
    }

    // ------------------------------------------------------------------ le profil GitHub

    /** Reprend le nom (sinon l'identifiant) et la photo du compte GitHub dans le profil. */
    suspend fun importProfile(context: Context): Boolean = withContext(Dispatchers.IO) {
        val account = (_login.value as? Login.In)?.account ?: return@withContext false
        runCatching {
            ProfileStore.load(context)
            ProfileStore.setPseudo(context, account.name ?: account.login)
            account.avatarUrl?.let { url ->
                val sized = url + (if ('?' in url) "&" else "?") + "s=512"
                val file = File(context.cacheDir, "github-avatar.img")
                http.newCall(Request.Builder().url(sized).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("photo ${response.code}")
                    file.outputStream().use { out -> response.body!!.byteStream().copyTo(out) }
                }
                ProfileStore.setAvatar(context, Uri.fromFile(file))
                file.delete()
            }
            true
        }.getOrDefault(false)
    }

    // ------------------------------------------------------------------ l'API de GitHub

    private class Unauthorized : IOException("401")
    private class NotFound : IOException("404")

    private fun api(token: String, url: String) = Request.Builder().url(url)
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")

    private fun call(request: Request): String = http.newCall(request).execute().use { response ->
        when {
            response.code == 401 -> throw Unauthorized()
            response.code == 404 -> throw NotFound()
            !response.isSuccessful -> throw IOException("GitHub ${response.code}")
        }
        response.body?.string().orEmpty()
    }

    private fun getJson(token: String, url: String) = JSONObject(call(api(token, url).build()))

    private fun postForm(url: String, fields: Map<String, String>): JSONObject {
        val form = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        return JSONObject(call(Request.Builder().url(url).header("Accept", "application/json").post(form).build()))
    }

    /** Le Gist de Mikeneko parmi ceux du compte (le plus récent), ou null. */
    private fun findGist(token: String): String? {
        var page = 1
        while (page <= 10) {
            val array = org.json.JSONArray(call(api(token, "$API/gists?per_page=100&page=$page").build()))
            for (i in 0 until array.length()) {
                val gist = array.getJSONObject(i)
                if (gist.optJSONObject("files")?.has(FILE) == true) return gist.getString("id")
            }
            if (array.length() < 100) return null
            page++
        }
        return null
    }

    /** Le contenu du fichier du coffre (null : le Gist n'a plus ce fichier). */
    private fun readGist(token: String, id: String): String? {
        val file = getJson(token, "$API/gists/$id").optJSONObject("files")?.optJSONObject(FILE) ?: return null
        // Au-delà de 1 Mo, l'API coupe le contenu : on lit le fichier brut
        return if (file.optBoolean("truncated")) call(api(token, file.getString("raw_url")).build()) else file.getString("content")
    }

    /** Un Gist supprimé entre-temps (sur GitHub) : on en cherchera ou créera un autre. */
    private const val MISSING = "\u0000absent"

    private fun readOrMissing(token: String, id: String): String? =
        try {
            readGist(token, id)
        } catch (_: NotFound) {
            MISSING
        }

    /** false : le Gist n'existe plus. */
    private fun writeGistOrMissing(token: String, id: String, content: String): Boolean =
        try {
            writeGist(token, id, content)
            true
        } catch (_: NotFound) {
            false
        }

    private fun gistBody(content: String) = JSONObject()
        .put("description", DESCRIPTION)
        .put("files", JSONObject().put(FILE, JSONObject().put("content", content)))

    private fun createGist(token: String, content: String): String {
        val json = gistBody(content).put("public", false).toString().toRequestBody(JSON)
        return JSONObject(call(api(token, "$API/gists").post(json).build())).getString("id")
    }

    private fun writeGist(token: String, id: String, content: String) {
        call(api(token, "$API/gists/$id").patch(gistBody(content).toString().toRequestBody(JSON)).build())
    }

    private val JSON = "application/json; charset=utf-8".toMediaType()

    // ------------------------------------------------------------------ le chiffrement du coffre

    private fun newKey(): String = String(CharArray(KEY_LENGTH) { KEY_ALPHABET[random.nextInt(KEY_ALPHABET.length)] })

    /** La clé tapée, sans tirets ni espaces, en majuscules ; null si elle n'a pas la bonne forme. */
    internal fun normalizeKey(typed: String): String? =
        typed.uppercase().filter { it in KEY_ALPHABET }.takeIf { it.length == KEY_LENGTH }

    private fun aesKey(code: String): SecretKeySpec {
        // HKDF-SHA256 (un seul bloc) : la clé de synchro → 256 bits
        val extract = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec("mikeneko-gist".toByteArray(), "HmacSHA256")) }.doFinal(code.toByteArray())
        val okm = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(extract, "HmacSHA256")) }.doFinal("cle|1".toByteArray() + 1.toByte())
        return SecretKeySpec(okm, "AES")
    }

    private val AAD = "mikeneko-gist|1".toByteArray()

    /** gzip, AES-GCM (nonce au hasard), le tout dans un petit JSON. */
    private fun seal(code: String, plain: ByteArray): String {
        val zipped = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(plain) } }.toByteArray()
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey(code), GCMParameterSpec(128, nonce))
        cipher.updateAAD(AAD)
        val sealed = cipher.doFinal(zipped)
        return JSONObject()
            .put("mikeneko", "sync")
            .put("version", 1)
            .put("updated", System.currentTimeMillis())
            .put("data", Base64.encodeToString(nonce + sealed, Base64.NO_WRAP))
            .toString()
    }

    /** null : pas la bonne clé, ou pas un coffre de Mikeneko. */
    private fun open(code: String, text: String): ByteArray? = runCatching {
        val bytes = Base64.decode(JSONObject(text).getString("data"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey(code), GCMParameterSpec(128, bytes, 0, 12))
        cipher.updateAAD(AAD)
        val zipped = cipher.doFinal(bytes, 12, bytes.size - 12)
        GZIPInputStream(ByteArrayInputStream(zipped)).use { it.readBytes() }
    }.getOrNull()

    // ------------------------------------------------------------------ le coffre du téléphone (Keystore)

    /** Le jeton et la clé de synchro sont chiffrés par une clé du Keystore (qui ne sort jamais du téléphone). */
    private fun vaultKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(VAULT_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(VAULT_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private fun lock(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, vaultKey())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }

    /** null : rien, ou plus lisible (sauvegarde restaurée sur un autre téléphone : la clé du Keystore n'y est pas). */
    private fun unlock(stored: String?): String? = stored?.let {
        runCatching {
            val bytes = Base64.decode(it, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, vaultKey(), GCMParameterSpec(128, bytes, 0, 12))
            String(cipher.doFinal(bytes, 12, bytes.size - 12))
        }.getOrNull()
    }

    private fun token(context: Context) = unlock(prefs(context).getString("jeton", null))
    private fun saveToken(context: Context, token: String) = prefs(context).edit().putString("jeton", lock(token)).apply()
    private fun key(context: Context) = unlock(prefs(context).getString("cle", null))
    private fun saveKey(context: Context, code: String) = prefs(context).edit().putString("cle", lock(code)).apply()

    private fun appVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
}
