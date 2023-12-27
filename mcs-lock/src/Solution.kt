import java.util.concurrent.atomic.AtomicReference

class Solution(
    private val env: Environment,
) : Lock<Solution.Node> {
    private val tail: AtomicReference<Node?> = AtomicReference(null)

    override fun lock(): Node {
        val my = Node()

        val prev = tail.getAndSet(my) ?: return my
        prev.next.value = my
        while (my.locked.value) {
            env.park()
        }

        return my
    }

    override fun unlock(node: Node) {
        if (node.next.value == null) {
            if (tail.compareAndSet(node, null)) {
                return
            }

            while (node.next.value == null) {
                // waiting for the next node to appear
            }
        }

        node.next.value!!.locked.value = false
        env.unpark(node.next.value!!.thread)
    }

    class Node {
        val thread: Thread = Thread.currentThread()
        val next = AtomicReference<Node?>(null)
        val locked = AtomicReference(true)
    }
}