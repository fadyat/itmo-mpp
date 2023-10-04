import java.util.concurrent.atomic.*

/**
 * @author Fadeyev Artyom
 */
class FAABasedQueue<E> : Queue<E> {
    private val head = AtomicReference(Segment(0))
    private val tail = AtomicReference(head.get())
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.get()
            val i = enqIdx.getAndIncrement().toInt()

            val segment = getOrCreateSegment(curTail, i / SEGMENT_SIZE)
            moveForward(segment, false)

            if (segment.compareAndSet(i % SEGMENT_SIZE, null, element)) {
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

            val curHead = head.get()
            val i = deqIdx.getAndIncrement().toInt()

            val segment = getOrCreateSegment(curHead, i / SEGMENT_SIZE)
            moveForward(segment, true)

            if (segment.compareAndSet(i % SEGMENT_SIZE, null, POISONED)) {
                continue
            }

            return segment.getAndSet(i % SEGMENT_SIZE, null) as E
        }
    }

    private fun getOrCreateSegment(pos: Segment, id: Int): Segment {
        var segment = pos
        while (segment.id < id) {
            if (segment.next.get() != null) {
                segment = segment.next.get()!!
                continue
            }

            // if was unsuccessful, then another thread has already created
            // a segment and on another iteration we will get it
            segment.next.compareAndSet(null, Segment(segment.id + 1))
        }

        return segment
    }

    private fun moveForward(movingTo: Segment, isHead: Boolean) {
        val atomicRef = if (isHead) head else tail

        while (true) {
            val current = atomicRef.get()
            if (current.id >= movingTo.id) {
                return
            }

            if (atomicRef.compareAndSet(current, movingTo)) {
                return
            }
        }
    }

    private fun isEmpty(): Boolean {
        return deqIdx.get() >= enqIdx.get()
    }
}

private class Segment(val id: Long) {
    val next = AtomicReference<Segment?>(null)
    val cells = AtomicReferenceArray<Any?>(SEGMENT_SIZE)

    fun compareAndSet(index: Int, expected: Any?, new: Any?): Boolean = cells.compareAndSet(index, expected, new)
    fun getAndSet(index: Int, new: Any?): Any? = cells.getAndSet(index, new)
}

// DO NOT CHANGE THIS CONSTANT
private const val SEGMENT_SIZE = 2
private val POISONED = Any()
