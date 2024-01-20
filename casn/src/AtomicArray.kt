class AtomicArray<E>(size: Int, initialValue: E) {
    private val a = arrayOfNulls<Ref<E>>(size)

    init {
        for (i in 0 until size) a[i] = Ref(initialValue)
    }

    fun get(index: Int) = a[index]!!.value

    fun set(index: Int, v: E) {
        a[index]!!.value = v
    }

    fun cas(
        index: Int,
        expected: E,
        update: E,
    ) = a[index]!!.compareAndSet(expected, update)

    fun cas2(
        index1: Int,
        expected1: E,
        update1: E,
        index2: Int,
        expected2: E,
        update2: E,
    ): Boolean {
        if (index1 == index2) {
            return if (expected1 == expected2) cas(index2, expected2, update2) else false
        }

        val (index, expected, descriptor) = order(index1, expected1, update1, index2, expected2, update2)

        return if (a[index]!!.compareAndSet(expected, descriptor)) {
            descriptor.complete()
        } else {
            false
        }
    }

    private fun order(
        index1: Int,
        expected1: E,
        update1: E,
        index2: Int,
        expected2: E,
        update2: E,
    ): Triple<Int, E, Descriptor> {
        return if (index1 < index2) {
            Triple(
                index1,
                expected1,
                CAS2Descriptor(a[index1]!!, expected1, update1, a[index2]!!, expected2, update2),
            )
        } else {
            Triple(
                index2,
                expected2,
                CAS2Descriptor(a[index2]!!, expected2, update2, a[index1]!!, expected1, update1),
            )
        }
    }
}
