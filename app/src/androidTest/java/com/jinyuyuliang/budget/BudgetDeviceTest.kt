package com.jinyuyuliang.budget
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jinyuyuliang.budget.data.BudgetStore
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate
import java.io.ByteArrayOutputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
@RunWith(AndroidJUnit4::class)
class BudgetDeviceTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private val today=LocalDate.now().toString()
 private val date=LocalDate.now()
 private val days=date.lengthOfMonth()-date.dayOfMonth+1
 private lateinit var store:BudgetStore
 @Before fun seed(){store=BudgetStore.get(compose.activity);runBlocking{store.restore(Backup.encode(LedgerState(budgets=listOf(MonthBudget(today.take(7),days*3000L)))))};compose.waitUntil(15000){store.state.value!=null}}
 @Test fun recordExpenseUpdatesBothAmounts(){compose.onNodeWithText("记一笔").performClick();compose.onNodeWithText("金额（元）").performTextReplacement("20");compose.onNodeWithText("保存记录").performScrollTo().performClick();compose.waitUntil(15000){store.state.value?.entries?.size==1};compose.onNodeWithText("¥ 10.00").assertExists();val s=store.state.value!!;assertEquals(1,s.alerts.size);assertEquals((days*3000L-2000L)/days,BudgetCalculator.calculate(s,today).daily)}
 @Test fun concurrentDuplicatePersistsOnce(){runBlocking{coroutineScope{repeat(6){i->launch(Dispatchers.IO){store.apply(Mutation.SaveEntry(Entry("e$i",today,2000,sourceKey="wx:one")))}}};store.refresh()};val s=store.state.value!!;assertEquals(1,s.entries.size);assertEquals(1,s.alerts.size)}
 @Test fun badBackupCannotReplaceLedger(){val before=store.state.value;runBlocking{assertThrows(IllegalArgumentException::class.java){runBlocking{store.restore("{broken")}}};assertEquals(before,store.state.value)}
 @Test fun xlsxParsesOnAndroid(){val rows=listOf(listOf("交易时间","交易类型","交易对方","商品","收/支","金额(元)","支付方式","当前状态","交易单号"),listOf(today,"商户消费","食堂","午饭","支出","20.00","零钱","支付成功","123"));val xml="<worksheet><sheetData>"+rows.mapIndexed{ri,row->"<row>"+row.mapIndexed{ci,v->"<c r='${'A'+ci}${ri+1}' t='inlineStr'><is><t>$v</t></is></c>"}.joinToString("")+"</row>"}.joinToString("")+"</sheetData></worksheet>";val out=ByteArrayOutputStream();ZipOutputStream(out).use{it.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"));it.write(xml.toByteArray());it.closeEntry()};val parsed=BillParser.parse(out.toByteArray(),"a.xlsx");assertNull(parsed.error);assertEquals(2000L,parsed.candidates.single().entry!!.amount)}
}
