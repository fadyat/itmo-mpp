class CAS2Descriptor<T>(
    private val a: Ref<T>,
    private val expectedA: T,
    private val updateA: T,
    private val b: Ref<T>,
    private val expectedB: T,
    private val updateB: T,
) : Descriptor() {
    private fun rdcss(): Boolean {
        val descriptor = RDCSSDescriptor(b, expectedB, this, this)
        if (b.v.value == this || b.compareAndSet(expectedB, descriptor)) {
            descriptor.complete()
            return descriptor.isSuccess
        }

        return false
    }

    override fun complete() {
        outcomeCompareAndSet(if (rdcss()) Outcome.SUCCESS else Outcome.FAILURE)

        if (isSuccess) {
            a.v.compareAndSet(this, updateA)
            b.v.compareAndSet(this, updateB)
        } else {
            a.v.compareAndSet(this, expectedA)
            b.v.compareAndSet(this, expectedB)
        }
    }
}
