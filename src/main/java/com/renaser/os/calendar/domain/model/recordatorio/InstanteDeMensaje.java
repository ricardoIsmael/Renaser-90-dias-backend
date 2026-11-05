package com.renaser.os.calendar.domain.model.recordatorio;

import java.time.Instant;

/**
 * Cuando sale uno de los avisos de MENSAJE (chat y WhatsApp) de una ocurrencia: {@code minutosAntes} del inicio (spec 041).
 * Hermano de {@link InstanteRecordatorio}, que lleva la regla del aviso del telefono.
 */
public record InstanteDeMensaje(Instant enviarEn, Instant inicioOcurrencia, int minutosAntes) {
}
