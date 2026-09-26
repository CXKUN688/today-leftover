package com.jinyuyuliang.budget

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.jinyuyuliang.budget.data.BudgetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Compose UI 设备测试。
 *
 * **重要限制**：Espresso（含最新稳定版 3.6.1）的
 * `InputManagerEventInjectionStrategy` 会调用 `android.hardware.input.InputManager.getInstance()`，
 * 该 API 在 Android 17（API 37）上已被移除，导致所有使用 ComposeTestRule 的用例在
 * `Espresso.onIdle()` 初始化阶段即抛 `NoSuchMethodException`，
 * 根本无法执行到任何断言。此为上游 Espresso 兼容性问题，非本应用缺陷。
 *
 * 因此本类用 [assumeTrue] 在 API >= 37 上**跳过**（记为 skipped 而非 failed），
 * 以免留下永久失败的测试污染结果；在 Espresso 支持 API 37 的版本上会自动恢复执行。
 *
 * 不依赖 UI 的应用逻辑验证见 [LedgerDeviceTest]，该套件在 API 37 上可正常通过。
 */
@RunWith(AndroidJUnit4::class)
class BudgetDeviceTest {

    /**
     * 跳过规则必须先于 Compose 规则求值：
     * `@get:Rule` 的 ComposeTestRule 在初始化时就会触发 Espresso，
     * 在 `@Before` 里 assumeTrue 已经太晚。
     * order = 0 使其在外层执行，从而在 API 37+ 上抢先跳过。
     */
    @get:Rule(order = 0)
    val skipOnUnsupportedApi: org.junit.rules.TestRule =
        org.junit.rules.TestRule { base, _ ->
            object : org.junit.runners.model.Statement() {
                override fun evaluate() {
                    assumeTrue(
                        "Espresso 尚不支持 API ${Build.VERSION.SDK_INT}（InputManager.getInstance 已移除）",
                        Build.VERSION.SDK_INT < 37
                    )
                    base.evaluate()
                }
            }
        }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val today = LocalDate.now().toString()
    private val date = LocalDate.now()
    private val days = date.lengthOfMonth() - date.dayOfMonth + 1
    private lateinit var store: BudgetStore

    @Before
    fun seed() {
        store = BudgetStore.get(compose.activity)
        runBlocking {
            store.restore(
                Backup.encode(LedgerState(budgets = listOf(MonthBudget(today.take(7), days * 3000L))))
            )
        }
        compose.waitUntil(15000) { store.state.value != null }
    }

    /** 记一笔 20 元后，首页同时显示今日剩余与动态日均，并产生一条预警。 */
    @Test
    fun recordExpenseUpdatesBothAmounts() {
        compose.onNodeWithText("记一笔").performClick()
        compose.onNodeWithText("金额（元）").performTextReplacement("20")
        compose.onNodeWithText("保存记录").performScrollTo().performClick()
        compose.waitUntil(15000) { store.state.value?.entries?.size == 1 }

        compose.onNodeWithText("¥ 10.00").assertExists()
        val s = store.state.value!!
        assertEquals(1, s.alerts.size)
        assertEquals((days * 3000L - 2000L) / days, BudgetCalculator.calculate(s, today).daily)
    }

    /** 并发写入同一来源只落库一次。 */
    @Test
    fun concurrentDuplicatePersistsOnce() {
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
        val s = store.state.value!!
        assertEquals(1, s.entries.size)
        assertEquals(1, s.alerts.size)
    }

    /** 损坏备份不能替换账本。 */
    @Test
    fun badBackupCannotReplaceLedger() {
        val before = store.state.value
        runBlocking {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                runBlocking { store.restore("{broken") }
            }
        }
        assertEquals(before, store.state.value)
    }

    /** XLSX 在 Android 运行时解析可用。 */
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

        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use {
            it.putNextEntry(java.util.zip.ZipEntry("xl/worksheets/sheet1.xml"))
            it.write(xml.toByteArray())
            it.closeEntry()
        }
        val parsed = BillParser.parse(out.toByteArray(), "a.xlsx")
        org.junit.Assert.assertNull(parsed.error)
        assertEquals(2000L, parsed.candidates.single().entry!!.amount)
    }
}
