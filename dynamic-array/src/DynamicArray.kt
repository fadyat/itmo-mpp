package mpp.dynamicarray

import kotlinx.atomicfu.*

interface DynamicArray<E> {
    /**
     * Returns the element located in the cell [idx],
     * or throws [IllegalArgumentException] if [idx]
     * exceeds the [size] of this array.
     */
    fun get(idx: Int): E

    /**
     * Puts the specified [x] into the cell [idx],
     * or throws [IllegalArgumentException] if [idx]
     * exceeds the [size] of this array.
     */
    fun put(idx: Int, x: E)

    /**
     * Adds the specified [x] to this array
     * increasing its [size].
     */
    fun pushBack(x: E)

    /**
     * Returns the current size of this array,
     * it increases with [pushBack] invocations.
     */
    val size: Int
}

private class Copied<E>(val x: E)

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    @Suppress("UNCHECKED_CAST")
    override fun get(idx: Int): E {
        var activeCore = this.core.value
        var previous: E? = null

        while (true) {
            when (val have = activeCore.get(idx)) {
                is Copied<*> -> {
                    previous = have.x as E?
                    activeCore = activeCore.nextCore.value!!
                }

                null -> {
                    require(previous != null)
                    return previous
                }

                else -> return have as E
            }
        }
    }

    override fun put(idx: Int, x: E) {
        var activeCore = this.core.value

        while (true) {
            when (val have = activeCore.get(idx)) {
                is Copied<*> -> activeCore = activeCore.nextCore.value!!
                else -> if (activeCore.cas(idx, have, x)) return
            }
        }
    }

    override fun pushBack(x: E) {
        while (true) {
            val activeCore = core.value
            var (idx, cap) = activeCore.size to activeCore.capacity

            while (idx < cap) {
                if (activeCore.cas(idx, null, x)) {
                    activeCore.incSize(idx)
                    return
                } else {
                    activeCore.incSize(idx) // helping
                }

                idx++
            }

            val newCore = Core<E>(cap * EXPANSION_FACTOR, idx + 1)
            newCore.array[idx].value = x

            if (!activeCore.nextCore.compareAndSet(null, newCore)) {
                copy(activeCore, activeCore.nextCore.value!!) // helping
            } else {
                copy(activeCore, newCore)
                return
            }
        }
    }

    private fun copy(activeCore: Core<E>, newCore: Core<E>) {
        for (idx in 0 until activeCore.capacity) {
            while (true) {
                when (val have = activeCore.get(idx)) {
                    is Copied<*> -> {
                        newCore.cas(idx, null, have.x) // helping
                        break
                    }

                    else -> {
                        if (activeCore.cas(idx, have, Copied(have))) {
                            newCore.cas(idx, null, have)
                            break
                        }
                    }
                }
            }
        }

        core.compareAndSet(activeCore, newCore)
    }

    override val size: Int
        get() = core.value.size
}

private class Core<E>(
    val capacity: Int,
    initialSize: Int = 0,
) {
    val array = atomicArrayOfNulls<Any>(capacity)
    private val _size = atomic(initialSize)
    val nextCore: AtomicRef<Core<E>?> = atomic(null)

    val size: Int
        get() = _size.value

    fun get(index: Int): Any? {
        require(index < size)
        return array[index].value
    }

    fun cas(index: Int, expect: Any?, update: Any?): Boolean {
        return array[index].compareAndSet(expect, update)
    }

    fun incSize(have: Int): Boolean {
        return _size.compareAndSet(have, have + 1)
    }
}

private const val EXPANSION_FACTOR = 2
private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME