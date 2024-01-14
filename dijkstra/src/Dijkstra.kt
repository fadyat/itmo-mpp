package dijkstra

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.reentrantLock
import java.util.PriorityQueue
import java.util.concurrent.Phaser
import kotlin.Comparator
import kotlin.concurrent.thread
import kotlin.random.Random

private val NODE_DISTANCE_COMPARATOR = Comparator<Node> { o1, o2 -> o1!!.distance.compareTo(o2!!.distance) }

fun shortestPathParallel(start: Node) {
    start.distance = 0

    val workers = Runtime.getRuntime().availableProcessors()
    val q = MultiQueue(workers)
    q.offer(start)

    val onFinish = Phaser(workers + 1)
    repeat(workers) {
        thread {
            while (!q.isEmpty()) {
                val c = q.poll() ?: continue

                for (e in c.outgoingEdges) {
                    while (true) {
                        val (old, new) = e.to.distance to c.distance + e.weight
                        if (new >= old) {
                            break
                        }

                        if (e.to.casDistance(old, new)) {
                            q.offer(e.to)
                            break
                        }
                    }
                }
                q.markCompleted()
            }
            onFinish.arrive()
        }
    }

    onFinish.arriveAndAwaitAdvance()
}

class LockedPriorityQueue(cmp: Comparator<Node>) {
    private val queue = PriorityQueue(cmp)
    private val lock = reentrantLock()

    fun tryOffer(x: Node): Boolean {
        val locked = lock.tryLock()
        if (locked) {
            try {
                queue.offer(x)
            } finally {
                lock.unlock()
            }
        }

        return locked
    }

    fun tryPoll(): Node? {
        val locked = lock.tryLock()
        if (locked) {
            try {
                return queue.poll()
            } finally {
                lock.unlock()
            }
        }

        return null
    }

    fun peek(): Node? = queue.peek()
}

class MultiQueue(
    workers: Int,
    cmp: Comparator<Node> = NODE_DISTANCE_COMPARATOR,
) {
    private val queues = MutableList(QUEUES_FACTOR * workers) { LockedPriorityQueue(cmp) }
    private val generator = RandomGenerator()
    private val processingOperations = atomic(0)

    private fun markStarted() = processingOperations.incrementAndGet()

    fun markCompleted() = processingOperations.decrementAndGet()

    fun isEmpty(): Boolean = processingOperations.value == 0

    fun offer(x: Node) {
        markStarted()

        while (true) {
            val idx = generator.nextIndex(queues.size)
            if (queues[idx].tryOffer(x)) {
                break
            }
        }
    }

    fun poll(): Node? {
        while (true) {
            val (first, second) = generator.nextTwoIndexes(queues.size)
            val q1 = queues[first]
            val q2 = queues[second]

            val (top1, top2) = q1.peek() to q2.peek()
            return when {
                top1 == null && top2 == null -> return null
                top1 == null -> q2
                top2 == null -> q1
                NODE_DISTANCE_COMPARATOR.compare(top1, top2) <= 0 -> q1
                else -> q2
            }.tryPoll() ?: continue
        }
    }
}

class RandomGenerator {
    private val randomGenerator = Random(0)

    fun nextIndex(limit: Int): Int = randomGenerator.nextInt(limit)

    fun nextTwoIndexes(limit: Int): Pair<Int, Int> {
        var (first, second) = nextIndex(limit) to nextIndex(limit)
        while (first == second) {
            second = nextIndex(limit)
        }

        return first to second
    }
}

const val QUEUES_FACTOR = 3
