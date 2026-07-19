package com.dmvp.app.security

/**
 * Pure Kotlin BLAKE3 implementation
 * TDD v5 Phase 1 Step 1.4
 * 
 * BLAKE3 is a cryptographic hash function based on ChaCha/Bao.
 * This is a minimal implementation for Android without external dependencies.
 * 
 * Reference: https://github.com/BLAKE3-team/BLAKE3-specs/blob/master/blake3.pdf
 */
object Blake3 {
    
    // BLAKE3 constants
    private const val BLOCK_LEN = 64
    private const val CHUNK_LEN = 1024
    private const val KEY_LEN = 32
    private const val OUT_LEN = 32
    
    // Flags
    private const val CHUNK_START = 1
    private const val CHUNK_END = 2
    private const val PARENT = 4
    private const val ROOT = 8
    
    // IV from BLAKE2s
    private val IV = intArrayOf(
        0x6A09E667.toInt(), 0xBB67AE85.toInt(),
        0x3C6EF372.toInt(), 0xA54FF53A.toInt(),
        0x510E527F.toInt(), 0x9B05688C.toInt(),
        0x1F83D9AB.toInt(), 0x5BE0CD19.toInt()
    )
    
    // Message permutation schedule
    private val MSG_SCHEDULE = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
        intArrayOf(2, 6, 3, 10, 7, 0, 4, 13, 1, 11, 12, 5, 9, 14, 15, 8),
        intArrayOf(3, 4, 10, 12, 13, 2, 7, 14, 6, 5, 9, 0, 11, 15, 8, 1),
        intArrayOf(10, 7, 12, 9, 14, 3, 13, 15, 4, 0, 11, 2, 5, 8, 1, 6),
        intArrayOf(12, 13, 9, 11, 15, 10, 14, 8, 7, 2, 5, 3, 0, 1, 6, 4),
        intArrayOf(9, 14, 11, 5, 8, 12, 15, 1, 13, 3, 0, 10, 2, 6, 4, 7),
        intArrayOf(11, 15, 5, 0, 1, 9, 8, 6, 14, 10, 2, 12, 3, 4, 7, 13)
    )
    
    /**
     * Hash data using BLAKE3
     */
    fun hash(data: ByteArray): ByteArray {
        return hash(data, OUT_LEN)
    }
    
    /**
     * Hash data using BLAKE3 with custom output length
     */
    fun hash(data: ByteArray, outLen: Int): ByteArray {
        if (data.isEmpty()) {
            return hashChunk(ByteArray(0), 0, 0, CHUNK_START or CHUNK_END, IV)
        }
        
        var chunkStart = 0
        val chunks = mutableListOf<ByteArray>()
        
        while (chunkStart < data.size) {
            val chunkEnd = minOf(chunkStart + CHUNK_LEN, data.size)
            val chunk = data.copyOfRange(chunkStart, chunkEnd)
            val chunkIndex = chunkStart / CHUNK_LEN
            
            var flags = 0
            if (chunkStart == 0) flags = flags or CHUNK_START
            if (chunkEnd == data.size) flags = flags or CHUNK_END
            
            val chunkHash = hashChunk(chunk, chunkIndex.toLong(), 0, flags, IV)
            chunks.add(chunkHash)
            
            chunkStart = chunkEnd
        }
        
        // If only one chunk, it's the root
        if (chunks.size == 1) {
            return chunks[0]
        }
        
        // Merge chunks in a tree
        while (chunks.size > 1) {
            val newChunks = mutableListOf<ByteArray>()
            var i = 0
            while (i < chunks.size) {
                if (i + 1 < chunks.size) {
                    val parent = parentHash(chunks[i], chunks[i + 1], IV)
                    newChunks.add(parent)
                    i += 2
                } else {
                    newChunks.add(chunks[i])
                    i++
                }
            }
            chunks.clear()
            chunks.addAll(newChunks)
        }
        
        return chunks[0]
    }
    
    private fun hashChunk(chunk: ByteArray, chunkIndex: Long, keyIndex: Int, flags: Int, cv: IntArray): ByteArray {
        val blockCount = (chunk.size + BLOCK_LEN - 1) / BLOCK_LEN
        var state = cv.copyOf()
        
        for (blockIdx in 0 until blockCount) {
            val blockStart = blockIdx * BLOCK_LEN
            val blockEnd = minOf(blockStart + BLOCK_LEN, chunk.size)
            val block = chunk.copyOfRange(blockStart, blockEnd)
            
            var blockFlags = flags
            if (blockIdx == 0) blockFlags = blockFlags or CHUNK_START
            if (blockIdx == blockCount - 1) blockFlags = blockFlags or CHUNK_END
            
            state = compress(state, block, chunkIndex, blockEnd, blockFlags)
        }
        
        return state.toByteArray()
    }
    
    private fun parentHash(left: ByteArray, right: ByteArray, cv: IntArray): ByteArray {
        val block = ByteArray(64)
        System.arraycopy(left, 0, block, 0, 32)
        System.arraycopy(right, 0, block, 32, 32)
        
        val state = compress(cv, block, 0, 64, PARENT)
        return state.toByteArray()
    }
    
    private fun compress(cv: IntArray, block: ByteArray, counter: Long, blockLen: Int, flags: Int): IntArray {
        val m = IntArray(16)
        for (i in 0 until 16) {
            val offset = i * 4
            m[i] = (block[offset].toInt() and 0xff) or
                   ((block[offset + 1].toInt() and 0xff) shl 8) or
                   ((block[offset + 2].toInt() and 0xff) shl 16) or
                   ((block[offset + 3].toInt() and 0xff) shl 24)
        }
        
        val state = IntArray(16)
        state[0] = cv[0]
        state[1] = cv[1]
        state[2] = cv[2]
        state[3] = cv[3]
        state[4] = cv[4]
        state[5] = cv[5]
        state[6] = cv[6]
        state[7] = cv[7]
        state[8] = IV[0]
        state[9] = IV[1]
        state[10] = IV[2]
        state[11] = IV[3]
        state[12] = counter.toInt()
        state[13] = (counter shr 32).toInt()
        state[14] = blockLen
        state[15] = flags
        
        // 7 rounds
        for (round in 0 until 7) {
            round(state, m, round)
        }
        
        // XOR first half with second half
        val result = IntArray(8)
        for (i in 0 until 8) {
            result[i] = state[i] xor state[i + 8]
        }
        
        return result
    }
    
    private fun round(state: IntArray, m: IntArray, round: Int) {
        val schedule = MSG_SCHEDULE[round]
        
        // Column step
        g(state, 0, 4, 8, 12, m[schedule[0]], m[schedule[1]])
        g(state, 1, 5, 9, 13, m[schedule[2]], m[schedule[3]])
        g(state, 2, 6, 10, 14, m[schedule[4]], m[schedule[5]])
        g(state, 3, 7, 11, 15, m[schedule[6]], m[schedule[7]])
        
        // Diagonal step
        g(state, 0, 5, 10, 15, m[schedule[8]], m[schedule[9]])
        g(state, 1, 6, 11, 12, m[schedule[10]], m[schedule[11]])
        g(state, 2, 7, 8, 13, m[schedule[12]], m[schedule[13]])
        g(state, 3, 4, 9, 14, m[schedule[14]], m[schedule[15]])
    }
    
    private fun g(state: IntArray, a: Int, b: Int, c: Int, d: Int, x: Int, y: Int) {
        state[a] = state[a] + state[b] + x
        state[d] = rotr32(state[d] xor state[a], 16)
        state[c] = state[c] + state[d]
        state[b] = rotr32(state[b] xor state[c], 12)
        state[a] = state[a] + state[b] + y
        state[d] = rotr32(state[d] xor state[a], 8)
        state[c] = state[c] + state[d]
        state[b] = rotr32(state[b] xor state[c], 7)
    }
    
    private fun rotr32(x: Int, n: Int): Int {
        return (x ushr n) or (x shl (32 - n))
    }
    
    private fun IntArray.toByteArray(): ByteArray {
        val result = ByteArray(size * 4)
        for (i in indices) {
            result[i * 4] = (this[i] and 0xff).toByte()
            result[i * 4 + 1] = ((this[i] shr 8) and 0xff).toByte()
            result[i * 4 + 2] = ((this[i] shr 16) and 0xff).toByte()
            result[i * 4 + 3] = ((this[i] shr 24) and 0xff).toByte()
        }
        return result
    }
}
