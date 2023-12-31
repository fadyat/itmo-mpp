import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

/**
 * Int-to-Int hash map with open addressing and linear probes.
 */
class IntIntHashMap {
    private val _core: AtomicRef<Core> = atomic(Core(INITIAL_CAPACITY))

    private val core
        get() = _core.value

    /**
     * Returns value for the corresponding key or zero if this key is not present.
     *
     * @param key a positive key.
     * @return value for the corresponding or zero if this key is not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    operator fun get(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        return toValue(core.getInternal(key))
    }

    /**
     * Changes value for the corresponding key and returns old value or zero if key was not present.
     *
     * @param key   a positive key.
     * @param value a positive value.
     * @return old value or zero if this key was not present.
     * @throws IllegalArgumentException if key or value are not positive, or value is equal to
     * [Integer.MAX_VALUE] which is reserved.
     */
    fun put(
        key: Int,
        value: Int,
    ): Int {
        require(key > 0) { "Key must be positive: $key" }
        require(isValue(value)) { "Invalid value: $value" }
        return toValue(putAndRehashWhileNeeded(key, value))
    }

    /**
     * Removes value for the corresponding key and returns old value or zero if key was not present.
     *
     * @param key a positive key.
     * @return old value or zero if this key was not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    fun remove(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        return toValue(putAndRehashWhileNeeded(key, DEL_VALUE))
    }

    private fun putAndRehashWhileNeeded(
        key: Int,
        value: Int,
    ): Int {
        while (true) {
            val activeCore = core
            val oldValue = activeCore.putInternal(key, value)
            if (oldValue != NEEDS_REHASH) return oldValue
            _core.compareAndSet(activeCore, activeCore.rehash())
        }
    }

    private class Core(capacity: Int) {
        val map = AtomicIntArray(2 * capacity)
        val nextCore: AtomicRef<Core?> = atomic(null)
        val shift: Int

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)
        }

        fun getInternal(key: Int): Int {
            var (index, probes) = index(key) to 0

            while (true) {
                when (map[index].value) {
                    key -> break
                    NULL_KEY -> return NULL_VALUE
                    else -> if (++probes >= MAX_PROBES) return NULL_VALUE
                }

                index = nextIndex(index)
            }

            return when (val stored = map[index + 1].value) {
                DEL_VALUE -> NULL_VALUE
                COPIED -> nextCore.value!!.getInternal(key)
                else -> if (isFrozen(stored)) defrost(stored) else stored
            }
        }

        fun putInternal(
            key: Int,
            value: Int,
        ): Int = putRehashing(key, value, true)

        private fun putRehashing(
            key: Int,
            value: Int,
            overwriteValue: Boolean,
        ): Int {
            var (index, probes) = index(key) to 0

            while (true) {
                when (map[index].value) {
                    key -> break
                    NULL_KEY -> if (map[index].compareAndSet(NULL_KEY, key)) break else continue
                    else -> if (++probes >= MAX_PROBES) return NEEDS_REHASH
                }

                index = nextIndex(index)
            }

            while (true) {
                when (val previous = map[index + 1].value) {
                    COPIED -> return nextCore.value!!.putInternal(key, value)
                    else -> {
                        if (isFrozen(previous)) {
                            doCopy(index)
                            return nextCore.value!!.putInternal(key, value)
                        }

                        // due to redirection of put operations when copying the map,
                        // the previous value can be changed and retry of the
                        // operation will overwrite more relevant value.
                        if (!overwriteValue && previous != NULL_VALUE) return previous
                        if (map[index + 1].compareAndSet(previous, value)) return previous
                    }
                }
            }
        }

        fun rehash(): Core {
            if (nextCore.value == null) {
                nextCore.compareAndSet(null, Core(EXPANSION_FACTOR * map.size))
            }

            for (index in 0 until map.size step 2) {
                doCopy(index)
            }

            return nextCore.value!!
        }

        private fun doCopy(index: Int) {
            while (true) {
                val (key, value) = map[index].value to map[index + 1].value
                when (value) {
                    COPIED -> break
                    DEL_VALUE, NULL_VALUE -> {
                        if (map[index + 1].compareAndSet(value, COPIED)) break
                    }
                }

                if (!isFrozen(value) && !map[index + 1].compareAndSet(value, freeze(value))) {
                    continue
                }

                nextCore.value!!.putRehashing(key, defrost(value), false)
                map[index + 1].compareAndSet(freeze(value), COPIED)
                break
            }
        }

        private fun index(key: Int): Int = (key * MAGIC ushr shift) * 2

        private fun nextIndex(index: Int): Int = if (index == 0) map.size - 2 else index - 2

        private fun freeze(value: Int): Int = value or Int.MIN_VALUE

        private fun defrost(value: Int): Int = value and Int.MAX_VALUE

        private fun isFrozen(value: Int): Boolean = value < 0
    }
}

private const val EXPANSION_FACTOR = 2
private const val MAGIC = -0x61c88647 // golden ratio
private const val INITIAL_CAPACITY = 2 // !!! DO NOT CHANGE INITIAL CAPACITY !!!
private const val MAX_PROBES = 8 // max number of probes to find an item
private const val NULL_KEY = 0 // missing key (initial value)
private const val NULL_VALUE = 0 // missing value (initial value)
private const val DEL_VALUE = Int.MAX_VALUE // mark for removed value
private const val NEEDS_REHASH = -1 // returned by `putInternal` to indicate that rehash is needed
private const val COPIED = Int.MIN_VALUE

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int = if (isValue(value)) value else 0
