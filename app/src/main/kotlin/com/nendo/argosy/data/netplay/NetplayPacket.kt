package com.nendo.argosy.data.netplay

import java.nio.ByteBuffer
import java.nio.ByteOrder

sealed class NetplayPacket {

    abstract val typeByte: Byte

    abstract fun serialize(): ByteArray

    data class PortInputState(
        val port: Int,
        val bitmask: Int
    )

    data class InputBundle(
        val frameIndex: Long,
        val ports: List<PortInputState>
    ) : NetplayPacket() {
        override val typeByte: Byte get() = TYPE_INPUT_BUNDLE

        override fun serialize(): ByteArray {
            val size = 1 + 8 + 1 + ports.size * (1 + 4)
            val buf = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
            buf.put(typeByte)
            buf.putLong(frameIndex)
            buf.put((ports.size and 0xFF).toByte())
            ports.forEach { port ->
                buf.put((port.port and 0xFF).toByte())
                buf.putInt(port.bitmask)
            }
            return buf.array()
        }
    }

    data class GuestInput(
        val guestFrameIndex: Long,
        val portNumber: Int,
        val bitmask: Int
    ) : NetplayPacket() {
        override val typeByte: Byte get() = TYPE_GUEST_INPUT

        override fun serialize(): ByteArray {
            val buf = ByteBuffer.allocate(1 + 8 + 1 + 4).order(ByteOrder.BIG_ENDIAN)
            buf.put(typeByte)
            buf.putLong(guestFrameIndex)
            buf.put((portNumber and 0xFF).toByte())
            buf.putInt(bitmask)
            return buf.array()
        }
    }

    data class SnapshotRequest(
        val reasonCode: Int
    ) : NetplayPacket() {
        override val typeByte: Byte get() = TYPE_SNAPSHOT_REQUEST

        override fun serialize(): ByteArray {
            val buf = ByteBuffer.allocate(1 + 1).order(ByteOrder.BIG_ENDIAN)
            buf.put(typeByte)
            buf.put((reasonCode and 0xFF).toByte())
            return buf.array()
        }
    }

    data class SnapshotChunk(
        val snapshotId: Int,
        val chunkIndex: Int,
        val chunkTotal: Int,
        val payload: ByteArray
    ) : NetplayPacket() {
        override val typeByte: Byte get() = TYPE_SNAPSHOT_CHUNK

        override fun serialize(): ByteArray {
            val chunkLen = payload.size
            val buf = ByteBuffer.allocate(1 + 4 + 2 + 2 + 2 + chunkLen).order(ByteOrder.BIG_ENDIAN)
            buf.put(typeByte)
            buf.putInt(snapshotId)
            buf.putShort((chunkIndex and 0xFFFF).toShort())
            buf.putShort((chunkTotal and 0xFFFF).toShort())
            buf.putShort((chunkLen and 0xFFFF).toShort())
            buf.put(payload)
            return buf.array()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SnapshotChunk) return false
            return snapshotId == other.snapshotId &&
                chunkIndex == other.chunkIndex &&
                chunkTotal == other.chunkTotal &&
                payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = snapshotId
            result = 31 * result + chunkIndex
            result = 31 * result + chunkTotal
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    data class Ping(
        val timestampNanos: Long
    ) : NetplayPacket() {
        override val typeByte: Byte get() = TYPE_PING

        override fun serialize(): ByteArray {
            val buf = ByteBuffer.allocate(1 + 8).order(ByteOrder.BIG_ENDIAN)
            buf.put(typeByte)
            buf.putLong(timestampNanos)
            return buf.array()
        }
    }

    data class Pong(
        val timestampNanos: Long
    ) : NetplayPacket() {
        override val typeByte: Byte get() = TYPE_PONG

        override fun serialize(): ByteArray {
            val buf = ByteBuffer.allocate(1 + 8).order(ByteOrder.BIG_ENDIAN)
            buf.put(typeByte)
            buf.putLong(timestampNanos)
            return buf.array()
        }
    }

    sealed class SessionControl : NetplayPacket() {
        override val typeByte: Byte get() = TYPE_SESSION_CONTROL
        abstract val subtype: Byte

        data object Goodbye : SessionControl() {
            override val subtype: Byte get() = SESSION_CONTROL_GOODBYE
            override fun serialize(): ByteArray = byteArrayOf(TYPE_SESSION_CONTROL, subtype)
        }

        data object Kick : SessionControl() {
            override val subtype: Byte get() = SESSION_CONTROL_KICK
            override fun serialize(): ByteArray = byteArrayOf(TYPE_SESSION_CONTROL, subtype)
        }

        data class SnapshotAck(
            val snapshotId: Int,
            val acknowledgedChunks: List<Int>
        ) : SessionControl() {
            override val subtype: Byte get() = SESSION_CONTROL_SNAPSHOT_ACK

            override fun serialize(): ByteArray {
                val ackCount = acknowledgedChunks.size
                val buf = ByteBuffer.allocate(1 + 1 + 4 + 2 + ackCount * 2)
                    .order(ByteOrder.BIG_ENDIAN)
                buf.put(TYPE_SESSION_CONTROL)
                buf.put(subtype)
                buf.putInt(snapshotId)
                buf.putShort((ackCount and 0xFFFF).toShort())
                acknowledgedChunks.forEach { buf.putShort((it and 0xFFFF).toShort()) }
                return buf.array()
            }
        }
    }

    companion object {
        const val TYPE_INPUT_BUNDLE: Byte = 0x01
        const val TYPE_GUEST_INPUT: Byte = 0x02
        const val TYPE_SNAPSHOT_REQUEST: Byte = 0x03
        const val TYPE_SNAPSHOT_CHUNK: Byte = 0x04
        const val TYPE_PING: Byte = 0x05
        const val TYPE_PONG: Byte = 0x06
        const val TYPE_SESSION_CONTROL: Byte = 0x07

        const val SESSION_CONTROL_GOODBYE: Byte = 0x00
        const val SESSION_CONTROL_KICK: Byte = 0x01
        const val SESSION_CONTROL_SNAPSHOT_ACK: Byte = 0x02

        fun deserialize(bytes: ByteArray): NetplayPacket? {
            if (bytes.isEmpty()) return null
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            return try {
                when (val type = buf.get()) {
                    TYPE_INPUT_BUNDLE -> readInputBundle(buf)
                    TYPE_GUEST_INPUT -> readGuestInput(buf)
                    TYPE_SNAPSHOT_REQUEST -> readSnapshotRequest(buf)
                    TYPE_SNAPSHOT_CHUNK -> readSnapshotChunk(buf)
                    TYPE_PING -> Ping(buf.long)
                    TYPE_PONG -> Pong(buf.long)
                    TYPE_SESSION_CONTROL -> readSessionControl(buf)
                    else -> {
                        @Suppress("UNUSED_VARIABLE")
                        val ignored = type
                        null
                    }
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun readInputBundle(buf: ByteBuffer): InputBundle {
            val frameIndex = buf.long
            val portCount = buf.get().toInt() and 0xFF
            val ports = ArrayList<PortInputState>(portCount)
            repeat(portCount) {
                val port = buf.get().toInt() and 0xFF
                val bitmask = buf.int
                ports.add(PortInputState(port, bitmask))
            }
            return InputBundle(frameIndex, ports)
        }

        private fun readGuestInput(buf: ByteBuffer): GuestInput {
            val frameIndex = buf.long
            val port = buf.get().toInt() and 0xFF
            val bitmask = buf.int
            return GuestInput(frameIndex, port, bitmask)
        }

        private fun readSnapshotRequest(buf: ByteBuffer): SnapshotRequest {
            val reason = buf.get().toInt() and 0xFF
            return SnapshotRequest(reason)
        }

        private fun readSnapshotChunk(buf: ByteBuffer): SnapshotChunk {
            val snapshotId = buf.int
            val chunkIndex = buf.short.toInt() and 0xFFFF
            val chunkTotal = buf.short.toInt() and 0xFFFF
            val chunkLen = buf.short.toInt() and 0xFFFF
            val payload = ByteArray(chunkLen)
            buf.get(payload)
            return SnapshotChunk(snapshotId, chunkIndex, chunkTotal, payload)
        }

        private fun readSessionControl(buf: ByteBuffer): SessionControl? {
            val subtype = buf.get()
            return when (subtype) {
                SESSION_CONTROL_GOODBYE -> SessionControl.Goodbye
                SESSION_CONTROL_KICK -> SessionControl.Kick
                SESSION_CONTROL_SNAPSHOT_ACK -> {
                    val snapshotId = buf.int
                    val ackCount = buf.short.toInt() and 0xFFFF
                    val chunks = ArrayList<Int>(ackCount)
                    repeat(ackCount) {
                        chunks.add(buf.short.toInt() and 0xFFFF)
                    }
                    SessionControl.SnapshotAck(snapshotId, chunks)
                }
                else -> null
            }
        }
    }
}
