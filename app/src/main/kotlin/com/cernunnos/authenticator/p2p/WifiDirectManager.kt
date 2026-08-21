package com.cernunnos.authenticator.p2p

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import com.cernunnos.authenticator.R
import com.cernunnos.authenticator.util.IOUtils
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

/**
 * Wi-Fi Direct P2P manager for transferring encrypted TOTP files
 * between two devices without any server.
 */
class WifiDirectManager(private val context: Context) {

    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null

    // Track active transfer threads so they can be interrupted on cleanup
    private var sendThread: Thread? = null
    private var receiveThread: Thread? = null

    private var peerListListener: ((List<WifiP2pDevice>) -> Unit)? = null
    private var connectionListener: ((WifiP2pInfo) -> Unit)? = null

    var isInitialized = false
        private set

    fun initialize() {
        if (manager == null) return
        channel = manager.initialize(context, context.mainLooper, null)
        isInitialized = true

        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(
                            WifiP2pManager.EXTRA_WIFI_STATE,
                            WifiP2pManager.WIFI_P2P_STATE_DISABLED,
                        )
                        Log.d("WifiDirect", "P2P state: $state")
                    }
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        manager.requestPeers(channel) { peerList ->
                            peerListListener?.invoke(peerList.deviceList.toList())
                        }
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        manager.requestConnectionInfo(channel) { info ->
                            connectionListener?.invoke(info)
                        }
                    }
                }
            }
        }
        context.registerReceiver(receiver, filter)
    }

    fun setPeerListListener(listener: (List<WifiP2pDevice>) -> Unit) {
        peerListListener = listener
    }

    fun setConnectionListener(listener: (WifiP2pInfo) -> Unit) {
        connectionListener = listener
    }

    fun discoverPeers(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (!isInitialized) {
            onError(context.getString(R.string.p2p_not_initialized))
            return
        }
        manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = onSuccess()
            override fun onFailure(reason: Int) {
                onError(context.getString(R.string.p2p_discovery_failed, reason))
            }
        })
    }

    fun connect(device: WifiP2pDevice, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (!isInitialized) {
            onError(context.getString(R.string.p2p_not_initialized))
            return
        }
        try {
            val mac = android.net.MacAddress.fromString(device.deviceAddress)
            val config = WifiP2pConfig.Builder()
                .setDeviceAddress(mac)
                .build()
            manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() = onSuccess()
                override fun onFailure(reason: Int) {
                    onError(context.getString(R.string.p2p_connect_failed, reason))
                }
            })
        } catch (e: Exception) {
            onError(context.getString(R.string.p2p_invalid_device, e.message ?: ""))
        }
    }

    /**
     * Magic byte sequence used as a handshake to authenticate the peer.
     * The receiver sends this sequence and the sender must echo it back
     * before any data transfer begins. This prevents random/unrelated
     * devices from injecting or receiving data on the well-known port.
     */
    private val MAGIC_HANDSHAKE = byteArrayOf(0xC0.toByte(), 0xC0.toByte(), 0x72.toByte(), 0x6E.toByte(), 0x75.toByte(), 0x6E.toByte(), 0x6E.toByte(), 0x6F.toByte(), 0x73.toByte())

    /**
     * Well-known port for the app's Wi-Fi Direct P2P transfer feature.
     * Both the sender and receiver use this fixed port so they can find
     * each other without an out-of-band port negotiation channel.
     */
    private val P2P_PORT = 8888
    private val SOCKET_TIMEOUT_MS = 30_000 // 30 seconds — prevents indefinite blocking

    /**
     * Send encrypted data. Client connects to group owner on the well-known P2P port.
     *
     * Data is already encrypted by the caller (ExportImport.export). The transfer
     * itself is over a raw socket, but the payload is encrypted at the application
     * layer, so a network eavesdropper cannot read the TOTP secrets.
     *
     * A simple magic-byte handshake is performed before the payload is sent to
     * ensure the peer is another Cernunnos instance (not an arbitrary device).
     */
    fun sendEncryptedData(info: WifiP2pInfo, data: String, onSent: () -> Unit, onError: (String) -> Unit) {
        sendThread = Thread {
            try {
                val socket = if (info.isGroupOwner) {
                    val serverSocket = ServerSocket(P2P_PORT)
                    serverSocket.soTimeout = SOCKET_TIMEOUT_MS
                    serverSocket.accept().also { serverSocket.close() }
                } else {
                    Socket(info.groupOwnerAddress, P2P_PORT)
                }
                socket.soTimeout = SOCKET_TIMEOUT_MS
                socket.use {
                    val out: OutputStream = it.getOutputStream()
                    val input: InputStream = it.getInputStream()
                    // Handshake: receiver sends magic bytes, sender echoes them back.
                    val handshake = ByteArray(MAGIC_HANDSHAKE.size)
                    val read = input.read(handshake)
                    if (read != MAGIC_HANDSHAKE.size || !handshake.contentEquals(MAGIC_HANDSHAKE)) {
                        onError(context.getString(R.string.p2p_handshake_failed))
                        return@use
                    }
                    out.write(MAGIC_HANDSHAKE)
                    out.flush()
                    // Send the (already encrypted) payload.
                    out.write(data.toByteArray())
                    out.flush()
                }
                onSent()
            } catch (e: Exception) {
                onError(context.getString(R.string.p2p_send_failed, e.message ?: ""))
            } finally {
                sendThread = null
            }
        }.also { it.isDaemon = true }
        sendThread?.start()
    }

    /**
     * Receive encrypted data. Server socket on the well-known P2P port.
     *
     * The receiver initiates the magic-byte handshake and verifies the echo
     * before reading the payload, rejecting connections from non-Cernunnos
     * devices.
     */
    fun receiveEncryptedData(onReceived: (String) -> Unit, onError: (String) -> Unit) {
        receiveThread = Thread {
            var serverSocket: ServerSocket? = null
            try {
                serverSocket = ServerSocket(P2P_PORT)
                serverSocket.soTimeout = SOCKET_TIMEOUT_MS
                val socket = serverSocket.accept()
                socket.soTimeout = SOCKET_TIMEOUT_MS
                socket.use {
                    val out: OutputStream = it.getOutputStream()
                    val input: InputStream = it.getInputStream()
                    // Initiate handshake: send magic bytes and expect them echoed back.
                    out.write(MAGIC_HANDSHAKE)
                    out.flush()
                    val echo = ByteArray(MAGIC_HANDSHAKE.size)
                    val read = input.read(echo)
                    if (read != MAGIC_HANDSHAKE.size || !echo.contentEquals(MAGIC_HANDSHAKE)) {
                        onError(context.getString(R.string.p2p_handshake_failed))
                        return@use
                    }
                    val text = input.use { String(IOUtils.readBounded(it, IOUtils.MAX_P2P_BYTES), Charsets.UTF_8) }
                    onReceived(text)
                }
            } catch (e: Exception) {
                onError(context.getString(R.string.p2p_receive_failed, e.message ?: ""))
            } finally {
                try { serverSocket?.close() } catch (_: Exception) {}
                receiveThread = null
            }
        }.also { it.isDaemon = true }
        receiveThread?.start()
    }

    fun cleanup() {
        // Interrupt active transfer threads
        sendThread?.interrupt()
        receiveThread?.interrupt()
        sendThread = null
        receiveThread = null
        // Stop Wi-Fi Direct discovery and remove any active group to save battery
        try { manager?.stopPeerDiscovery(channel, null) } catch (_: Exception) {}
        try { manager?.removeGroup(channel, null) } catch (_: Exception) {}
        receiver?.let { context.unregisterReceiver(it) }
        receiver = null
        isInitialized = false
    }
}
