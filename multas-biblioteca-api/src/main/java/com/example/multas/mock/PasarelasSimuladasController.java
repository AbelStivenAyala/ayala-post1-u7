package com.example.multas.mock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Simula PagosUDES y Wompi SOLO para pruebas locales (app.pagos.mock.enabled=true).
 * Regla: se rechaza todo pago de mas de $10.000 (multas de 21 dias o mas).
 */
@RestController
@RequestMapping("/mock")
@ConditionalOnProperty(prefix = "app.pagos.mock", name = "enabled", havingValue = "true")
public class PasarelasSimuladasController {

    private static final BigDecimal LIMITE_PESOS = new BigDecimal("10000");
    private static final long LIMITE_CENTAVOS = 1_000_000L;

    @PostMapping("/pagosudes/transacciones")
    public Map<String, String> pagosUdes(@RequestBody Map<String, Object> body) {
        BigDecimal monto = new BigDecimal(body.get("monto").toString());
        boolean aprobada = monto.compareTo(LIMITE_PESOS) <= 0;
        return Map.of(
            "idTransaccion", "PU-" + UUID.randomUUID().toString().substring(0, 8),
            "estadoTransaccion", aprobada ? "APROBADA" : "RECHAZADA");
    }

    @PostMapping("/wompi/transactions")
    public Map<String, String> wompi(@RequestBody Map<String, Object> body) {
        long centavos = ((Number) body.get("amountInCents")).longValue();
        boolean aprobada = centavos <= LIMITE_CENTAVOS;
        return Map.of(
            "reference", body.get("reference").toString(),
            "status", aprobada ? "APPROVED" : "DECLINED");
    }
}
