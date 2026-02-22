package com.example.ledger.domain.service

import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

@Service
class GainLossService {
    fun calculate(proceedsKrw: BigDecimal, costBasisKrw: BigDecimal): BigDecimal {
        return proceedsKrw.subtract(costBasisKrw, MathContext.DECIMAL128).setScale(8, RoundingMode.HALF_UP)
    }
}
