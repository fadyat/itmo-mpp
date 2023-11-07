import java.util.concurrent.*
import java.util.concurrent.atomic.*

/**
 * @author Fadeyev Artyom
 */
class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>()
    private val combinerLock = AtomicBoolean(false)
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        if (combinerLock.compareAndSet(false, true)) {
            return enqueuePerform { queue.addLast(element) }
        }

        return enqueueWait(element)
    }

    private fun enqueuePerform(fn: () -> Unit) {
        fn()
        helpOthers()
        combinerLock.set(false)
    }

    private fun enqueueWait(element: E) {
        val idx = storeAtRandomCell(element)

        while (true) {
            if (tasksForCombiner.get(idx) is Enqueue) {
                return tasksForCombiner.set(idx, null)
            }

            if (combinerLock.compareAndSet(false, true)) {
                if (tasksForCombiner.get(idx) is Enqueue) {
                    return enqueuePerform { tasksForCombiner.set(idx, null) }
                }

                return enqueuePerform {
                    tasksForCombiner.set(idx, null)
                    queue.addLast(element)
                }
            }
        }
    }

    override fun dequeue(): E? {
        if (combinerLock.compareAndSet(false, true)) {
            return dequeuePerform { queue.removeFirstOrNull() }
        }

        return dequeueWait()
    }

    private fun dequeuePerform(fn: () -> E?): E? {
        val result = fn()
        helpOthers()
        combinerLock.set(false)
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun dequeueWait(): E? {
        val idx = storeAtRandomCell(Dequeue)

        while (true) {
            if (tasksForCombiner.get(idx) is Result<*>) {
                return (tasksForCombiner.getAndSet(idx, null) as Result<E?>).value
            }

            if (combinerLock.compareAndSet(false, true)) {
                if (tasksForCombiner.get(idx) is Result<*>) {
                    return dequeuePerform {
                        (tasksForCombiner.getAndSet(idx, null) as Result<E?>).value
                    }
                }

                return dequeuePerform {
                    tasksForCombiner.set(idx, null)
                    queue.removeFirstOrNull()
                }
            }
        }
    }

    private fun storeAtRandomCell(element: Any?): Int {
        while (true) {
            val idx = randomCellIndex()

            if (tasksForCombiner.compareAndSet(idx, null, element)) {
                return idx
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun helpOthers() {
        for (i in 0 until tasksForCombiner.length()) {
            when (tasksForCombiner.get(i)) {
                is Dequeue -> {
                    tasksForCombiner.set(i, Result(queue.removeFirstOrNull()))
                }

                is Result<*>, null, Enqueue -> {
                    continue
                }

                else -> {
                    queue.addLast(tasksForCombiner.getAndSet(i, Enqueue) as E)
                }
            }
        }
    }

    private fun randomCellIndex(): Int = ThreadLocalRandom.current().nextInt(tasksForCombiner.length())
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

private object Dequeue
private object Enqueue

private class Result<V>(
    val value: V
)