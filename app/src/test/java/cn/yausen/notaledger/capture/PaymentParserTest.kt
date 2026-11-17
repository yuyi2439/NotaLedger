package cn.yausen.notaledger.capture

import org.junit.Assert.*
import org.junit.Test

class PaymentParserTest {
    private val alipay = "com.eg.android.AlipayGphone"
    @Test fun `completed payment provides amount merchant and original time`() {
        val draft = PaymentParser.parse(alipay, "支付成功\n付款25.60元\n商户：XX便利店", 123L)!!
        assertEquals(25.60, draft.amount, 0.001)
        assertEquals("XX便利店", draft.merchant)
        assertEquals(123L, draft.timestamp)
    }
    @Test fun `wechat currency prefix and grouped amount are supported`() {
        assertEquals(1234.5, PaymentParser.parse("com.tencent.mm", "微信支付\n支付成功 ￥1,234.50\n商家: 超市", 1)!!.amount, 0.001)
    }
    @Test fun `payee label is a merchant rather than received income`() {
        val draft = PaymentParser.parse(alipay, "付款成功\n支付金额：￥25.00元\n收款方：便利店", 1)!!
        assertEquals("便利店", draft.merchant)
        assertEquals(25.0, draft.amount, 0.001)
    }
    @Test fun `unrelated apps and ordinary messages cannot produce a draft`() {
        assertNull(PaymentParser.parse("other.app", "支付25元", 1))
        assertNull(PaymentParser.parse(alipay, "今日推荐，价格25元", 1))
        assertNull(PaymentParser.parse(alipay, "支付成功", 1))
    }
    @Test fun `refund income balance transfer and unsuccessful payments stay manual`() {
        listOf("退款", "收款", "到账", "余额", "转账", "支付失败", "待支付", "红包", "优惠").forEach {
            assertNull(it, PaymentParser.parse(alipay, "$it 支付25元", 1))
        }
    }
    @Test fun `ambiguous and invalid amounts stay manual`() {
        listOf("支付10元，付款20元", "支付0元", "支付-2元", "支付25.999元", "支付1,23元", "支付9999999999元").forEach {
            assertNull(it, PaymentParser.parse(alipay, it, 1))
        }
    }
}
