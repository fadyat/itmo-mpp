import java.util.concurrent.*
import java.util.concurrent.atomic.*

/**
 * @author Fadeyev Artyom
 */
open class TreiberStackWithElimination<E> : Stack<E> {
    private val stack = TreiberStack<E>()
    private val eliminationArray = AtomicReferenceArray<E?>(ELIMINATION_ARRAY_SIZE)

    override fun push(element: E) {
        if (tryPushElimination(element)) return
        stack.push(element)
    }

    protected open fun tryPushElimination(element: E): Boolean {
        val idx = randomCellIndex()

        if (!eliminationArray.compareAndSet(idx, CELL_STATE_EMPTY, element)) {

            // if expected value is changed, it means that one
            // of the pushing threads also selected this cell and stored
            // its element there, so we can't use this cell for pushing
            return false
        }

        // waiting for other threads to pop element from the cell
        repeat(ELIMINATION_WAIT_CYCLES) {
            if (eliminationArray.get(idx) == CELL_STATE_EMPTY) {
                return true
            }
        }

        // popping thread also can CAS the cell right now, so we
        // need inverted condition here
        return !eliminationArray.compareAndSet(idx, element, CELL_STATE_EMPTY)
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val idx = randomCellIndex()
        val stored = eliminationArray.get(idx)

        // if cell is empty, we can't pop anything
        if (stored == CELL_STATE_EMPTY) {
            return null
        }

        // if value still here, we are changing state of the cell
        // to retrieved and returning the value
        if (eliminationArray.compareAndSet(idx, stored, CELL_STATE_EMPTY)) {
            return stored
        }

        // otherwise, it means that time of life of the value
        // in the cell is over, and we can't use it for popping
        return null
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(eliminationArray.length())

    companion object {
        private const val ELIMINATION_ARRAY_SIZE = 2 // Do not change!
        private const val ELIMINATION_WAIT_CYCLES = 1 // Do not change!

        // Initially, all cells are in EMPTY state.
        private val CELL_STATE_EMPTY = null
    }
}
