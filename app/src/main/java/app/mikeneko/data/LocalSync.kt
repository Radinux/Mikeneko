package app.mikeneko.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * La synchro entre Mikeneko sur un même réseau privé (2.17.0), sans compte ni serveur.
 *
 * - **Lier deux appareils** : l'un affiche un PIN tiré au hasard ([showPin]), l'autre le tape
 *   ([joinWithPin]). Ils gardent alors un secret commun : ils sont **liés** ([links]).
 * - **Ensuite, tout seuls** : tant que l'appli tourne, chaque appareil lié ouvre sa porte et
 *   s'annonce sur le réseau ([start]) ; quand deux appareils liés se retrouvent sur le même réseau
 *   (n'importe lequel : maison, partage de connexion…), ils se resynchronisent sans PIN, puis
 *   régulièrement tant qu'ils restent ensemble. « Arrêter la synchro » ([unlink]) défait le lien ;
 *   l'autre appareil le défait aussi à la rencontre suivante.
 * - **Ce qui passe** : chacun envoie sa sauvegarde (celle de Réglages › Sauvegarde, [MikenekoBackup])
 *   et fusionne celle de l'autre sans rien effacer.
 *
 * L'idée vient de LocalSend (projet libre) : s'annoncer sur le réseau local et se parler directement.
 *
 * Sécurité : une clé d'échange à usage unique (ECDH P-256) de chaque côté ; la clé de chiffrement
 * (AES-GCM) vient du secret commun de l'échange ET du PIN (ou, une fois liés, du secret gardé).
 * Quelqu'un qui écoute le réseau ne lit rien ; un mauvais PIN ne déchiffre rien ; 5 essais ratés et un
 * nouveau PIN est tiré. Le seul risque connu : pendant la minute de l'appairage, un attaquant actif
 * placé entre les deux appareils pourrait deviner le PIN (comme tout appairage par code court sans
 * échange spécial, dit PAKE). Une fois liés, plus de PIN : le secret gardé fait 256 bits.
 */
object LocalSync {
    private const val SERVICE_TYPE = "_mikeneko._tcp."
    private const val PROTOCOL = 2
    private const val DOOR_PORT = 53_318
    private val GOODBYE = "mikeneko-adieu".toByteArray()
    private const val MAX_FRAME = 64 * 1024 * 1024
    private const val MAX_TRIES = 5
    /** Deux appareils liés ensemble : pas plus d'une synchro toutes les 5 min, et une toutes les 15 min. */
    private const val MIN_GAP_MS = 5 * 60_000L
    private const val PERIOD_MS = 15 * 60_000L

    /** Un Mikeneko trouvé sur le réseau. [waitingPin] : il affiche un PIN en ce moment. */
    data class Peer(val id: String, val name: String, val host: String, val port: Int, val waitingPin: Boolean)

    /**
     * Un appareil lié. [lastSync] : la dernière synchro réussie (ms), 0 si jamais. [host] / [port] : où on
     * l'a joint la dernière fois (essayé quand la découverte ne le montre pas : réseaux qui bloquent mDNS).
     */
    data class Link(val id: String, val name: String, val secret: String, val lastSync: Long, val host: String? = null, val port: Int = 0)

    sealed interface State {
        data object Idle : State
        /** On attend l'autre appareil : il doit taper [pin]. [address] : à taper à la main si la liste ne le montre pas. */
        data class Waiting(val pin: String, val name: String, val address: String?) : State
        data object Syncing : State
        /** Ce que la synchro a apporté ici. */
        data class Done(val added: MikenekoBackup.Added, val peer: String) : State
        data class Failed(val reason: Reason) : State
    }

    enum class Reason { WrongPin, Network }

    private val _state = MutableStateFlow<State>(State.Idle)
    /** L'appairage par PIN en cours (l'écran de synchro). */
    val state: StateFlow<State> = _state.asStateFlow()

    private val _links = MutableStateFlow<List<Link>>(emptyList())
    val links: StateFlow<List<Link>> = _links.asStateFlow()

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    /** Les Mikeneko vus sur le réseau en ce moment. */
    val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val random = SecureRandom()
    private val syncing = Mutex()
    private var appContext: Context? = null
    private var server: ServerSocket? = null
    private var serverJob: Job? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var loopJob: Job? = null
    /** Le PIN affiché en ce moment (null : on n'appaire pas). */
    @Volatile private var pin: String? = null
    private var wrongTries = 0
    /** Ce qui garde la porte ouverte : des appareils liés, l'écran de synchro. */
    private var screenOpen = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("synchro", Context.MODE_PRIVATE)

    /** L'identifiant de cet appareil pour la synchro (tiré une fois). */
    fun deviceId(context: Context): String {
        val p = prefs(context)
        return p.getString("id", null) ?: UUID.randomUUID().toString().also { p.edit().putString("id", it).apply() }
    }

    /** Le nom de cet appareil, montré à l'autre : le pseudo du profil (2.18.0), sinon le modèle du téléphone. */
    fun deviceName(): String =
        ProfileStore.profile.value.pseudo.takeIf { it.isNotBlank() }
            ?: Build.MODEL?.takeIf { it.isNotBlank() } ?: "Mikeneko"

    private fun loadLinks(context: Context) {
        _links.value = runCatching {
            val array = JSONArray(prefs(context).getString("liens", "[]"))
            (0 until array.length()).map {
                val o = array.getJSONObject(it)
                Link(o.getString("id"), o.optString("nom"), o.getString("secret"), o.optLong("derniere"), o.optString("hote").ifEmpty { null }, o.optInt("port"))
            }
        }.getOrDefault(emptyList())
    }

    private fun saveLinks(context: Context, list: List<Link>) {
        _links.value = list
        val array = JSONArray()
        list.forEach {
            array.put(JSONObject().put("id", it.id).put("nom", it.name).put("secret", it.secret).put("derniere", it.lastSync).put("hote", it.host ?: "").put("port", it.port))
        }
        prefs(context).edit().putString("liens", array.toString()).apply()
    }

    private fun upsertLink(context: Context, link: Link) =
        synchronized(this) { saveLinks(context, _links.value.filterNot { it.id == link.id } + link) }

    private fun touchLink(context: Context, id: String, host: String?, port: Int) = synchronized(this) {
        saveLinks(context, _links.value.map {
            if (it.id != id) it else it.copy(lastSync = System.currentTimeMillis(), host = host ?: it.host, port = if (host != null) port else it.port)
        })
    }

    /**
     * « Arrêter la synchro » avec cet appareil. S'il est joignable, on le prévient tout de suite (un
     * « au revoir » chiffré avec le secret du lien : seul le vrai partenaire peut le dire) ; sinon il le
     * saura à la rencontre suivante.
     */
    fun unlink(context: Context, id: String) {
        val app = context.applicationContext
        val link = _links.value.firstOrNull { it.id == id }
        val peer = _peers.value.firstOrNull { it.id == id }
            ?: link?.host?.takeIf { link.port > 0 }?.let { Peer(link.id, link.name, it, link.port, waitingPin = false) }
        forget(app, id)
        if (link != null && peer != null) {
            scope.launch {
                runCatching { connect(app, peer.host, peer.port) { exchange(app, it, asHost = false, mode = Mode.Goodbye(link)) } }
            }
        }
    }

    private fun forget(context: Context, id: String) {
        synchronized(this) { saveLinks(context, _links.value.filterNot { it.id == id }) }
        refresh(context)
    }

    // ------------------------------------------------------------------ cycle de vie

    /** Au démarrage de l'appli : s'il y a des appareils liés, on ouvre la porte et on les cherche. */
    fun start(context: Context) {
        val app = context.applicationContext
        appContext = app
        ProfileStore.load(app) // le pseudo est le nom montré aux autres
        loadLinks(app)
        refresh(app)
        // Un autre réseau (une autre box, un partage de connexion…) : on s'y annonce et on y cherche à nouveau
        runCatching {
            app.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) = restartSoon(app)
                override fun onLinkPropertiesChanged(network: android.net.Network, properties: android.net.LinkProperties) = restartSoon(app)
            })
        }
    }

    private var restartJob: Job? = null

    private fun restartSoon(context: Context) {
        restartJob?.cancel()
        restartJob = scope.launch {
            delay(2_000) // le temps que le réseau soit vraiment là
            stopBrowsing(context)
            unannounce(context)
            refresh(context)
        }
    }

    /** Ouvre ou ferme la porte, l'annonce et la recherche selon ce qui en a besoin. */
    private fun refresh(context: Context) {
        val needed = _links.value.isNotEmpty() || screenOpen
        if (needed) {
            openDoor(context)
            announce(context)
            browse(context)
            if (loopJob?.isActive != true) loopJob = scope.launch { autoLoop(context) }
        } else {
            loopJob?.cancel()
            loopJob = null
            stopBrowsing(context)
            unannounce(context)
            closeDoor()
        }
    }

    /** L'écran de synchro est ouvert : on se montre et on cherche, même sans appareil lié. */
    fun screenOpened(context: Context) {
        screenOpen = true
        appContext = context.applicationContext
        loadLinks(context)
        refresh(context.applicationContext)
    }

    fun screenClosed(context: Context) {
        screenOpen = false
        pin = null
        _state.value = State.Idle
        refresh(context.applicationContext)
    }

    // ------------------------------------------------------------------ appairage par PIN

    private fun newPin(): String = (random.nextInt(900_000) + 100_000).toString()

    /** Cet appareil attend l'autre : un PIN, valable tant que l'écran est ouvert. */
    fun showPin(context: Context) {
        val app = context.applicationContext
        screenOpened(app)
        wrongTries = 0
        val code = newPin()
        pin = code
        _state.value = State.Waiting(code, deviceName(), localAddress(app)?.let { a -> server?.localPort?.let { "$a:$it" } })
        announce(app) // « j'attends un PIN » dans l'annonce
    }

    /** Se connecte à l'autre appareil avec le PIN qu'il affiche. */
    fun joinWithPin(context: Context, host: String, port: Int, code: String) {
        val app = context.applicationContext
        _state.value = State.Syncing
        scope.launch {
            val outcome = syncing.withLock {
                runCatching { connect(app, host, port) { exchange(app, it, asHost = false, mode = Mode.Pin(code.trim()), via = host to port) } }.getOrElse { Outcome.Error }
            }
            when (outcome) {
                is Outcome.Synced -> _state.value = State.Done(outcome.added, outcome.peer)
                Outcome.WrongPin -> _state.value = State.Failed(Reason.WrongPin)
                else -> _state.value = State.Failed(Reason.Network)
            }
        }
    }

    // ------------------------------------------------------------------ appareils liés

    /**
     * Synchronise maintenant avec l'appareil lié [id] : là où la découverte le voit, sinon à sa dernière
     * adresse connue. Renvoie false si on ne sait pas où le trouver.
     */
    fun syncNow(context: Context, id: String): Boolean {
        val link = _links.value.firstOrNull { it.id == id } ?: return false
        val peer = _peers.value.firstOrNull { it.id == id }
            ?: link.host?.takeIf { link.port > 0 }?.let { Peer(link.id, link.name, it, link.port, waitingPin = false) }
            ?: return false
        scope.launch { syncLinked(context.applicationContext, peer) }
        return true
    }

    /**
     * Quelque chose a changé ici (2.19.2, [AutoSync]) : une synchro avec chaque appareil lié présent sur le
     * réseau (pas plus d'une toutes les 15 s avec le même).
     */
    fun syncPresent(context: Context) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        _peers.value.forEach { peer ->
            val link = _links.value.firstOrNull { it.id == peer.id } ?: return@forEach
            if (now - link.lastSync > 15_000) scope.launch { syncLinked(app, peer) }
        }
    }

    /** Les appareils liés vus sur le réseau : une synchro quand on les retrouve, puis toutes les 15 min. */
    private suspend fun autoLoop(context: Context) {
        while (true) {
            val me = deviceId(context)
            val now = System.currentTimeMillis()
            for (peer in _peers.value) {
                val link = _links.value.firstOrNull { it.id == peer.id } ?: continue
                val since = now - link.lastSync
                // Un seul des deux lance la synchro (le plus petit identifiant) ; l'autre en secours
                val turn = me < peer.id || since > 2 * PERIOD_MS
                if (turn && since > MIN_GAP_MS && (link.lastSync == 0L || since > PERIOD_MS || peerJustSeen(peer.id))) syncLinked(context, peer)
            }
            seenNow.clear()
            delay(30_000)
        }
    }

    /** Les appareils qui viennent d'apparaître sur le réseau (on vient de se retrouver). */
    private val seenNow = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private fun peerJustSeen(id: String) = id in seenNow

    private suspend fun syncLinked(context: Context, peer: Peer) {
        val link = _links.value.firstOrNull { it.id == peer.id } ?: return
        syncing.withLock {
            val outcome = runCatching { connect(context, peer.host, peer.port) { exchange(context, it, asHost = false, mode = Mode.Linked(link), via = peer.host to peer.port) } }
                .getOrElse { Outcome.Error }
            if (outcome == Outcome.Unknown) forget(context, peer.id) // l'autre a coupé la synchro
        }
    }

    // ------------------------------------------------------------------ la porte (serveur)

    private fun openDoor(context: Context) {
        if (server?.isClosed == false) return
        // Toujours la même porte si elle est libre (une adresse tapée à la main reste bonne), sinon une au hasard
        val socket = runCatching { ServerSocket(DOOR_PORT) }.getOrNull() ?: runCatching { ServerSocket(0) }.getOrNull() ?: return
        server = socket
        serverJob = scope.launch {
            while (isActive && !socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                // (sans le verrou des synchros sortantes : si les deux appareils se lançaient en même
                // temps, chacun attendrait l'autre jusqu'au délai d'attente)
                launch {
                    client.use {
                        it.soTimeout = 60_000
                        val outcome = runCatching { exchange(context, it, asHost = true, mode = null) }.getOrElse { Outcome.Error }
                        onHosted(context, outcome)
                    }
                }
            }
        }
    }

    private fun onHosted(context: Context, outcome: Outcome) {
        when (outcome) {
            is Outcome.Synced -> if (outcome.byPin) {
                pin = null
                _state.value = State.Done(outcome.added, outcome.peer)
                announce(context)
            }
            Outcome.WrongPin -> {
                val code = pin ?: return
                // Trop d'essais : un nouveau PIN (on ne devine pas un code qui change)
                if (++wrongTries >= MAX_TRIES) {
                    wrongTries = 0
                    pin = newPin()
                }
                val shown = _state.value as? State.Waiting
                _state.value = State.Waiting(pin ?: code, deviceName(), shown?.address)
            }
            else -> Unit
        }
    }

    private fun closeDoor() {
        serverJob?.cancel()
        serverJob = null
        runCatching { server?.close() }
        server = null
    }

    // ------------------------------------------------------------------ l'annonce et la recherche (mDNS)

    private fun nsd(context: Context) = context.getSystemService(NsdManager::class.java)

    private fun announce(context: Context) {
        val port = server?.localPort ?: return
        val manager = nsd(context) ?: return
        registration?.let { runCatching { manager.unregisterService(it) } }
        val id = deviceId(context)
        val info = NsdServiceInfo().apply {
            serviceName = "mikeneko-" + id.take(8)
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("id", id)
            setAttribute("nom", deviceName())
            setAttribute("pin", if (pin != null) "1" else "0")
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) = Unit
        }
        registration = listener
        runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    private fun unannounce(context: Context) {
        registration?.let { listener -> runCatching { nsd(context)?.unregisterService(listener) } }
        registration = null
    }

    private fun browse(context: Context) {
        if (discovery != null) return
        val manager = nsd(context) ?: return
        val me = deviceId(context)
        val pending = ArrayDeque<NsdServiceInfo>()
        var resolving = false
        val byService = mutableMapOf<String, Peer>()

        fun publish() {
            _peers.value = synchronized(byService) { byService.values.toList() }
        }

        fun next() {
            if (resolving) return
            val info = synchronized(pending) { pending.removeFirstOrNull() } ?: return
            resolving = true
            @Suppress("DEPRECATION")
            manager.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, error: Int) {
                    resolving = false
                    next()
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    resolving = false
                    @Suppress("DEPRECATION")
                    val host = info.host?.hostAddress
                    val attributes = info.attributes
                    val id = attributes["id"]?.let { String(it) }
                    if (host != null && id != null && id != me) {
                        val peer = Peer(id, attributes["nom"]?.let { String(it) } ?: info.serviceName, host, info.port, attributes["pin"]?.let { String(it) } == "1")
                        val known = synchronized(byService) { byService.put(info.serviceName, peer) }
                        if (known == null) seenNow.add(id)
                        publish()
                    }
                    next()
                }
            })
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, error: Int) = Unit
            override fun onStopDiscoveryFailed(type: String, error: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                synchronized(pending) { pending.addLast(info) }
                next()
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                synchronized(byService) { byService.remove(info.serviceName) }
                publish()
            }
        }
        discovery = listener
        runCatching { manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { discovery = null }
    }

    private fun stopBrowsing(context: Context) {
        discovery?.let { listener -> runCatching { nsd(context)?.stopServiceDiscovery(listener) } }
        discovery = null
        _peers.value = emptyList()
    }

    /** L'adresse IPv4 de cet appareil sur le réseau local (pour la taper à la main). */
    private fun localAddress(context: Context): String? = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return null
        manager.getLinkProperties(network)?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }.getOrNull()

    // ------------------------------------------------------------------ l'échange

    private sealed interface Mode {
        data class Pin(val code: String) : Mode
        data class Linked(val link: Link) : Mode
        /** On coupe la synchro : on le dit à l'autre, sans rien échanger. */
        data class Goodbye(val link: Link) : Mode
    }

    private sealed interface Outcome {
        data class Synced(val added: MikenekoBackup.Added, val peer: String, val byPin: Boolean) : Outcome
        data object WrongPin : Outcome
        /** L'autre ne nous connaît plus (il a coupé la synchro). */
        data object Unknown : Outcome
        data object Error : Outcome
    }

    private suspend fun <T> connect(context: Context, host: String, port: Int, block: suspend (Socket) -> T): T =
        withContext(Dispatchers.IO) {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 8_000)
                socket.soTimeout = 60_000
                block(socket)
            }
        }

    private class Channel(socket: Socket) {
        val input = DataInputStream(socket.getInputStream().buffered())
        val output = DataOutputStream(socket.getOutputStream().buffered())

        fun send(bytes: ByteArray) {
            output.writeInt(bytes.size)
            output.write(bytes)
            output.flush()
        }

        fun receive(): ByteArray {
            val size = input.readInt()
            require(size in 0..MAX_FRAME) { "taille" }
            return ByteArray(size).also { input.readFully(it) }
        }
    }

    /** Le bonjour : « mikeneko-sync », version, façon (pin / lien), identifiant, nom, puis la clé publique. */
    private class Hello(val mode: String, val id: String, val name: String, val key: ByteArray)

    private fun hello(mode: String, id: String, key: ByteArray): ByteArray {
        val header = listOf("mikeneko-sync", PROTOCOL.toString(), mode, id, Base64.encodeToString(deviceName().toByteArray(), Base64.NO_WRAP))
            .joinToString("|") + "|"
        return header.toByteArray(Charsets.US_ASCII) + key
    }

    private fun parse(bytes: ByteArray): Hello? {
        // L'en-tête est en ASCII (le nom est en base64) : 5 champs, puis la clé
        var bars = 0
        var end = -1
        for (i in bytes.indices) {
            if (bytes[i] == '|'.code.toByte() && ++bars == 5) {
                end = i
                break
            }
        }
        if (end < 0) return null
        val parts = String(bytes, 0, end, Charsets.US_ASCII).split('|')
        if (parts.size != 5 || parts[0] != "mikeneko-sync" || parts[1].toIntOrNull() != PROTOCOL) return null
        val name = runCatching { String(Base64.decode(parts[4], Base64.NO_WRAP)) }.getOrDefault("Mikeneko")
        return Hello(parts[2], parts[3], name, bytes.copyOfRange(end + 1, bytes.size))
    }

    /**
     * 1. bonjour dans les deux sens (celui qui se connecte dit s'il vient avec un PIN ou un lien) ;
     * 2. la clé de chiffrement = HKDF(secret ECDH, PIN ou secret du lien) ; celui qui se connecte envoie
     *    une preuve chiffrée, l'autre répond « ok » chiffré (sinon « non » ou « inconnu », en clair) ;
     * 3. les deux sauvegardes, chiffrées, puis chacun fusionne. Par PIN, les deux gardent un lien.
     */
    private suspend fun exchange(context: Context, socket: Socket, asHost: Boolean, mode: Mode?, via: Pair<String, Int>? = null): Outcome {
        val channel = Channel(socket)
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val mine = keys.public.encoded
        val me = deviceId(context)

        val peer: Hello
        val secretInput: String
        val byPin: Boolean
        if (asHost) {
            peer = parse(channel.receive()) ?: return Outcome.Error
            channel.send(hello("hote", me, mine))
            when (peer.mode) {
                "pin" -> {
                    secretInput = "pin|" + (pin ?: run { channel.send("non".toByteArray()); return Outcome.Error })
                    byPin = true
                }
                "lien" -> {
                    val link = _links.value.firstOrNull { it.id == peer.id }
                        ?: run { channel.send("inconnu".toByteArray()); return Outcome.Error }
                    secretInput = "lien|" + link.secret
                    byPin = false
                }
                else -> return Outcome.Error
            }
        } else {
            val kind = if (mode is Mode.Pin) "pin" else "lien"
            channel.send(hello(kind, me, mine))
            peer = parse(channel.receive()) ?: return Outcome.Error
            secretInput = when (mode) {
                is Mode.Pin -> "pin|" + mode.code
                is Mode.Linked -> "lien|" + mode.link.secret
                is Mode.Goodbye -> "lien|" + mode.link.secret
                null -> return Outcome.Error
            }
            byPin = mode is Mode.Pin
        }

        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(keys.private)
        agreement.doPhase(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(peer.key)), true)
        val shared = agreement.generateSecret()
        // Le sel : les deux clés publiques dans un ordre fixe (celle de l'hôte d'abord)
        val salt = MessageDigest.getInstance("SHA-256").digest(if (asHost) mine + peer.key else peer.key + mine)
        val crypto = Crypto(hkdf(shared, salt, "mikeneko-sync|$secretInput".toByteArray(), 32), asHost)

        if (asHost) {
            val proof = crypto.open(channel.receive())
            if (proof == null || String(proof) != "preuve") {
                channel.send("non".toByteArray())
                return if (byPin) Outcome.WrongPin else Outcome.Error
            }
            channel.send(crypto.seal("ok".toByteArray()))
            if (byPin) _state.value = State.Syncing
        } else {
            channel.send(crypto.seal("preuve".toByteArray()))
            val answer = channel.receive()
            if (String(answer) == "inconnu") return Outcome.Unknown
            if (crypto.open(answer)?.let(::String) != "ok") return if (byPin) Outcome.WrongPin else Outcome.Error
        }

        if (mode is Mode.Goodbye) {
            channel.send(crypto.seal(GOODBYE))
            return Outcome.Error
        }

        // Les sauvegardes : celui qui se connecte envoie la sienne d'abord
        val ours = ByteArrayOutputStream().also { MikenekoBackup.export(context, it, appVersion(context)) }.toByteArray()
        val received: ByteArray
        if (asHost) {
            received = crypto.open(channel.receive()) ?: return Outcome.Error
            // L'autre a coupé la synchro : on défait le lien aussi
            if (!byPin && received.contentEquals(GOODBYE)) {
                forget(context, peer.id)
                return Outcome.Error
            }
            channel.send(crypto.seal(ours))
        } else {
            channel.send(crypto.seal(ours))
            received = crypto.open(channel.receive()) ?: return Outcome.Error
        }
        // En plus (2.19.0) : la clé du coffre GitHub, pour que l'autre synchronise aussi hors du réseau.
        // Un Mikeneko plus ancien ne l'envoie ni ne la lit : on passe sans rien dire.
        runCatching {
            channel.send(crypto.seal(JSONObject().put("cle", GitHubSync.keyToShare(context) ?: "").toString().toByteArray()))
            val extras = crypto.open(channel.receive())?.let { JSONObject(String(it)) }
            GitHubSync.receiveKey(context, extras?.optString("cle")?.ifEmpty { null })
        }
        val content = MikenekoBackup.read(String(received, Charsets.UTF_8))
        val added = withContext(Dispatchers.Main) { MikenekoBackup.import(context, content, sync = true) }

        // Appairés par PIN : les deux gardent le même secret (tiré de cet échange), et seront liés
        if (byPin) {
            val secret = Base64.encodeToString(hkdf(shared, salt, "mikeneko-lien|$secretInput".toByteArray(), 32), Base64.NO_WRAP)
            upsertLink(context, Link(peer.id, peer.name, secret, System.currentTimeMillis(), via?.first, via?.second ?: 0))
        } else {
            touchLink(context, peer.id, via?.first, via?.second ?: 0)
        }
        // Ce qu'on a maintenant, l'autre l'a aussi : pas de synchro « en retour » pour ce qu'on vient de recevoir
        AutoSync.markLocal(context)
        return Outcome.Synced(added, peer.name, byPin)
    }

    private fun appVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /** AES-GCM, un compteur par sens (l'hôte et l'autre n'utilisent jamais le même nonce). */
    private class Crypto(key: ByteArray, asHost: Boolean) {
        private val secret = SecretKeySpec(key, "AES")
        private val sendDirection: Byte = if (asHost) 1 else 2
        private val receiveDirection: Byte = if (asHost) 2 else 1
        private var sent = 0L
        private var received = 0L

        private fun nonce(direction: Byte, counter: Long) = ByteArray(12).also {
            it[0] = direction
            for (i in 0 until 8) it[4 + i] = (counter ushr (8 * (7 - i))).toByte()
        }

        fun seal(plain: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(128, nonce(sendDirection, sent++)))
            return cipher.doFinal(plain)
        }

        /** null : pas chiffré avec la même clé (mauvais PIN), ou abîmé. */
        fun open(sealed: ByteArray): ByteArray? = runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, nonce(receiveDirection, received)))
            cipher.doFinal(sealed).also { received++ }
        }.getOrNull()
    }

    /** HKDF-SHA256 (RFC 5869). */
    private fun hkdf(input: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val extract = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(salt, "HmacSHA256")) }.doFinal(input)
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(extract, "HmacSHA256")) }
        val out = ByteArrayOutputStream()
        var previous = ByteArray(0)
        var counter = 1
        while (out.size() < length) {
            mac.reset()
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()
            out.write(previous)
            counter++
        }
        return out.toByteArray().copyOf(length)
    }
}
