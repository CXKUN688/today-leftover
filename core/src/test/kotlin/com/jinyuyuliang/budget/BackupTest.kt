package com.jinyuyuliang.budget
import org.junit.Assert.*
import org.junit.Test
class BackupTest {
 @Test fun roundTrip(){val s=LedgerState(budgets=listOf(MonthBudget("2026-09",30000)),entries=listOf(Entry("a",date="2026-09-21",amount=2000)));assertEquals(s,Backup.decode(Backup.encode(s)))}
 @Test fun corruptInputRejected(){assertThrows(IllegalArgumentException::class.java){Backup.decode("{bad")}}
 @Test fun unknownVersionRejected(){assertThrows(IllegalArgumentException::class.java){Backup.decode("{\"version\":99,\"state\":{}}")}}
}
