package com.jinyuyuliang.budget

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jinyuyuliang.budget.data.BudgetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 设备测试（不依赖 Compose / Espresso）。
 *
 * 之所以与 [BudgetDeviceTest] 分开：Espresso 3.5.0 在 Android 17 (API 37) 上会因
 * `InputManager.getInstance()` 被移除而在初始化阶段抛 NoSuchMethodException，
 * 导致任何使用 ComposeTestRule 的用例都无法执行。详见 docs/VERIFICATION.md。
 *
 * 本类只使用 Instrumentation 上下文与纯逻辑，因此可在 API 37 上正常执行，
 * 用于验证「应用代码在真实设备上确实可用」。
 */
@RunWith(AndroidJUnit4::class)
class LedgerDeviceTest {

    private val today = LocalDate.now().toString()
    private val date = LocalDate.now()
    private val days = date.lengthOfMonth() - date.dayOfMonth + 1
    private lateinit var store: BudgetStore

    @Before
    fun seed() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        store = BudgetStore.get(ctx)
        runBlocking {
            store.restore(
                Backup.encode(LedgerState(budgets = listOf(MonthBudget(today.take(7), days * 3000L))))
            )
        }
    }

    /** 记账后余额与日均同时更新，且预警恰好一条。 */
    @Test
    fun recordingExpenseUpdatesBalanceAndAllowance() {
        runBlocking { store.apply(Mutation.SaveEntry(Entry("e1", today, 2000))) }
        val s = store.state.value ?: error("state 为空")
        assertEquals(1, s.entries.size)
        assertEquals(1, s.alerts.size)

        val summary: Summary = BudgetCalculator.calculate(s, today)
        assertEquals(days * 3000L - 2000L, summary.available)
        // 动态日均：(预算 - 已花) / 剩余天数
        assertEquals((days * 3000L - 2000L) / days, summary.daily)
        assertEquals(days, summary.daysLeft)
        // 今日参考额度基于消费前基准，今日剩余 = 3000 - 2000
        assertEquals(1000L, summary.todayRemaining)
    }

    /** 同日并发写入同一来源只落库一次，预警也只发一次。 */
    @Test
    fun concurrentDuplicateSourcePersistsOnce() {
        runBlocking {
            coroutineScope {
                repeat(6) { i ->
                    launch(Dispatchers.IO) {
                        store.apply(Mutation.SaveEntry(Entry("e$i", today, 2000, sourceKey = "wx:one")))
                    }
                }
            }
            store.refresh()
        }
        val s = store.state.value ?: error("state 为空")
        assertEquals(1, s.entries.size)
        assertEquals(1, s.alerts.size)
    }

    /** 损坏备份被拒绝，且原账本保持不变。 */
    @Test
    fun badBackupCannotReplaceLedger() {
        val before = store.state.value
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.restore("{broken") }
        }
        assertEquals(before, store.state.value)
    }

    /** XLSX 解析在 Android 运行时可用（含 JAXP DOM 兼容性）。 */
    @Test
    fun xlsxParsesOnAndroid() {
        val rows = listOf(
            listOf("交易时间", "交易类型", "交易对方", "商品", "收/支", "金额(元)", "支付方式", "当前状态", "交易单号"),
            listOf(today, "商户消费", "食堂", "午饭", "支出", "20.00", "零钱", "支付成功", "123")
        )
        val xml = "<worksheet><sheetData>" + rows.mapIndexed { ri, row ->
            "<row>" + row.mapIndexed { ci, v ->
                "<c r='${'A' + ci}${ri + 1}' t='inlineStr'><is><t>$v</t></is></c>"
            }.joinToString("") + "</row>"
        }.joinToString("") + "</sheetData></worksheet>"

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use {
            it.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            it.write(xml.toByteArray())
            it.closeEntry()
        }

        val parsed = BillParser.parse(out.toByteArray(), "a.xlsx")
        assertNull(parsed.error)
        assertEquals(2000L, parsed.candidates.single().entry!!.amount)
    }

    /** 备份导出/恢复后数据一致（真实设备上的持久化往返）。 */
    @Test
    fun backupRoundTripOnDevice() {
        runBlocking { store.apply(Mutation.SaveEntry(Entry("e1", today, 1234, note = "测试"))) }
        val exported = runBlocking { store.export() }
        val restored = Backup.decode(exported)
        val s = store.state.value ?: error("state 为空")
        assertEquals(s.entries.size, restored.entries.size)
        assertEquals(s.budgets, restored.budgets)
    }
}
