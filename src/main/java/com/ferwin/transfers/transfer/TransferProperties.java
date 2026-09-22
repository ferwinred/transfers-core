package com.ferwin.transfers.transfer;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Límites transaccionales configurables por ambiente (sin recompilar). */
@ConfigurationProperties(prefix = "app.transfers")
public record TransferProperties(BigDecimal maxAmount, BigDecimal dailyLimit) {
}
