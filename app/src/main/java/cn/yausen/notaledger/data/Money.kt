package cn.yausen.notaledger.data

import java.math.BigDecimal
import java.math.RoundingMode

/** Storage uses fen; yuan doubles are only used at the input/display boundary. */
object Money {
    fun toCents(yuan: Double): Long {
        require(yuan.isFinite()) { "金额必须是有限数值" }
        return BigDecimal.valueOf(yuan).movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP).longValueExact()
    }
}
