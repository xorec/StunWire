package com.xorec.stunwire.model.networking

import android.app.Service
import android.net.wifi.WifiManager
import android.text.format.Formatter
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.xorec.stunwire.model.db.SessionsRepository
import com.xorec.stunwire.StunWireApp
import com.xorec.stunwire.createSessionDatabaseName
import com.xorec.stunwire.model.crypto.decrypt
import com.xorec.stunwire.model.crypto.encrypt
import com.xorec.stunwire.model.crypto.importPartnerIdentityCertificate
import com.xorec.stunwire.model.crypto.loadMyIdentityKeyPair
import com.xorec.stunwire.model.crypto.performKeyAgreement
import com.xorec.stunwire.model.crypto.generateMyEphemeralKeyPair
import com.xorec.stunwire.model.crypto.signMyEphemeralCertificate
import com.xorec.stunwire.model.crypto.verifyPartnerEphemeralCertificate
import com.xorec.stunwire.model.db.SessionMessage
import com.xorec.stunwire.model.db.SessionMessageStatus
import com.xorec.stunwire.model.db.SessionMessageType
import com.xorec.stunwire.eightBytesToLongBigEndian
import com.xorec.stunwire.extractMappedAddress
import com.xorec.stunwire.fourBytesToIntBigEndian
import com.xorec.stunwire.intToBytes
import com.xorec.stunwire.longToBytes
import com.xorec.stunwire.twoBytesToBufferBigEndian
import com.xorec.stunwire.twoBytesToIntBigEndian
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.net.BindException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.charset.Charset
import java.util.Calendar
import java.util.Random
import javax.crypto.spec.SecretKeySpec

const val RCV_BUFFER_SIZE = 1500
// 1 byte (message type) + 2 bytes (data length) + 1 byte (protocol message type)
// + 4 bytes (message code) + 2 bytes (index) + 2 bytes (last index) + 12 bytes (IV)
// + 16 bytes (auth tag)
const val MESSAGE_CHUNK_SIZE = RCV_BUFFER_SIZE - 100
const val STUN_REQUEST_ANSWER_LENGTH_BYTES: Int = 88
const val PEER_TIMESTAMP_DELAY_MILLS = 2000

class SessionManager(private val sessionType: SessionType, private val lifecycleScope: CoroutineScope,
                     private val db: SessionsRepository, private val imageHandler: ImageHandler
) {
    private lateinit var socket: DatagramSocket
    private lateinit var sharedKey: SecretKeySpec

    private var _sessionStatus: MutableLiveData<SessionStatus> = MutableLiveData<SessionStatus>(SessionStatus.SESSION_STATUS_ACTIVE)
    val sessionStatus: LiveData<SessionStatus> = _sessionStatus

    private val sentInConstructionMessages: HashMap<Int, List<ByteArray>> = HashMap()
    private val receivedInConstructionMessages: HashMap<Int, ProtocolReceivedInConstructionMessageData> = HashMap()
    private val sentEndServiceList: ArrayList<Int> = ArrayList()
    private var peerTypingTimestampReceived: Long = 0L
    private var peerTypingTimestampSent: Long = 0L
    private var messageCodeCounter: Int = 2

    fun retrieveNetworkInfo(stunServerAddress: String): NetworkAddress? {
        try {
            socket = try {
                DatagramSocket(8910)
            } catch(e: BindException) {
                DatagramSocket()
            }

            var publicAddress: NetworkAddress? = null

            when(sessionType) {
                SessionType.SESSION_TYPE_INTERNET -> {
                    val serverAddress = InetAddress.getByName(stunServerAddress.split(":")[0])
                    val serverPort = stunServerAddress.split(":")[1].toInt()

                    socket.soTimeout = 15000
                    socket.connect(serverAddress, serverPort)
                    val rcvDataBuffer = ByteArray(STUN_REQUEST_ANSWER_LENGTH_BYTES)
                    val rcvPacket = DatagramPacket(rcvDataBuffer, 0, STUN_REQUEST_ANSWER_LENGTH_BYTES)

                    val magic = byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x21, 0x12, 0xA4.toByte(), 0x42)
                    val transactionID = ByteArray(12)
                    Random().nextBytes(transactionID)

                    val stunRequest: ByteArray =
                        magic + transactionID

                    socket.send(DatagramPacket(stunRequest, 0, 20))
                    socket.receive(rcvPacket)

                    if (!(rcvDataBuffer.slice(IntRange(0, 1)).toByteArray().contentEquals(byteArrayOf(0x01, 0x01))
                                && rcvDataBuffer.slice(IntRange(4, 7)).toByteArray().contentEquals(byteArrayOf(0x21, 0x12, (0xA4).toByte(), 0x42))
                                && rcvDataBuffer.slice(IntRange(8, 19)).toByteArray().contentEquals(transactionID))) {
                        return null
                    }

                    publicAddress = extractMappedAddress(rcvDataBuffer)

                    launchKeepAlive()
                }
                SessionType.SESSION_TYPE_LAN -> {
                    publicAddress = NetworkAddress(
                        InetAddress.getByName(
                            Formatter.formatIpAddress(
                                (StunWireApp.instance.applicationContext.getSystemService(
                                    Service.WIFI_SERVICE
                                ) as WifiManager).connectionInfo.ipAddress
                            )
                        ), socket.localPort
                    )
                }
            }

            return publicAddress
        } catch(e: Exception) {
            return null
        }
    }

    fun performHandshake(partnerAddress: NetworkAddress) {
        lifecycleScope.launch(Dispatchers.IO) {
            var handshakeState = HandshakeState.HANDSHAKE_STATE_WAITING_FOR_HANDSHAKE_DATA

            val rcvBuffer = ByteArray(RCV_BUFFER_SIZE)

            val myEncodedIdentityCert = loadMyIdentityKeyPair().certificate.encoded
            val myEphemeralKeyPair = generateMyEphemeralKeyPair()
            val myEncodedEphemeralCert = myEphemeralKeyPair.public.encoded
            val myEphemeralCertSign = signMyEphemeralCertificate(myEncodedEphemeralCert)

            val handshakeData = twoBytesToBufferBigEndian(myEncodedIdentityCert.size) +
                    myEncodedIdentityCert +
                    twoBytesToBufferBigEndian(myEncodedEphemeralCert.size) +
                    myEncodedEphemeralCert +
                    twoBytesToBufferBigEndian(myEphemeralCertSign.size) +
                    myEphemeralCertSign
            val handshakeDataPacket = craftPlainMessagePacket(
                PlainMessageType.PLAIN_MESSAGE_TYPE_HANDSHAKE_DATA,
                handshakeData)
            val handshakeDataApprovedPacket = craftPlainMessagePacket(
                PlainMessageType.PLAIN_MESSAGE_TYPE_HANDSHAKE_DATA_APPROVED,
                byteArrayOf())
            val handshakeCompletedPacket = craftPlainMessagePacket(
                PlainMessageType.PLAIN_MESSAGE_TYPE_HANDSHAKE_COMPLETED,
                byteArrayOf())
            val handshakeErrorPacket = craftPlainMessagePacket(
                PlainMessageType.PLAIN_MESSAGE_TYPE_HANDSHAKE_ERROR,
                byteArrayOf())
            val handshakeErrorReceivedPacket = craftPlainMessagePacket(
                PlainMessageType.PLAIN_MESSAGE_TYPE_HANDSHAKE_ERROR_RECEIVED,
                byteArrayOf())

            var rcvPacket = DatagramPacket(rcvBuffer, 0, rcvBuffer.size)

            socket.connect(partnerAddress.address, partnerAddress.port)
            socket.soTimeout = 500

            while (!socket.isClosed && isActive) {
                try {
                    when (handshakeState) {
                        HandshakeState.HANDSHAKE_STATE_WAITING_FOR_HANDSHAKE_DATA -> {
                            socket.send(handshakeDataPacket)
                        }
                        HandshakeState.HANDSHAKE_STATE_HANDSHAKE_DATA_APPROVED -> {
                            socket.send(handshakeDataApprovedPacket)
                        }
                        HandshakeState.HANDSHAKE_STATE_HANDSHAKE_COMPLETED -> {
                            socket.send(handshakeCompletedPacket)
                        }
                        HandshakeState.HANDSHAKE_STATE_HANDSHAKE_ERROR -> {
                            socket.send(handshakeErrorPacket)
                        }
                    }

                    socket.receive(rcvPacket)

                    // format: MESSAGE_TYPE_PLAIN [1 byte] | plain message type [1 byte] | data
                    if (rcvBuffer[0] != MessageType.MESSAGE_TYPE_PLAIN.ordinal.toByte()) {
                        continue
                    }

                    val receivedMessageType: PlainMessageType =
                        PlainMessageType.values()[rcvBuffer[1].toInt()]

                    if (receivedMessageType == PlainMessageType.PLAIN_MESSAGE_TYPE_HANDSHAKE_DATA) {
                        socket.send(handshakeDataPacket)

                        if (handshakeState == HandshakeState.HANDSHAKE_STATE_WAITING_FOR_HANDSHAKE_DATA) {
                            val partnerIdentityCertSize = twoBytesToIntBigEndian(rcvBuffer, 2)
                            val partnerIdentityCertBytes =
                                rcvBuffer.slice(IntRange(4, 4 + partnerIdentityCertSize - 1)).toByteArray()
                            val partnerEphemeralCertSize =
                                twoBytesToIntBigEndian(rcvBuffer, 4 + partnerIdentityCertSize)
                            val partnerEphemeralCertBytes = rcvBuffer.slice(
                                IntRange(
                                    4 + partnerIdentityCertSize + 2,
                                    4 + partnerIdentityCertSize + 2 + partnerEphemeralCertSize - 1
                                )
                            ).toByteArray()

                            val partnerEphemeralCertSignSize = twoBytesToIntBigEndian(
                                rcvBuffer,
                                4 + partnerIdentityCertSize + 2 + partnerEphemeralCertSize
                            )
                            val partnerEphemeralCertSignBytes = rcvBuffer.slice(
                                IntRange(
                                    4 + partnerIdentityCertSize + 2 + partnerEphemeralCertSize + 2,
                                    4 + partnerIdentityCertSize + 2 + partnerEphemeralCertSize + 2 + partnerEphemeralCertSignSize - 1
                                )
                            ).toByteArray()

                            importPartnerIdentityCertificate(partnerIdentityCertBytes)

                            if (verifyPartnerEphemeralCertificate(partnerEphemeralCertBytes, partnerEphemeralCertSignBytes)) {
                                sharedKey = performKeyAgreement(myEphemeralKeyPair, partnerEphemeralCertBytes)
                                handshakeState =
                                    HandshakeState.HANDSHAKE_STATE_HANDSHAKE_DATA_APPROVED
                            } else handshakeState =
                                HandshakeState.HANDSHAKE_STATE_HANDSHAKE_ERROR
                        }
                    } else if (receivedMessageType == PlainMessageType.PLAIN_MESSAGE_TYPE_HANDSHAKE_DATA_APPROVED && handshakeState == HandshakeState.HANDSHAKE_STATE_HANDSHAKE_DATA_APPROVED) {
                        handshakeState = HandshakeState.HANDSHAKE_STATE_HANDSHAKE_COMPLETED
                    } else if ((receivedMessageType == PlainMessageType.PLAIN_MESSAGE_TYPE_HANDSHAKE_COMPLETED && handshakeState == HandshakeState.HANDSHAKE_STATE_HANDSHAKE_COMPLETED)
                        || (receivedMessageType == PlainMessageType.PLAIN_MESSAGE_TYPE_HANDSHAKE_COMPLETED_RECEIVED && handshakeState == HandshakeState.HANDSHAKE_STATE_HANDSHAKE_COMPLETED)) {
                        db.openSessionDatabase(createSessionDatabaseName())

                        socket.soTimeout = 60000

                        if (sessionType == SessionType.SESSION_TYPE_LAN) {
                            launchKeepAlive()
                        }

                        lifecycleScope.launch(Dispatchers.IO) {
                            while (!socket.isClosed && isActive) {
                                if (Calendar.getInstance().timeInMillis - peerTypingTimestampReceived > PEER_TIMESTAMP_DELAY_MILLS * 2) {
                                    if (_sessionStatus.value == SessionStatus.SESSION_STATUS_TYPING) {
                                        _sessionStatus.postValue(SessionStatus.SESSION_STATUS_ACTIVE)
                                    }
                                }

                                try {
                                    if (receivedInConstructionMessages.isNotEmpty()) {
                                        socket.send(craftEncryptedPacketFromMessage(craftReceivingListMessage()))
                                    }

                                    sentEndServiceList.forEach { messageCode ->
                                        if (_sessionStatus.value == SessionStatus.SESSION_STATUS_ACTIVE || _sessionStatus.value == SessionStatus.SESSION_STATUS_DISCONNECTING) {

                                            if (!sentInConstructionMessages.containsKey(messageCode)) {
                                                sentEndServiceList.remove(messageCode)
                                            } else {
                                                val sentInConstructionMessage = sentInConstructionMessages[messageCode]!!
                                                try {
                                                    socket.send(craftEncryptedPacketFromMessage(craftEndOfContentTransmissionMessage(messageCode, sentInConstructionMessage.lastIndex)))
                                                } catch (e: Exception) {

                                                }
                                            }
                                        }
                                    }

                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }

                                delay(250)
                            }
                        }

                        lifecycleScope.launch(Dispatchers.IO) {
                            while (!socket.isClosed && isActive) {
                                try {
                                    socket.receive(rcvPacket)

                                    // encrypted data format: MESSAGE_TYPE_ENCRYPTED [1 byte] | data length [2 bytes] | data bytes
                                    if (rcvBuffer[0] != MessageType.MESSAGE_TYPE_ENCRYPTED.ordinal.toByte()) {
                                        continue
                                    }

                                    val receivedMessageType: MessageType =
                                        MessageType.values()[rcvBuffer[0].toInt()]

                                    var decryptedMessage: ByteArray

                                    if (receivedMessageType == MessageType.MESSAGE_TYPE_ENCRYPTED) {
                                        val encryptedSize = twoBytesToIntBigEndian(rcvBuffer, 1)

                                        decryptedMessage = decryptMessage(
                                            rcvBuffer.slice(IntRange(3, 3 + encryptedSize - 1)).toByteArray()
                                        )

                                        // plain data format: message type [1 byte] | data bytes
                                        if (decryptedMessage[0].toInt() < EncryptedMessageType.MESSAGE_TYPE_CONTENT.ordinal || decryptedMessage[0].toInt() > EncryptedMessageType.MESSAGE_TYPE_RECEIVING_LIST_FLUSH.ordinal) {
                                            continue
                                        }

                                        val receivedMessageSubtype =
                                            EncryptedMessageType.values()[decryptedMessage[0].toInt()]

                                        decryptedMessage = decryptedMessage.slice(IntRange(1, decryptedMessage.size - 1)).toByteArray()

                                        when (receivedMessageSubtype) {
                                            EncryptedMessageType.MESSAGE_TYPE_CONTENT -> {
                                                processContentMessage(decryptedMessage)
                                            }
                                            EncryptedMessageType.MESSAGE_TYPE_END_OF_CONTENT_TRANSMISSION -> {
                                                processEndOfContentTransmissionMessage(decryptedMessage)
                                            }
                                            EncryptedMessageType.MESSAGE_TYPE_RESEND_REQUEST -> {
                                                processResendRequestMessage(decryptedMessage)
                                            }
                                            EncryptedMessageType.MESSAGE_TYPE_ALL_CONTENT_RECEIVED -> {
                                                processAllContentReceivedMessage(decryptedMessage)
                                            }
                                            EncryptedMessageType.MESSAGE_TYPE_RECEIVING_LIST -> {
                                                processReceivingListMessage(decryptedMessage)
                                            }
                                            EncryptedMessageType.MESSAGE_TYPE_RECEIVING_LIST_FLUSH -> {
                                                processReceivingListFlushMessage(decryptedMessage)
                                            }
                                        }
                                    }
                                } catch(e: SocketTimeoutException) {
                                    e.printStackTrace()
                                    StunWireApp.instance.sessionOver()
                                }  catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }

                        StunWireApp.instance.handshakeCompleted()
                        return@launch
                    } else if (receivedMessageType == PlainMessageType.PLAIN_MESSAGE_TYPE_HANDSHAKE_ERROR) {
                        socket.send(handshakeErrorReceivedPacket)
                        StunWireApp.instance.handshakeError()
                        return@launch
                    } else if (receivedMessageType == PlainMessageType.PLAIN_MESSAGE_TYPE_HANDSHAKE_ERROR_RECEIVED) {
                        StunWireApp.instance.handshakeError()
                        return@launch
                    }
                } catch (e: Exception) {
                }
            }
        }
    }

    fun stop() {
        socket.close()
    }

    fun sendImageMessage(imageData: ByteArray) {
        val message = SessionMessage(
            true, messageCodeCounter++, SessionMessageType.SESSION_MESSAGE_TYPE_IMAGE.ordinal.toByte(), SessionMessageStatus.SESSION_MESSAGE_STATUS_DELIVERING,
            false, Calendar.getInstance().timeInMillis, imageData
        )

        db.insert(message)
        sendMessage(message)
    }

    fun sendTextMessage(text: String) {
        val message = SessionMessage(true, messageCodeCounter++, SessionMessageType.SESSION_MESSAGE_TYPE_TEXT.ordinal.toByte(),
            SessionMessageStatus.SESSION_MESSAGE_STATUS_DELIVERING,
            false, Calendar.getInstance().timeInMillis, text.toByteArray(
                Charset.forName("UTF-16")))

        db.insert(message)
        sendMessage(message)
    }

    fun sendReadMessage(messageCode: Int) {
        db.setMessageRead(messageCode)

        val message = SessionMessage(true, messageCodeCounter++, SessionMessageType.SESSION_MESSAGE_TYPE_READ.ordinal.toByte(),
            SessionMessageStatus.SESSION_MESSAGE_STATUS_DELIVERING,
            false, Calendar.getInstance().timeInMillis, intToBytes(messageCode)
        )

        sendMessage(message)
    }

    fun sendPeerTypingMessage() {
        if (Calendar.getInstance().timeInMillis - peerTypingTimestampSent > PEER_TIMESTAMP_DELAY_MILLS) {
            peerTypingTimestampSent = Calendar.getInstance().timeInMillis
            sendMessage(SessionMessage(true, 1, SessionMessageType.SESSION_MESSAGE_TYPE_PEER_TYPING.ordinal.toByte(), SessionMessageStatus.SESSION_MESSAGE_STATUS_DELIVERING, false, Calendar.getInstance().timeInMillis, byteArrayOf(0x00)))
        }
    }

    fun deleteMessage(isSent: Boolean, messageCode: Int) {
        if (isSent) {
            sentInConstructionMessages.remove(messageCode)
        }

        val message = SessionMessage(true, messageCodeCounter++, SessionMessageType.SESSION_MESSAGE_TYPE_DELETE.ordinal.toByte(),
            SessionMessageStatus.SESSION_MESSAGE_STATUS_DELIVERING,
            false, Calendar.getInstance().timeInMillis, byteArrayOf(if (isSent) (0x01).toByte() else (0x00).toByte()) + intToBytes(messageCode)
        )

        sendMessage(message)
    }

    fun disconnectLaunched() {
        _sessionStatus.postValue(SessionStatus.SESSION_STATUS_DISCONNECTING)
        sendMessage(SessionMessage(true, 0, SessionMessageType.SESSION_MESSAGE_TYPE_DISCONNECT.ordinal.toByte(), SessionMessageStatus.SESSION_MESSAGE_STATUS_DELIVERING, false, Calendar.getInstance().timeInMillis, byteArrayOf(0x00)))
    }

    // format: MESSAGE_TYPE_PLAIN [1 byte] | plain message type [1 byte] | data
    private fun craftPlainMessagePacket(plainMessageType: PlainMessageType, data: ByteArray): DatagramPacket {
        val wholeData = byteArrayOf(MessageType.MESSAGE_TYPE_PLAIN.ordinal.toByte()) + byteArrayOf(plainMessageType.ordinal.toByte()) + data
        return DatagramPacket(wholeData, 0, wholeData.size)
    }

    private fun craftEncryptedPacketFromMessage(networkMessage: ProtocolMessage): DatagramPacket {
        // plain data format: message type [1 byte] | data bytes
        val encryptedMessage = if (networkMessage.data != null) {
            encrypt(messageTypeToByteArray(networkMessage.messageType) + networkMessage.data, sharedKey)
        } else encrypt(messageTypeToByteArray(networkMessage.messageType), sharedKey)

        // encrypted data format: MESSAGE_TYPE_ENCRYPTED [1 byte] | data length [2 bytes] | data bytes
        val finalEncryptedMessage =
            byteArrayOf(MessageType.MESSAGE_TYPE_ENCRYPTED.ordinal.toByte()) + twoBytesToBufferBigEndian(
                encryptedMessage.size
            ) + encryptedMessage

        return DatagramPacket(finalEncryptedMessage, 0, finalEncryptedMessage.size)
    }

    private fun messageTypeToByteArray(type: EncryptedMessageType): ByteArray {
        return byteArrayOf(type.ordinal.toByte())
    }

    private fun decryptMessage(encryptedMessage: ByteArray): ByteArray {
        return decrypt(encryptedMessage, sharedKey)

    }

    private fun insertReceivedSessionMessage(message: SessionMessage) {
        db.insert(message)
    }

    private fun sendMessage(message: SessionMessage) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val chunkList = ArrayList<ByteArray>()

                // format: session message type [1 byte] | timestamp [8 bytes] | session message data
                for (list in (byteArrayOf(message.type) + longToBytes(message.timestamp) + message.data).asIterable().chunked(
                    MESSAGE_CHUNK_SIZE
                )) {
                    chunkList.add(list.toByteArray())
                }

                sentInConstructionMessages[message.messageCode] = chunkList

                for (list in chunkList) {
                    // format: message code [4 bytes] | index [2 bytes] | last index [2 bytes] | data bytes [0-8192]
                    socket.send(craftEncryptedPacketFromMessage(craftContentMessage(message.messageCode, chunkList.indexOf(list), chunkList.size - 1, list)))
                    delay(2)
                }

                socket.send(craftEncryptedPacketFromMessage(craftEndOfContentTransmissionMessage(message.messageCode, chunkList.size - 1)))

                sentEndServiceList.add(message.messageCode)
            } catch(e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun craftContentMessage(messageCode: Int, chunkIndex: Int, lastIndex: Int, chunk: ByteArray): ProtocolMessage {
        // format: message code [4 bytes] | chunk index [2 bytes] | last index [2 bytes] | chunk bytes
        return ProtocolMessage(
            EncryptedMessageType.MESSAGE_TYPE_CONTENT, intToBytes(messageCode) + twoBytesToBufferBigEndian(chunkIndex) + twoBytesToBufferBigEndian(lastIndex)
                + chunk)
    }

    private fun processContentMessage(rcvBuffer: ByteArray) {
        // format: message code [4 bytes] | chunk index [2 bytes] | last index [2 bytes] | chunk bytes
        val messageCode = fourBytesToIntBigEndian(rcvBuffer, 0)
        val chunkIndex = twoBytesToIntBigEndian(rcvBuffer, 4)
        val lastIndex = twoBytesToIntBigEndian(rcvBuffer, 6)
        val chunkLength = rcvBuffer.size - 8

        try {
            val chunkBytes = rcvBuffer.slice(IntRange(8, 8 + chunkLength - 1)).toByteArray()

            if (receivedInConstructionMessages.containsKey(messageCode)) {
                if (!receivedInConstructionMessages[messageCode]!!.chunks.containsKey(chunkIndex)) {
                    receivedInConstructionMessages[messageCode]!!.chunks[chunkIndex] = chunkBytes
                }

            } else {
                val data = ProtocolReceivedInConstructionMessageData(lastIndex)
                data.chunks[chunkIndex] = chunkBytes

                receivedInConstructionMessages[messageCode] = data
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun craftEndOfContentTransmissionMessage(messageCode: Int, lastIndex: Int): ProtocolMessage {
        // format: message code [4 bytes] | last index [2 bytes]
        return ProtocolMessage(EncryptedMessageType.MESSAGE_TYPE_END_OF_CONTENT_TRANSMISSION, intToBytes(messageCode) + twoBytesToBufferBigEndian(lastIndex))
    }

    private fun processEndOfContentTransmissionMessage(rcvBuffer: ByteArray) {
        // format: message code [4 bytes] | last index [2 bytes]
        val messageCode = fourBytesToIntBigEndian(rcvBuffer, 0)
        val lastIndex = twoBytesToIntBigEndian(rcvBuffer, 4)

        val absentIndexes = ArrayList<Int>()
        for (index in 0..lastIndex) {
            absentIndexes.add(index)
        }

        if (!receivedInConstructionMessages.containsKey(messageCode)) {
            // if we didn't hear about this message before, sending resendRequest message with ALL indexes
            socket.send(craftEncryptedPacketFromMessage(craftResendRequestMessage(messageCode, absentIndexes)))
        } else {
            // collecting absent indexes
            for (index in 0..lastIndex) {
                if (receivedInConstructionMessages.get(messageCode)!!.chunks.containsKey(index)) {
                    absentIndexes.remove(index)
                }
            }
            if (absentIndexes.isEmpty()) {
                // we've received everything now, sending all_content_received message
                socket.send(craftEncryptedPacketFromMessage(craftAllContentReceivedMessage(messageCode)))
                // and finally processing the session message
                processReceivedSessionMessage(messageCode)
            } else {
                // sending resendRequest message
                socket.send(craftEncryptedPacketFromMessage(craftResendRequestMessage(messageCode, absentIndexes)))
            }
        }
    }

    private fun processReceivedSessionMessage(messageCode: Int) {
        // inserting message into database
        val receivedMessageInConstruction = receivedInConstructionMessages.get(messageCode)!!
        val messageData = ByteArrayOutputStream()

        for (index in 0..receivedMessageInConstruction.lastIndex) {
            messageData.write(receivedMessageInConstruction.chunks.get(index)!!)
        }

        // format: session message type [1 byte] | timestamp [8 bytes] | session message data

        // excluding message from received in construction list
        receivedInConstructionMessages.remove(messageCode)

        val initialData = messageData.toByteArray().slice(IntRange(9, messageData.toByteArray().size - 1)).toByteArray()
        val finalData = processReceivedContent(SessionMessageType.values()[messageData.toByteArray()[0].toInt()], initialData)

        // processing message
        val sessionMessage = SessionMessage(false, messageCode,
            messageData.toByteArray()[0],
            SessionMessageStatus.SESSION_MESSAGE_STATUS_DELIVERING,
            false, eightBytesToLongBigEndian(messageData.toByteArray(), 1),
            finalData)

        when (SessionMessageType.values()[sessionMessage.type.toInt()]) {
            SessionMessageType.SESSION_MESSAGE_TYPE_DISCONNECT -> {
                StunWireApp.instance.sessionOver()
            }
            SessionMessageType.SESSION_MESSAGE_TYPE_PEER_TYPING -> {
                peerTypingTimestampReceived = Calendar.getInstance().timeInMillis
                _sessionStatus.postValue(SessionStatus.SESSION_STATUS_TYPING)
            }
            SessionMessageType.SESSION_MESSAGE_TYPE_TEXT -> {
                insertReceivedSessionMessage(sessionMessage)
            }
            SessionMessageType.SESSION_MESSAGE_TYPE_IMAGE -> {
                insertReceivedSessionMessage(sessionMessage)
            }
            SessionMessageType.SESSION_MESSAGE_TYPE_READ -> {
                db.setMessageRead(fourBytesToIntBigEndian(sessionMessage.data, 0))
            }
            SessionMessageType.SESSION_MESSAGE_TYPE_DELETE -> {
                val isSent = sessionMessage.data[0] != (0x01).toByte()
                val messageCode = fourBytesToIntBigEndian(sessionMessage.data, 1)

                db.delete(isSent, messageCode)
            }
        }
    }

    private fun processReceivedContent(type: SessionMessageType, initialData: ByteArray): ByteArray {
        return if (type == SessionMessageType.SESSION_MESSAGE_TYPE_IMAGE) {
            imageHandler.scaleBitmapToFitSessionScreen(initialData)
        } else initialData
    }

    private fun craftResendRequestMessage(messageCode: Int, absentIndexes: ArrayList<Int>): ProtocolMessage {
        // ((1431 / 2) * 1427 = 1020305 is the max size of a media message to transit
        // format: message code [4 bytes] | absent indexes [2 bytes each]
        val outputStream = ByteArrayOutputStream()
        outputStream.write(intToBytes(messageCode))

        for (index in absentIndexes) {
            outputStream.write(twoBytesToBufferBigEndian(index))
        }

        return ProtocolMessage(EncryptedMessageType.MESSAGE_TYPE_RESEND_REQUEST, outputStream.toByteArray())
    }

    private fun processResendRequestMessage(rcvBuffer: ByteArray) {
        // format: message code [4 bytes] | absent indexes [2 bytes each]
        val messageCode = fourBytesToIntBigEndian(rcvBuffer, 0)
        val absentIndexesAmount: Int = (rcvBuffer.size - 4) / 2

        // checking if we should answer to this request at all
        if (sentInConstructionMessages.containsKey(messageCode)) {
            // well, let's send everything what was asked
            val sentInConstructionMessage = sentInConstructionMessages.get(messageCode)!!
            for (index in 0 until absentIndexesAmount) {
                val absentIndex = twoBytesToIntBigEndian(rcvBuffer, 4 + index * 2)

                socket.send(craftEncryptedPacketFromMessage(craftContentMessage(messageCode, absentIndex, sentInConstructionMessage.lastIndex,
                    sentInConstructionMessage.get(absentIndex))))
            }
        }
    }

    private fun craftAllContentReceivedMessage(messageCode: Int): ProtocolMessage {
        // format: message code [4 bytes]
        return ProtocolMessage(EncryptedMessageType.MESSAGE_TYPE_ALL_CONTENT_RECEIVED, intToBytes(messageCode))
    }

    private fun processAllContentReceivedMessage(rcvBuffer: ByteArray) {
        // format: message code [4 bytes]
        val messageCode = fourBytesToIntBigEndian(rcvBuffer, 0)

        sentInConstructionMessages.remove(messageCode)
        sentEndServiceList.remove(messageCode)

        // let's check if this was a disconnect message acceptance confirmation, and we're disconnecting
        if (_sessionStatus.value == SessionStatus.SESSION_STATUS_DISCONNECTING && messageCode == 0) {
            StunWireApp.instance.sessionOver()
        }

        db.setMessageDelivered(messageCode)
    }

    private fun craftReceivingListMessage(): ProtocolMessage {
        // format: receiving codes [2 bytes each]
        val outputStream = ByteArrayOutputStream()

        for (messageInConstruction in receivedInConstructionMessages) {
            outputStream.write(twoBytesToBufferBigEndian(messageInConstruction.key))
        }

        return ProtocolMessage(EncryptedMessageType.MESSAGE_TYPE_RECEIVING_LIST, outputStream.toByteArray())
    }

    private fun processReceivingListMessage(rcvBuffer: ByteArray) {
        // format: receiving codes [2 bytes each]
        val receivingIndexesAmount: Int = (rcvBuffer.size) / 2
        val flushCodes = ArrayList<Int>()

        for (index in 0 until receivingIndexesAmount) {
            val receivingCode = twoBytesToIntBigEndian(rcvBuffer, index * 2)

            if (!sentInConstructionMessages.containsKey(receivingCode)) {
                flushCodes.add(receivingCode)
            }
        }

        if (flushCodes.isNotEmpty()) {
            socket.send(craftEncryptedPacketFromMessage(craftReceivingListFlushMessage(flushCodes)))
        }
    }

    private fun craftReceivingListFlushMessage(flushCodes: ArrayList<Int>): ProtocolMessage {
        // format: flush codes [2 bytes each]
        val outputStream = ByteArrayOutputStream()

        for (code in flushCodes) {
            outputStream.write(twoBytesToBufferBigEndian(code))
        }

        return ProtocolMessage(EncryptedMessageType.MESSAGE_TYPE_RECEIVING_LIST_FLUSH, outputStream.toByteArray())
    }

    private fun processReceivingListFlushMessage(rcvBuffer: ByteArray) {
        // format: flush codes [2 bytes each]
        val flushCodesAmount: Int = (rcvBuffer.size) / 2

        for (index in 0 until flushCodesAmount) {
            val flushCode = twoBytesToIntBigEndian(rcvBuffer, index * 2)
            receivedInConstructionMessages.remove(flushCode)
        }
    }

    private fun launchKeepAlive() {
        lifecycleScope.launch(Dispatchers.IO) {
            while(!socket.isClosed && isActive) {
                try {
                    socket.send(craftPlainMessagePacket(
                        PlainMessageType.PLAIN_MESSAGE_TYPE_KEEP_ALIVE,
                        byteArrayOf()
                    ))
                    delay(2000)
                } catch(e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}