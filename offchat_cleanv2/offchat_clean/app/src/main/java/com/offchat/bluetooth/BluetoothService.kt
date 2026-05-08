package com.offchat.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.offchat.MainActivity
import com.offchat.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

private const val PKT_ANNOUNCE:   Byte = 0x01
private const val PKT_MESSAGE:    Byte = 0x02
private const val PKT_ATTENDANCE: Byte = 0x03
private const val PKT_ACK:        Byte = 0x04
private const val TTL_MAX: Byte        = 7
private const val HDR                  = 18   // type(1)+ttl(1)+senderID(8)+msgID(8)
private const val CHUNK_SIZE           = 490  // safe BLE write size after MTU 517
private const val MAX_QUEUE            = 50
private const val QUEUE_TTL_MS         = 30 * 60 * 1000L

private data class Packet(val type: Byte, val ttl: Byte, val senderID: Long, val msgID: Long, val payload: ByteArray) {
    fun encode(): ByteArray = ByteBuffer.allocate(HDR + payload.size).also {
        it.put(type); it.put(ttl); it.putLong(senderID); it.putLong(msgID); it.put(payload)
    }.array()
    companion object {
        fun decode(b: ByteArray): Packet? {
            if (b.size < HDR) return null
            val buf = ByteBuffer.wrap(b)
            return Packet(buf.get(), buf.get(), buf.getLong(), buf.getLong(),
                ByteArray(b.size - HDR).also { buf.get(it) })
        }
    }
}

private data class QueuedMessage(val data: ByteArray, val queuedAt: Long = System.currentTimeMillis()) {
    fun isExpired() = System.currentTimeMillis() - queuedAt > QUEUE_TTL_MS
}

data class AnnouncePayload(val roll: String, val name: String, val role: String, val year: String, val branch: String, val dept: String)
data class MsgPayload(val id: String, val senderRoll: String, val senderName: String, val channel: String, val content: String, val ts: Long)
data class AttPayload(val studentRoll: String, val studentName: String, val subject: String, val date: String, val present: Boolean, val teacherRoll: String)
data class AckPayload(val msgId: String)

class BluetoothService : Service() {

    companion object {
        private const val TAG = "OffChatMesh"
        private const val CH  = "offchat_mesh"
        private const val NID = 1001

        val SVC_UUID:  UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
        val RX_UUID:   UUID = UUID.fromString("fa87c0d1-afac-11de-8a39-0800200c9a66")
        val TX_UUID:   UUID = UUID.fromString("fa87c0d2-afac-11de-8a39-0800200c9a66")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        var myRoll = ""; var myName = ""; var myRole = UserRole.STUDENT
        var myYear = ""; var myBranch = ""; var myDept = ""

        val peers       = MutableStateFlow<Map<String, Peer>>(emptyMap())
        val incomingMsg = MutableStateFlow<MsgPayload?>(null)
        val incomingAtt = MutableStateFlow<AttPayload?>(null)
        val deliveryAck = MutableStateFlow<String?>(null)
        var isRunning = false

        private var inst: BluetoothService? = null
        fun sendMsg(p: MsgPayload) { inst?.enqueue(PKT_MESSAGE, p) }
        fun sendAtt(p: AttPayload) { inst?.enqueue(PKT_ATTENDANCE, p) }
    }

    private val gson      = Gson()
    private val scope     = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rng       = Random()
    private val sessionID = rng.nextLong()

    private var btMgr: BluetoothManager? = null
    private var btAdp: BluetoothAdapter? = null
    private var gattServer: BluetoothGattServer? = null
    private var txChar: BluetoothGattCharacteristic? = null

    private val subscribedAddrs  = ConcurrentHashMap.newKeySet<String>()
    private val gattClients      = ConcurrentHashMap<String, BluetoothGatt>()
    private val mtuMap           = ConcurrentHashMap<String, Int>()
    private val connectingSet    = ConcurrentHashMap.newKeySet<String>()
    private val addrToRoll       = ConcurrentHashMap<String, String>()
    private val rollToAddr       = ConcurrentHashMap<String, String>()
    private val peerMap          = ConcurrentHashMap<String, Peer>()
    private val seenIds          = Collections.synchronizedSet(LinkedHashSet<Long>())
    private val outboundQueue    = ConcurrentHashMap<String, ConcurrentLinkedQueue<QueuedMessage>>()

    // Fragment reassembly
    private val fragBufs     = ConcurrentHashMap<Long, Array<ByteArray?>>()
    private val fragReceived = ConcurrentHashMap<Long, Int>()

    @Volatile private var advertising = false
    @Volatile private var scanning    = false

    // ── Lifecycle ──────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        inst  = this
        btMgr = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        btAdp = btMgr?.adapter
        createChannel()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                startForeground(NID, buildNotif(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(NID, buildNotif())
        } catch (e: Exception) { try { startForeground(NID, buildNotif()) } catch (_: Exception) {} }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (myRoll.isNotBlank() && hasPerms()) {
            startGattServer(); startAdv(); startScan(); startQueueCleanup()
            isRunning = true
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false; inst = null; scope.cancel()
        stopAdv(); stopScan()
        gattClients.values.forEach { runCatching { it.close() } }
        runCatching { gattServer?.close() }
        super.onDestroy()
    }

    // When user swipes app from recents — restart service after 1 second
    override fun onTaskRemoved(rootIntent: Intent?) {
        val restart = PendingIntent.getService(
            applicationContext, 1,
            Intent(applicationContext, BluetoothService::class.java),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000, restart)
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── FEATURE 1: Fragmentation ──────────────────────────────────────────────────
    //
    // Payloads larger than CHUNK_SIZE are split into numbered fragments.
    // Each fragment is a Packet whose payload = type(1)+fragID(8)+total(2)+index(2)+chunk
    // Receiver buffers by fragID, reassembles when all arrive.

    private fun enqueue(type: Byte, payload: Any) {
        scope.launch {
            val jsonBytes = gson.toJson(payload).toByteArray(Charsets.UTF_8)
            val msgId = rng.nextLong()
            val targetRoll = targetRollOf(payload)

            if (jsonBytes.size <= CHUNK_SIZE) {
                val pkt = Packet(type, TTL_MAX, sessionID, msgId, jsonBytes).encode()
                broadcastRaw(pkt, null)
                storeForOffline(pkt, targetRoll)
            } else {
                val chunks = jsonBytes.toList().chunked(CHUNK_SIZE).map { it.toByteArray() }
                val fragID = rng.nextLong()
                Log.d(TAG, "Fragmenting ${jsonBytes.size}b into ${chunks.size} chunks")
                chunks.forEachIndexed { i, chunk ->
                    val fragPay = ByteBuffer.allocate(13 + chunk.size).also { b ->
                        b.put(type); b.putLong(fragID); b.putShort(chunks.size.toShort())
                        b.putShort(i.toShort()); b.put(chunk)
                    }.array()
                    val pkt = Packet(0x05, TTL_MAX, sessionID, msgId + i, fragPay).encode()
                    broadcastRaw(pkt, null)
                    storeForOffline(pkt, targetRoll)
                    delay(20)
                }
            }
        }
    }

    private fun reassemble(fragPayload: ByteArray): Pair<Byte, ByteArray>? {
        if (fragPayload.size < 13) return null
        val buf = ByteBuffer.wrap(fragPayload)
        val innerType  = buf.get()
        val fragID     = buf.getLong()
        val total      = buf.getShort().toInt()
        val index      = buf.getShort().toInt()
        val data       = ByteArray(fragPayload.size - 13).also { buf.get(it) }
        val chunks     = fragBufs.getOrPut(fragID) { arrayOfNulls(total) }
        chunks[index]  = data
        val count      = fragReceived.merge(fragID, 1, Int::plus) ?: 1
        return if (count >= total) {
            fragBufs.remove(fragID); fragReceived.remove(fragID)
            Pair(innerType, chunks.filterNotNull().fold(ByteArray(0)) { a, b -> a + b })
        } else null
    }

    // ── FEATURE 2: Background BLE ─────────────────────────────────────────────────
    //
    // Service runs as foreground with START_STICKY.
    // onTaskRemoved restarts via AlarmManager when user kills app.
    // Notification is setOngoing(true) so user can't dismiss it.
    // Advertising never times out (setTimeout(0)).

    // ── FEATURE 3: Store-and-Forward ─────────────────────────────────────────────
    //
    // Outgoing direct messages for offline peers are buffered.
    // On ANNOUNCE (peer connected), we flush their queue.
    // Messages expire after QUEUE_TTL_MS.

    private fun targetRollOf(payload: Any): String? = when (payload) {
        is MsgPayload  -> if (payload.channel == "global" || payload.channel.isEmpty()) null else payload.channel
        is AttPayload  -> payload.studentRoll
        else -> null
    }

    private fun storeForOffline(pkt: ByteArray, targetRoll: String?) {
        targetRoll ?: return
        val addr = rollToAddr[targetRoll]
        if (addr != null && gattClients.containsKey(addr)) return  // peer is online
        val queue = outboundQueue.getOrPut(targetRoll) { ConcurrentLinkedQueue() }
        while (queue.size >= MAX_QUEUE) queue.poll()
        queue.add(QueuedMessage(pkt))
        Log.d(TAG, "Stored for offline $targetRoll (queue=${queue.size})")
    }

    private fun flushQueue(peerRoll: String, gatt: BluetoothGatt) {
        val queue = outboundQueue[peerRoll]?.takeIf { it.isNotEmpty() } ?: return
        Log.d(TAG, "Flushing ${queue.size} msgs to $peerRoll")
        scope.launch {
            while (queue.isNotEmpty()) {
                val item = queue.poll() ?: break
                if (!item.isExpired()) { writeRx(gatt, item.data); delay(30) }
            }
        }
    }

    private fun startQueueCleanup() {
        scope.launch {
            while (true) {
                delay(5 * 60_000L)
                outboundQueue.values.forEach { q -> q.removeAll { it.isExpired() } }
            }
        }
    }

    // ── GATT Server ───────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun startGattServer() {
        if (gattServer != null) return
        gattServer = btMgr?.openGattServer(this, srvCb) ?: return
        val svc = BluetoothGattService(SVC_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val rx = BluetoothGattCharacteristic(RX_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE)
        val tx = BluetoothGattCharacteristic(TX_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ)
        tx.addDescriptor(BluetoothGattDescriptor(CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))
        txChar = tx
        svc.addCharacteristic(rx); svc.addCharacteristic(tx)
        gattServer?.addService(svc)
        Log.d(TAG, "GATT server ready")
    }

    private val srvCb = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(d: BluetoothDevice, s: Int, state: Int) {
            if (state == BluetoothProfile.STATE_DISCONNECTED) { subscribedAddrs.remove(d.address); peerGone(d.address) }
        }
        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(d: BluetoothDevice, reqId: Int, c: BluetoothGattCharacteristic, prep: Boolean, resp: Boolean, off: Int, v: ByteArray?) {
            if (resp) gattServer?.sendResponse(d, reqId, BluetoothGatt.GATT_SUCCESS, 0, null)
            if (c.uuid == RX_UUID && v != null) handleRaw(d.address, v)
        }
        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(d: BluetoothDevice, reqId: Int, desc: BluetoothGattDescriptor, prep: Boolean, resp: Boolean, off: Int, v: ByteArray?) {
            if (desc.uuid == CCCD_UUID) {
                if (v?.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == true) subscribedAddrs.add(d.address) else subscribedAddrs.remove(d.address)
            }
            if (resp) gattServer?.sendResponse(d, reqId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }
    }

    // ── BLE Advertising ───────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun startAdv() {
        if (!hasPerms() || advertising) return
        btAdp?.bluetoothLeAdvertiser?.startAdvertising(
            AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true).setTimeout(0).build(),
            AdvertiseData.Builder().setIncludeDeviceName(false).addServiceUuid(ParcelUuid(SVC_UUID)).build(),
            advCb)
    }

    @SuppressLint("MissingPermission")
    private fun stopAdv() { if (!hasPerms()) return; runCatching { btAdp?.bluetoothLeAdvertiser?.stopAdvertising(advCb) }; advertising = false }

    private val advCb = object : AdvertiseCallback() {
        override fun onStartSuccess(s: AdvertiseSettings?) { advertising = true }
        override fun onStartFailure(e: Int) {
            if (e == ADVERTISE_FAILED_ALREADY_STARTED) advertising = true
            else scope.launch { delay(3000); startAdv() }
        }
    }

    // ── BLE Scanning ──────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (!hasPerms() || scanning) return
        btAdp?.bluetoothLeScanner?.startScan(
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SVC_UUID)).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                .setReportDelay(0).build(),
            scanCb)
        scanning = true
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() { runCatching { btAdp?.bluetoothLeScanner?.stopScan(scanCb) }; scanning = false }

    private val scanCb = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(t: Int, r: ScanResult?) {
            val dev = r?.device ?: return
            val addr = dev.address
            if (gattClients.containsKey(addr) || !connectingSet.add(addr)) return
            scope.launch { delay(50 + rng.nextInt(150).toLong()); connectPeer(dev); connectingSet.remove(addr) }
        }
        override fun onScanFailed(e: Int) {
            if (e != SCAN_FAILED_ALREADY_STARTED) { scanning = false; scope.launch { delay(5000); startScan() } }
        }
    }

    // ── GATT Client ───────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun connectPeer(dev: BluetoothDevice) {
        if (!hasPerms()) return
        val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            dev.connectGatt(this, false, clientCb, BluetoothDevice.TRANSPORT_LE)
        else dev.connectGatt(this, false, clientCb)
        if (gatt != null) gattClients[dev.address] = gatt
    }

    private val clientCb = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, s: Int, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED    -> gatt.requestMtu(517)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    gattClients.remove(gatt.device.address); mtuMap.remove(gatt.device.address)
                    peerGone(gatt.device.address); scope.launch { delay(4000); connectingSet.remove(gatt.device.address) }
                }
            }
        }
        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, s: Int) { mtuMap[gatt.device.address] = mtu - 3; gatt.discoverServices() }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, s: Int) {
            if (s != BluetoothGatt.GATT_SUCCESS) { gatt.disconnect(); return }
            val tx = gatt.getService(SVC_UUID)?.getCharacteristic(TX_UUID) ?: run { gatt.disconnect(); return }
            gatt.setCharacteristicNotification(tx, true)
            val cccd = tx.getDescriptor(CCCD_UUID) ?: run { sendAnnounce(gatt); return }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            else { @Suppress("DEPRECATION") cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; @Suppress("DEPRECATION") gatt.writeDescriptor(cccd) }
        }
        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt, d: BluetoothGattDescriptor, s: Int) { if (d.uuid == CCCD_UUID) sendAnnounce(gatt) }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, c: BluetoothGattCharacteristic) { handleRaw(gatt.device.address, c.value ?: return) }
        override fun onCharacteristicChanged(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, v: ByteArray) { handleRaw(gatt.device.address, v) }
    }

    // ── Packet processing ─────────────────────────────────────────────────────────

    private fun handleRaw(from: String, data: ByteArray) {
        val pkt = Packet.decode(data) ?: return
        val key = pkt.senderID xor pkt.msgID
        synchronized(seenIds) {
            if (!seenIds.add(key)) return
            if (seenIds.size > 2000) {
                val iter = seenIds.iterator()
                repeat(200) {
                    if (iter.hasNext()) {
                        iter.next()
                        iter.remove()
                    }
                }
            }
        }
        if (pkt.type == 0x05.toByte()) {
            val assembled = reassemble(pkt.payload) ?: return
            dispatch(from, assembled.first, assembled.second)
        } else {
            dispatch(from, pkt.type, pkt.payload)
            if (pkt.ttl > 0) relayRaw(Packet(pkt.type, (pkt.ttl - 1).toByte(), pkt.senderID, pkt.msgID, pkt.payload).encode(), from)
        }
    }

    private fun dispatch(from: String, type: Byte, payload: ByteArray) {
        when (type) {
            PKT_ANNOUNCE   -> onAnnounce(from, payload)
            PKT_MESSAGE    -> onMsg(payload)
            PKT_ATTENDANCE -> onAtt(payload)
            PKT_ACK        -> runCatching { deliveryAck.value = gson.fromJson(String(payload), AckPayload::class.java).msgId }
        }
    }

    private fun onAnnounce(addr: String, payload: ByteArray) {
        val a = runCatching { gson.fromJson(String(payload), AnnouncePayload::class.java) }.getOrNull() ?: return
        if (a.roll.isBlank() || a.roll == myRoll) return
        addrToRoll[addr] = a.roll; rollToAddr[a.roll] = addr
        peerMap[a.roll] = Peer(a.roll, a.name, if (a.role == "TEACHER") UserRole.TEACHER else UserRole.STUDENT, a.year, a.branch, a.dept, addr)
        peers.value = peerMap.toMap()
        gattClients[addr]?.let { flushQueue(a.roll, it) }   // store-and-forward delivery
        Log.d(TAG, "Peer: ${a.roll}")
    }

    private fun onMsg(payload: ByteArray) {
        val msg = runCatching { gson.fromJson(String(payload), MsgPayload::class.java) }.getOrNull() ?: return
        incomingMsg.value = msg
        // Send ACK
        scope.launch {
            val ackBytes = gson.toJson(AckPayload(msg.id)).toByteArray()
            val pkt = Packet(PKT_ACK, 1, sessionID, rng.nextLong(), ackBytes).encode()
            rollToAddr[msg.senderRoll]?.let { addr -> gattClients[addr]?.let { writeRx(it, pkt) } }
        }
    }

    private fun onAtt(payload: ByteArray) {
        runCatching { gson.fromJson(String(payload), AttPayload::class.java) }.getOrNull()?.let { incomingAtt.value = it }
    }

    // ── Low-level send helpers ────────────────────────────────────────────────────

    private fun broadcastRaw(data: ByteArray, except: String?) {
        notifyAll(data, except)
        gattClients.filter { it.key != except }.values.forEach { writeRx(it, data) }
    }

    private fun relayRaw(data: ByteArray, except: String) {
        notifyAll(data, except)
        gattClients.filter { it.key != except }.values.forEach { writeRx(it, data) }
    }

    @SuppressLint("MissingPermission")
    private fun sendAnnounce(gatt: BluetoothGatt) {
        val pay = gson.toJson(AnnouncePayload(myRoll, myName, myRole.name, myYear, myBranch, myDept)).toByteArray()
        writeRx(gatt, Packet(PKT_ANNOUNCE, TTL_MAX, sessionID, rng.nextLong(), pay).encode())
    }

    @SuppressLint("MissingPermission")
    private fun writeRx(gatt: BluetoothGatt, data: ByteArray) {
        if (!hasPerms()) return
        val rx = gatt.getService(SVC_UUID)?.getCharacteristic(RX_UUID) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            gatt.writeCharacteristic(rx, data, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        else { @Suppress("DEPRECATION") rx.value = data; rx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE; @Suppress("DEPRECATION") gatt.writeCharacteristic(rx) }
    }

    @SuppressLint("MissingPermission")
    private fun notifyAll(data: ByteArray, except: String?) {
        if (!hasPerms()) return; val tx = txChar ?: return
        subscribedAddrs.filter { it != except }.forEach { addr ->
            val dev = btAdp?.getRemoteDevice(addr) ?: return@forEach
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                gattServer?.notifyCharacteristicChanged(dev, tx, false, data)
            else { @Suppress("DEPRECATION") tx.value = data; @Suppress("DEPRECATION") gattServer?.notifyCharacteristicChanged(dev, tx, false) }
        }
    }

    private fun peerGone(addr: String) {
        val roll = addrToRoll.remove(addr) ?: return
        rollToAddr.remove(roll); peerMap.remove(roll); peers.value = peerMap.toMap()
    }

    // ── Permissions & Notification ────────────────────────────────────────────────

    private fun hasPerms() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        chk(Manifest.permission.BLUETOOTH_SCAN) && chk(Manifest.permission.BLUETOOTH_CONNECT) && chk(Manifest.permission.BLUETOOTH_ADVERTISE)
    else chk(Manifest.permission.BLUETOOTH) && chk(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun chk(p: String) = ActivityCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CH, "OffChat Mesh", NotificationManager.IMPORTANCE_LOW))
    }

    private fun buildNotif() = NotificationCompat.Builder(this, CH)
        .setContentTitle("OffChat").setContentText("Mesh network active")
        .setSmallIcon(android.R.drawable.ic_menu_send).setOngoing(true)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        .setPriority(NotificationCompat.PRIORITY_LOW).build()
}
