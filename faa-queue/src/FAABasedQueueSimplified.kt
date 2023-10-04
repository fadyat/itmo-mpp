import java.util.concurrent.atomic.*
import kotlin.math.*

/**
 * @author Fadeyev Artyom
 */
class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = AtomicReferenceArray<Any?>(1024) // conceptually infinite array
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    override fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.getAndIncrement().toInt()
            if (infiniteArray.compareAndSet(i, null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (isEmpty()) {
                return null
            }

            val i = deqIdx.getAndIncrement().toInt()
            if (infiniteArray.compareAndSet(i, null, POISONED)) {
                continue
            }

            return infiniteArray.getAndSet(i, null) as E
        }
    }

    private fun isEmpty(): Boolean {
        // idea: is to make a "snapshot" of values of both pointers and compare them
        //
        // using double collect technique to make comparison atomic
        // also can be `deqIdx.get() >= enqIdx.get()`, but order of taking values is important;
        // it's easy to make a mistake and get a wrong result

        while (true) {
            val deq = deqIdx.get()
            val enq = enqIdx.get()
            if (deq != deqIdx.get()) {
                continue
            }

            return deq >= enq
        }
    }

    override fun validate() {
        for (i in 0 until min(deqIdx.get().toInt(), enqIdx.get().toInt())) {
            check(infiniteArray[i] == null || infiniteArray[i] == POISONED) {
                "`infiniteArray[$i]` must be `null` or `POISONED` with `deqIdx = ${deqIdx.get()}` at the end of the execution"
            }
        }
        for (i in max(deqIdx.get().toInt(), enqIdx.get().toInt()) until infiniteArray.length()) {
            check(infiniteArray[i] == null || infiniteArray[i] == POISONED) {
                "`infiniteArray[$i]` must be `null` or `POISONED` with `enqIdx = ${enqIdx.get()}` at the end of the execution"
            }
        }
    }
}

private val POISONED = Any()
