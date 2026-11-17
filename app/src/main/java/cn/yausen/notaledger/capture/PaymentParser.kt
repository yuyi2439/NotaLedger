package cn.yausen.notaledger.capture

/** Only explicit completed expenses are candidates. Ambiguous messages stay manual. */
data class PaymentDraft(val amount: Double, val merchant: String, val timestamp: Long)

object PaymentParser {
    val paymentApps = mapOf("com.tencent.mm" to "微信", "com.eg.android.AlipayGphone" to "支付宝")
    private val excluded = Regex("退款|退回|收款(?!方\\s*[：:])|到账|收入|余额|转账|红包|待支付|待付款|请支付|请付款|支付请求|付款请求|支付失败|付款失败|验证码|优惠|立减|还款")
    private val amount = Regex("(?:支付成功|付款成功|成功支付|成功付款|支付金额|付款金额|消费金额|支付|付款|消费)(?:人民币)?[：:\\s]*[¥￥]?\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)(?:\\s*元|(?=\\s|$|[，。；;]|,(?![0-9])))")
    private val merchant = Regex("(?:商户|商家|收款方|商品)\\s*[：:]\\s*([^\\n，,；;]+)")
    fun parse(packageName: String, text: String, timestamp: Long): PaymentDraft? {
        if (packageName !in paymentApps || excluded.containsMatchIn(text)) return null
        val matches = amount.findAll(text).toList()
        if (matches.size != 1) return null
        val value = matches.single().groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (!value.isFinite() || value <= 0 || value > 999999999) return null
        val shop = merchant.find(text)?.groupValues?.get(1)?.trim()?.take(80).orEmpty()
        return PaymentDraft(value, shop, timestamp)
    }
}
