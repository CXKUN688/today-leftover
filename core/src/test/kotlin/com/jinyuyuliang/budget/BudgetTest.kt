package com.jinyuyuliang.budget
import org.junit.Assert.*
import org.junit.Test
class BudgetTest {
 private val day="2026-09-21"
 private fun state()=LedgerState(budgets=listOf(MonthBudget("2026-09",30000)))
 @Test fun spendingRecalculatesDaily() { val s=state(); assertEquals(3000L,BudgetCalculator.calculate(s,day).daily); val next=Ledger.apply(s,Mutation.SaveEntry(Entry("a",date=day,amount=2000)),day); val b=BudgetCalculator.calculate(next,day); assertEquals(28000L,b.available); assertEquals(2800L,b.daily); assertEquals(3000L,next.days.single().allowance); assertEquals(1000L,b.todayRemaining) }
 @Test fun reservationPaymentDoesNotDoubleDeduct() { val s=LedgerState(budgets=listOf(MonthBudget("2026-09",200000)),reserves=listOf(Reserve("rent","2026-09","房租",50000))); assertEquals(150000L,BudgetCalculator.calculate(s,day).available); val n=Ledger.apply(s,Mutation.SaveEntry(Entry("a",date=day,amount=50000,reserveId="rent")),day); assertEquals(150000L,BudgetCalculator.calculate(n,day).available); assertEquals(0L,BudgetCalculator.calculate(n,day).todayUsed) }
 @Test fun monthLengthsAndLeapYear() { for((date,n) in listOf("2024-02-01" to 29,"2025-02-01" to 28,"2026-04-01" to 30,"2026-01-01" to 31,"2026-09-30" to 1)) assertEquals(n,BudgetCalculator.calculate(LedgerState(),date).daysLeft) }
 @Test fun internalTransferDoesNotSpend() { val s=Ledger.apply(state(),Mutation.SaveEntry(Entry("a",date=day,amount=2000,kind=Kind.TRANSFER)),day); assertEquals(30000L,BudgetCalculator.calculate(s,day).available) }
 @Test fun onlyChosenIncomeAddsBudget() { val e=Entry("a",date=day,amount=1000,kind=Kind.INCOME); assertEquals(30000L,BudgetCalculator.calculate(state().copy(entries=listOf(e)),day).available); assertEquals(31000L,BudgetCalculator.calculate(state().copy(entries=listOf(e.copy(addToBudget=true))),day).available) }
 @Test fun moneyIsExactAndRejectsInvalid() { assertEquals(101L,Money.parse("1.01")); for(v in listOf("0","-1","1.001","abc","9999999999999999999999")) assertThrows(IllegalArgumentException::class.java){Money.parse(v)} }
 @Test fun overBudgetIsNegative() { assertEquals(-100L,BudgetCalculator.calculate(state().copy(entries=listOf(Entry("a",date=day,amount=31000))),day).daily) }
 @Test fun reserveRefundRestoresReserve() { val s=LedgerState(budgets=listOf(MonthBudget("2026-09",200000)),reserves=listOf(Reserve("r","2026-09","房租",50000)),entries=listOf(Entry("e",date=day,amount=50000,reserveId="r"))); val n=Ledger.apply(s,Mutation.SaveEntry(Entry("f",date=day,amount=10000,kind=Kind.REFUND,originalId="e")),day); assertEquals(150000L,BudgetCalculator.calculate(n,day).available) }
}
