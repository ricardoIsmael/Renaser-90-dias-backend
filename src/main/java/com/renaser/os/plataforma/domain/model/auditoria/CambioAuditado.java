package com.renaser.os.plataforma.domain.model.auditoria;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Una fila de la auditoría append-only (spec 002, FR-007; contrato §4): quién, cuándo, qué, de qué valor a cuál,
 * motivo y dispositivo. Nadie la edita ni la borra: un trigger de la base rechaza el UPDATE y el DELETE, y ninguna API
 * lo intenta.
 *
 * @param actor       {@code null} solo en {@link TipoDeCambio#AUTOMATICO}
 * @param clave       la funcionalidad; {@code null} en un desbloqueo, que no toca ninguna
 * @param dispositivo el agente del pedido (User-Agent), recortado; puede faltar
 */
public record CambioAuditado(UUID id, TipoDeCambio tipo, UserId actor, String clave, Map<String, Object> antes,
                             Map<String, Object> despues, String motivo, String dispositivo, Instant creadoEn) {

    static final int LARGO_MAXIMO_DEL_DISPOSITIVO = 200;

    public CambioAuditado {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(tipo, "tipo es obligatorio");
        Objects.requireNonNull(creadoEn, "creadoEn es obligatorio");
        if (actor == null && tipo != TipoDeCambio.AUTOMATICO) {
            throw new IllegalArgumentException("Solo un cambio automatico va sin actor");
        }
        antes = copia(antes);
        despues = copia(despues);
        dispositivo = recortar(dispositivo);
    }

    public static CambioAuditado manual(UUID id, UserId actor, String clave, Map<String, Object> antes,
                                        Map<String, Object> despues, String motivo, String dispositivo,
                                        Instant en) {
        return new CambioAuditado(id, TipoDeCambio.MANUAL, Objects.requireNonNull(actor), clave, antes, despues,
                motivo, dispositivo, en);
    }

    public static CambioAuditado automatico(UUID id, String clave, Map<String, Object> antes,
                                            Map<String, Object> despues, String causa, Instant en) {
        return new CambioAuditado(id, TipoDeCambio.AUTOMATICO, null, clave, antes, despues, causa, null, en);
    }

    /** Un intento de desbloqueo o de reautenticación; {@code resultado} es «OK» o «CONTRASENA_INCORRECTA». */
    public static CambioAuditado acceso(UUID id, TipoDeCambio tipo, UserId actor, String resultado,
                                        String dispositivo, Instant en) {
        if (tipo != TipoDeCambio.DESBLOQUEO && tipo != TipoDeCambio.REAUTENTICACION) {
            throw new IllegalArgumentException("Un acceso es un desbloqueo o una reautenticacion");
        }
        return new CambioAuditado(id, tipo, Objects.requireNonNull(actor), null, null,
                Map.of("resultado", resultado), null, dispositivo, en);
    }

    private static Map<String, Object> copia(Map<String, Object> valores) {
        return valores == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(valores));
    }

    private static String recortar(String dispositivo) {
        if (dispositivo == null || dispositivo.isBlank()) {
            return null;
        }
        String limpio = dispositivo.strip();
        return limpio.length() <= LARGO_MAXIMO_DEL_DISPOSITIVO ? limpio
                : limpio.substring(0, LARGO_MAXIMO_DEL_DISPOSITIVO);
    }
}
