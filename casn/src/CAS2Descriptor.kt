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
            return descriptor.complete()
        }

        return false
    }

    override fun complete(): Boolean {
        if (rdcss()) outcomeCompareAndSet(Outcome.SUCCESS)

        val (updA, updB) = when (isSuccess) {
            true -> updateA to updateB
            else -> expectedA to expectedB
        }

        a.v.compareAndSet(this, updA)
        b.v.compareAndSet(this, updB)
        return isSuccess
    }
}
