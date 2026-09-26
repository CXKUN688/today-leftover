package com.jinyuyuliang.budget
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
class RegressionTest {
 @Test fun sameCandidateTwiceDoesNotDuplicateQueue(){val c=Candidate("x",Entry("x","2026-09-21",100));val s=Ledger.apply(LedgerState(),Mutation.AddCandidates(listOf(c,c)),"2026-09-21");assertEquals(1,s.candidates.size)}
 @Test fun customRuleAppliesToNewCandidate(){val c=Candidate("x",Entry("x","2026-09-21",100,merchant="测试超市"));val s=Ledger.apply(LedgerState(rules=listOf(Rule("超市","购物"))),Mutation.AddCandidates(listOf(c)),"2026-09-21");assertEquals("购物",s.candidates.single().entry!!.category)}
 @Test fun xlsxInlineStringsCanBeImported(){val cells=listOf(listOf("交易时间","交易类型","交易对方","商品","收/支","金额(元)","支付方式","当前状态","交易单号"),listOf("2026-09-21","商户消费","食堂","午餐","支出","20.00","零钱","支付成功","123"));val xml="<worksheet><sheetData>"+cells.mapIndexed{ri,row->"<row>"+row.mapIndexed{ci,v->"<c r='${('A'+ci)}${ri+1}' t='inlineStr'><is><t>$v</t></is></c>"}.joinToString("")+"</row>"}.joinToString("")+"</sheetData></worksheet>";val out=ByteArrayOutputStream();ZipOutputStream(out).use{it.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"));it.write(xml.toByteArray());it.closeEntry()};val report=BillParser.parse(out.toByteArray(),"test.xlsx");assertNull(report.error);assertEquals(2000L,report.candidates.single().entry!!.amount)}
 @Test fun oldOpeningAdjustmentBlocksDoubleCounting(){val s=LedgerState(budgets=listOf(MonthBudget("2026-09",30000,1000,"2026-09-20")));assertThrows(IllegalArgumentException::class.java){Ledger.apply(s,Mutation.Accept(listOf(Entry("a","2026-09-19",1000)),emptyList()),"2026-09-21")}}
 @Test fun disabledAlertsDoNotCreateEvents(){val s=Ledger.apply(LedgerState(budgets=listOf(MonthBudget("2026-09",30000)),alertEnabled=false),Mutation.SaveEntry(Entry("x","2026-09-21",2500)),"2026-09-21");assertTrue(s.alerts.isEmpty())}
 @Test fun refundThenRespendingDoesNotRepeatReminder(){var s=Ledger.apply(LedgerState(budgets=listOf(MonthBudget("2026-09",30000))),Mutation.SaveEntry(Entry("x","2026-09-21",2000)),"2026-09-21");s=Ledger.apply(s,Mutation.SaveEntry(Entry("r","2026-09-21",1000,kind=Kind.REFUND,originalId="x")),"2026-09-21");s=Ledger.apply(s,Mutation.SaveEntry(Entry("y","2026-09-21",1000)),"2026-09-21");assertEquals(1,s.alerts.size)}
}
