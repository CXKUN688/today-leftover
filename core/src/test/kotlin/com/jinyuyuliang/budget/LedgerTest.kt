package com.jinyuyuliang.budget
import org.junit.Assert.*
import org.junit.Test
class LedgerTest {
 val day="2026-09-21"
 fun initial()=LedgerState(budgets=listOf(MonthBudget("2026-09",30000)))
 @Test fun duplicateSourceDoesNotDeductTwice(){val e=Entry("a",date=day,amount=2000,sourceKey="wx:123:EXPENSE");val s=Ledger.apply(initial(),Mutation.SaveEntry(e),day);val n=Ledger.apply(s,Mutation.SaveEntry(e.copy(id="b")),day);assertEquals(1,n.entries.size);assertEquals(28000L,BudgetCalculator.calculate(n,day).available)}
 @Test fun deletionUpdatesBudget(){val s=Ledger.apply(initial(),Mutation.SaveEntry(Entry("a",date=day,amount=2000)),day);assertEquals(30000L,BudgetCalculator.calculate(Ledger.apply(s,Mutation.DeleteEntry("a"),day),day).available)}
 @Test fun refundOverOriginalRejected(){val s=initial().copy(entries=listOf(Entry("a",date=day,amount=1000)));assertThrows(IllegalArgumentException::class.java){Ledger.apply(s,Mutation.SaveEntry(Entry("r",date=day,amount=1001,kind=Kind.REFUND,originalId="a")),day)}}
 @Test fun cannotDeleteRefundedOriginal(){val s=initial().copy(entries=listOf(Entry("a",date=day,amount=1000),Entry("r",date=day,amount=500,kind=Kind.REFUND,originalId="a")));assertThrows(IllegalArgumentException::class.java){Ledger.apply(s,Mutation.DeleteEntry("a"),day)}}
 @Test fun cannotShrinkRefundedOriginal(){val s=initial().copy(entries=listOf(Entry("a",date=day,amount=1000),Entry("r",date=day,amount=500,kind=Kind.REFUND,originalId="a")));assertThrows(IllegalArgumentException::class.java){Ledger.apply(s,Mutation.SaveEntry(s.entries.first().copy(amount=400)),day)}}
 @Test fun futureEntryRejected(){assertThrows(IllegalArgumentException::class.java){Ledger.apply(initial(),Mutation.SaveEntry(Entry("a",date="2026-09-22",amount=100)),day)}}
 @Test fun yesterdayRefundDoesNotHideTodaySpending(){val s=initial().copy(entries=listOf(Entry("a",date="2026-09-20",amount=3000),Entry("b",date=day,amount=2000),Entry("r",date=day,amount=3000,kind=Kind.REFUND,originalId="a")));assertEquals(2000L,BudgetCalculator.calculate(s,day).todayUsed);assertEquals(28000L,BudgetCalculator.calculate(s,day).available)}
 @Test fun crossMonthRefundIncreasesCurrentBudget(){val s=initial().copy(entries=listOf(Entry("a",date="2026-08-20",amount=3000),Entry("r",date=day,amount=3000,kind=Kind.REFUND,originalId="a")));assertEquals(33000L,BudgetCalculator.calculate(s,day).available)}
 @Test fun budgetChangeRebasesButPreservesReminderLevel(){var s=Ledger.apply(initial(),Mutation.SaveEntry(Entry("a",date=day,amount=2000)),day);s=Ledger.apply(s,Mutation.SaveBudget(MonthBudget("2026-09",60000)),day);assertEquals(6000L,s.days.single().allowance);assertEquals(1,s.days.single().highest)}
}
