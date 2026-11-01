package dev.jasper.ishav2.agent

import dev.jasper.ishav2.data.BudgetCategories
import dev.jasper.ishav2.data.BudgetData
import dev.jasper.ishav2.data.BudgetStore
import dev.jasper.ishav2.data.Transaction
import dev.jasper.ishav2.data.TxType
import dev.jasper.ishav2.data.formatMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class BudgetTest {

    private fun add(text: String) = BudgetParser.parse(text) as BudgetCommand.Add

    @Test
    fun parsesExpenses() {
        val lunch = add("Spent ₱150 on lunch at Jollibee")
        assertEquals(TxType.EXPENSE, lunch.type)
        assertEquals(150.0, lunch.amount, 0.0)
        assertEquals("Food", lunch.category)
        assertEquals("lunch at jollibee", lunch.note)

        val bill = add("paid the electricity bill 1,850.50")
        assertEquals(1850.5, bill.amount, 0.001)
        assertEquals("Bills", bill.category)

        val grab = add("I spent 2k on grab yesterday")
        assertEquals(2000.0, grab.amount, 0.0)
        assertEquals("Transport", grab.category)
        assertEquals(1, grab.daysAgo)

        assertEquals("School", add("bought 85 pesos photocopy").category)
        assertEquals("Other", add("spent 300 on something random").category)
        assertEquals(900.0, add("bought 3 shirts for 900").amount, 0.0)
        assertEquals(250.0, add("paid ₱250 for 2 tickets").amount, 0.0)
    }

    @Test
    fun parsesIncome() {
        val allowance = add("got 3000 allowance")
        assertEquals(TxType.INCOME, allowance.type)
        assertEquals("Allowance", allowance.category)
        assertEquals(TxType.INCOME, add("received php 15,000 salary").type)
    }

    @Test
    fun parsesBudgetsSummariesAndUndo() {
        assertEquals(BudgetCommand.SetBudget("Food", 3000.0), BudgetParser.parse("set food budget to 3000"))
        assertEquals(BudgetCommand.SetBudget(BudgetCategories.TOTAL, 8000.0), BudgetParser.parse("set my monthly budget to 8k"))
        assertEquals(BudgetCommand.SetBudget(BudgetCategories.TOTAL, 5000.0), BudgetParser.parse("set budget to 5000"))
        assertEquals(BudgetCommand.SetBudget("Transport", 1500.0), BudgetParser.parse("budget 1500 for transport"))
        assertEquals(BudgetCommand.Summary(BudgetCommand.Period.MONTH), BudgetParser.parse("How much is left?"))
        assertEquals(BudgetCommand.Summary(BudgetCommand.Period.TODAY), BudgetParser.parse("how much did I spend today"))
        assertEquals(BudgetCommand.Summary(BudgetCommand.Period.WEEK), BudgetParser.parse("how much have i spent this week?"))
        assertEquals(BudgetCommand.UndoLast, BudgetParser.parse("undo last expense"))
    }

    @Test
    fun leavesOrdinaryChatAlone() {
        assertNull(BudgetParser.parse("got 3 assignments due tomorrow"))
        assertNull(BudgetParser.parse("spent 2 hours on my thesis"))
        assertNull(BudgetParser.parse("spend some time planning my week"))
        assertNull(BudgetParser.parse("buy 2 eggs on the way home"))
        assertNull(BudgetParser.parse("what is a budget?"))
        assertNull(BudgetParser.parse("got a question about my schedule"))
        assertNull(BudgetParser.parse("help me plan my day"))
    }

    @Test
    fun alertsFireOnceWhenCrossingThresholds() {
        val base = BudgetData(budgets = mapOf("Food" to 1000.0))
        val first = Transaction(type = TxType.EXPENSE, amount = 700.0, category = "Food")
        val afterFirst = base.copy(transactions = listOf(first))
        assertTrue(BudgetStore.crossedThresholds(base, afterFirst, first).isEmpty())

        val second = Transaction(type = TxType.EXPENSE, amount = 150.0, category = "Food")
        val afterSecond = afterFirst.copy(transactions = afterFirst.transactions + second)
        val warn = BudgetStore.crossedThresholds(afterFirst, afterSecond, second).single()
        assertEquals(false, warn.over)

        val third = Transaction(type = TxType.EXPENSE, amount = 200.0, category = "Food")
        val afterThird = afterSecond.copy(transactions = afterSecond.transactions + third)
        val over = BudgetStore.crossedThresholds(afterSecond, afterThird, third).single()
        assertEquals(true, over.over)

        val summary = BudgetStore.summarize(afterThird, YearMonth.now())
        assertEquals(1050.0, summary.expense, 0.0)
        assertTrue(summary.categories.single().over)
    }

    @Test
    fun formatsMoney() {
        assertEquals("₱1,500", formatMoney("₱", 1500.0))
        assertEquals("$12.50", formatMoney("$", 12.5))
    }
}
