package com.jinyuyuliang.budget
import org.junit.Assert.*
import org.junit.Test
class ImportTest {
 private val header="交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号\n"
 @Test fun csvParsesPreambleAndQuotedFields(){val csv="微信支付账单\n"+header+"2026-09-21 12:00:00,商户消费,食堂,\"午饭,加餐\",支出,20.00,零钱,支付成功,123\n";val report=BillParser.parse(csv.toByteArray(),"bill.csv");assertNull(report.error);assertEquals(2000L,report.candidates.single().entry!!.amount);assertEquals("午饭,加餐",report.candidates.single().entry!!.note)}
 @Test fun gb18030Encoding(){val csv=header+"2026-09-21 12:00:00,商户消费,食堂,午饭,支出,20.00,零钱,支付成功,123";assertEquals("食堂",BillParser.parse(csv.toByteArray(charset("GB18030")),"a.csv").candidates.single().entry!!.merchant)}
 @Test fun ambiguousTransferRequiresReview(){val csv=header+"2026-09-21 12:00:00,转账,朋友,转账,支出,20.00,零钱,转账成功,123";assertTrue(BillParser.parse(csv.toByteArray(),"a.csv").candidates.single().needsReview)}
 @Test fun failedPaymentCannotBeReady(){val csv=header+"2026-09-21 12:00:00,商户消费,店铺,物品,支出,20.00,零钱,支付失败,123";assertNull(BillParser.parse(csv.toByteArray(),"a.csv").candidates.single().entry)}
 @Test fun unknownFileDoesNotPretendSuccess(){assertNotNull(BillParser.parse("garbage".toByteArray(),"a.csv").error)}
 @Test fun overLimitRejected(){assertNotNull(BillParser.parse(ByteArray(20*1024*1024+1),"a.csv").error)}
 @Test fun refundAndPaymentHaveDifferentKeys(){val csv=header+"2026-09-21,商户消费,店铺,物品,支出,20.00,零钱,支付成功,123\n2026-09-21,退款,店铺,物品,收入,10.00,零钱,退款成功,123";val a=BillParser.parse(csv.toByteArray(),"a.csv").candidates;assertNotEquals(a[0].entry!!.sourceKey,a[1].entry!!.sourceKey)}
 @Test fun ordinaryChatIsIgnored(){assertNull(PaymentParser.parse("com.tencent.mm","1","张三","借我20元","2026-09-21"))}
 @Test fun unverifiedPaymentRequiresConfirmation(){val c=PaymentParser.parse("com.eg.android.AlipayGphone","1","支付成功","向食堂付款20.00元","2026-09-21");assertNotNull(c);assertTrue(c!!.needsReview)}
 @Test fun otherApplicationsIgnored(){assertNull(PaymentParser.parse("evil.app","1","支付成功","付款20元","2026-09-21"))}
}
