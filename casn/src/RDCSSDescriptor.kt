import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.loop

enum class Outcome {
    UNDECIDED,
    SUCCESS,
    FAILURE,
}

abstract class Descriptor {
    private val state = atomic(Outcome.UNDECIDED)

    val isSuccess: Boolean
        get() = state.value == Outcome.SUCCESS

    val isUndecided: Boolean
        get() = state.value == Outcome.UNDECIDED

    protected fun outcomeCompareAndSet(update: Outcome) = state.compareAndSet(Outcome.UNDECIDED, update)

    abstract fun complete(): Boolean
}

class Ref<T>(initial: T) {
    val v = atomic<Any?>(initial)

    @Suppress("UNCHECKED_CAST")
    var value: T
        get() {
            v.loop {
                when (it) {
                    is Descriptor -> it.complete()
                    else -> return it as T
                }
            }
        }
        set(upd) {
            v.loop {
                when (it) {
                    is Descriptor -> it.complete()
                    else -> if (v.compareAndSet(it, upd)) return
                }
            }
        }

    fun compareAndSet(
        expected: Any?,
        update: Any?,
    ): Boolean {
        v.loop {
            when (it) {
                is Descriptor -> it.complete()
                expected -> if (v.compareAndSet(it, update)) return true
                else -> return false
            }
        }
    }
}

class RDCSSDescriptor<T>(
    private val a: Ref<T>,
    private val expectedA: T,
    private val updateA: Any?,
    private val descriptor: Descriptor,
) : Descriptor() {
    override fun complete(): Boolean {
        outcomeCompareAndSet(if (descriptor.isUndecided) Outcome.SUCCESS else Outcome.FAILURE)

        val update = if (isSuccess) updateA else expectedA
        a.v.compareAndSet(this, update)
        return isSuccess
    }
}
