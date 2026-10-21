package com.renaser.os.mentoring.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Una evidencia de una obligación: solo cuál es, de qué tipo (FOTO, VIDEO, AUDIO, TEXTO, CAPTURA) y cuándo se subió.
 * El archivo se abre aparte, con la autorización de {@code evidence}.
 */
public record EvidenciaDeObligacion(UUID id, String tipo, Instant subidaEn) {
}
