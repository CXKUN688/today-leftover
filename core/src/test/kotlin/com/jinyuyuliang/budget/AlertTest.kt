package com.jinyuyuliang.budget
import org.junit.Assert.*
import org.junit.Test
class AlertTest {
 @Test fun exactTwoThirdsThreshold() { assertEquals(0,Alerts.level(3000,1999,2,3)); assertEquals(1,Alerts.level(3000,2000,2,3)); assertEquals(2,Alerts.level(3000,3000,2,3)); assertEquals(2,Alerts.level(3000,3500,2,3)) }
 @Test fun hugeAmountsDoNotOverflow() { assertEquals(1,Alerts.level(900000000000L,600000000000L,2,3)) }
 @Test fun reminderOnceEvenAfterRefundAndRespending() { val d="2026-09-21"; var s=LedgerState(budgets=listOf(MonthBudget("2026-09",30000))); s=Ledger.apply(s,Mutation.SaveEntry(Entry("a",date=d,amount=2000)),d); assertEquals(1,s.alerts.size); s=Ledger.apply(s,Mutation.SaveEntry(Entry("b",date=d,amount=100)),d); assertEquals(1,s.alerts.size); s=Ledger.apply(s,Mutation.SaveEntry(Entry("c",date=d,amount=1000)),d); assertEquals(2,s.alerts.size) }
 @Test fun oneLargePurchaseOnlySendsHighestLevel() { val d="2026-09-21"; val s=Ledger.apply(LedgerState(budgets=listOf(MonthBudget("2026-09",30000))),Mutation.SaveEntry(Entry("a",date=d,amount=5000)),d); assertEquals(1,s.alerts.size); assertEquals(2,s.alerts.single().level) }
 @Test fun afternoonFirstReadReconstructsMorningAllowance() { val d="2026-09-21"; val s=Ledger.ensureDay(LedgerState(budgets=listOf(MonthBudget("2026-09",30000)),entries=listOf(Entry("a",date=d,amount=2000))),d); assertEquals(3000L,s.days.single().allowance) }
 @Test fun noNotificationForOldImport() { val d="2026-09-21"; val s=Ledger.apply(LedgerState(budgets=listOf(MonthBudget("2026-09",30000))),Mutation.SaveEntry(Entry("a",date="2026-09-20",amount=10000)),d); assertEquals(0,s.alerts.size) }
}
