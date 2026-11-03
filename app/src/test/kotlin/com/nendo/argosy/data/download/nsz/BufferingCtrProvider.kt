package com.nendo.argosy.data.download.nsz

import java.security.AlgorithmParameters
import java.security.Key
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher
import javax.crypto.CipherSpi
import javax.crypto.ShortBufferException

internal class BufferingCtrProvider(val baseline: Provider) :
    Provider("NszTestBufferingCtr", 1.0, "Block-buffering CTR test provider") {

    var initializations = 0
        private set

    init {
        putService(object : Service(
            this, "Cipher", "AES/CTR/NoPadding", BufferingCtrSpi::class.java.name,
            emptyList(), emptyMap()
        ) {
            override fun newInstance(constructorParameter: Any?): Any =
                BufferingCtrSpi(Cipher.getInstance(TRANSFORMATION, baseline)) {
                    initializations++
                }
        })
    }

    companion object {
        const val TRANSFORMATION = "AES/CTR/NoPadding"

        fun <T> withProvider(block: (BufferingCtrProvider) -> T): T = synchronized(this) {
            val baseline = Cipher.getInstance(TRANSFORMATION).provider
            val provider = BufferingCtrProvider(baseline)
            check(Security.getProvider(provider.name) == null)
            check(Security.insertProviderAt(provider, 1) == 1)
            try {
                check(Cipher.getInstance(TRANSFORMATION).provider === provider)
                block(provider)
            } finally {
                Security.removeProvider(provider.name)
                check(Security.getProvider(provider.name) == null)
            }
        }
    }
}

private class BufferingCtrSpi(
    private val delegate: Cipher,
    private val onInit: () -> Unit
) : CipherSpi() {
    private var pending = ByteArray(0)

    override fun engineSetMode(mode: String) = require(mode.equals("CTR", true))
    override fun engineSetPadding(padding: String) = require(padding.equals("NoPadding", true))
    override fun engineGetBlockSize(): Int = 16
    override fun engineGetOutputSize(inputLen: Int): Int = pending.size + inputLen
    override fun engineGetIV(): ByteArray = delegate.iv
    override fun engineGetParameters(): AlgorithmParameters? = delegate.parameters

    override fun engineInit(opmode: Int, key: Key, random: SecureRandom?) {
        delegate.init(opmode, key, random)
        reset()
    }

    override fun engineInit(opmode: Int, key: Key, params: AlgorithmParameterSpec?, random: SecureRandom?) {
        delegate.init(opmode, key, params, random)
        reset()
    }

    override fun engineInit(opmode: Int, key: Key, params: AlgorithmParameters?, random: SecureRandom?) {
        delegate.init(opmode, key, params, random)
        reset()
    }

    private fun reset() {
        pending = ByteArray(0)
        onInit()
    }

    override fun engineUpdate(input: ByteArray, inputOffset: Int, inputLen: Int): ByteArray? {
        pending += input.copyOfRange(inputOffset, inputOffset + inputLen)
        val size = pending.size / 16 * 16
        if (size == 0) return null
        val output = delegate.update(pending, 0, size)
        pending = pending.copyOfRange(size, pending.size)
        return output
    }

    override fun engineUpdate(
        input: ByteArray, inputOffset: Int, inputLen: Int, output: ByteArray, outputOffset: Int
    ): Int {
        val size = (pending.size + inputLen) / 16 * 16
        if (output.size - outputOffset < size) throw ShortBufferException()
        val bytes = engineUpdate(input, inputOffset, inputLen) ?: return 0
        bytes.copyInto(output, outputOffset)
        return bytes.size
    }

    override fun engineDoFinal(input: ByteArray?, inputOffset: Int, inputLen: Int): ByteArray {
        val remaining = pending + (input?.copyOfRange(inputOffset, inputOffset + inputLen) ?: ByteArray(0))
        val output = delegate.doFinal(remaining)
        pending = ByteArray(0)
        return output
    }

    override fun engineDoFinal(
        input: ByteArray?, inputOffset: Int, inputLen: Int, output: ByteArray, outputOffset: Int
    ): Int {
        if (output.size - outputOffset < engineGetOutputSize(inputLen)) throw ShortBufferException()
        val bytes = engineDoFinal(input, inputOffset, inputLen)
        bytes.copyInto(output, outputOffset)
        return bytes.size
    }
}
