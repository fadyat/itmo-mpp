import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Bank implementation.
 *
 * @author Fadeyev Artyom
 */
class BankImpl(n: Int) : Bank {
    private val accounts: Array<Account> = Array(n) { Account() }

    override val numberOfAccounts: Int
        get() = accounts.size

    override fun getAmount(index: Int): Long {
        accounts[index].lock.withLock {
            return accounts[index].amount
        }
    }

    override val totalAmount: Long
        get() {
            val sum: Long
            for (i in accounts.indices) {
                accounts[i].lock.lock()
            }

            try {
                sum = accounts.sumOf { it.amount }
            } finally {
                for (i in accounts.indices) {
                    accounts[i].lock.unlock()
                }
            }

            return sum
        }

    override fun deposit(index: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        accounts[index].lock.withLock {
            return depositUnsafe(index, amount)
        }
    }

    private fun depositUnsafe(index: Int, amount: Long): Long {
        val account = accounts[index]
        check(!(amount > Bank.MAX_AMOUNT || account.amount + amount > Bank.MAX_AMOUNT)) { "Overflow" }
        account.amount += amount
        return account.amount
    }

    override fun withdraw(index: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }

        accounts[index].lock.withLock {
            return withdrawUnsafe(index, amount)
        }
    }

    private fun withdrawUnsafe(index: Int, amount: Long): Long {
        val account = accounts[index]
        check(account.amount - amount >= 0) { "Underflow" }
        account.amount -= amount
        return account.amount
    }

    override fun transfer(fromIndex: Int, toIndex: Int, amount: Long) {
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromIndex != toIndex) { "fromIndex == toIndex" }

        val lockOrder = listOf(fromIndex, toIndex).sorted()
        for (i in lockOrder) {
            accounts[i].lock.lock()
        }
        try {
            transferUnsafe(fromIndex, toIndex, amount)
        } finally {
            for (i in lockOrder) {
                accounts[i].lock.unlock()
            }
        }
    }

    private fun transferUnsafe(fromIndex: Int, toIndex: Int, amount: Long) {
        val from = accounts[fromIndex]
        val to = accounts[toIndex]
        check(amount <= from.amount) { "Underflow" }
        check(!(amount > Bank.MAX_AMOUNT || to.amount + amount > Bank.MAX_AMOUNT)) { "Overflow" }
        from.amount -= amount
        to.amount += amount
    }

    /**
     * Private account data structure.
     */
    class Account {
        /**
         * Amount of funds in this account.
         */
        var amount: Long = 0
        var lock: ReentrantLock = ReentrantLock()
    }
}