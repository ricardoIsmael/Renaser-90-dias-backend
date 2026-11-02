package com.renaser.os.habits.api;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lo medido en una obligación de hábito (hoy, los km de KILÓMETROS DIARIOS) y de dónde salió, para quien acompaña a la
 * persona (spec 037): su mentor, su líder y Administración lo ven en la ficha. Es un resumen de lectura: nunca entra a un
 * cálculo.
 *
 * @param origen       {@code MANUAL} (la persona escribió el número junto a su captura) o {@code HEALTH_CONNECT} (lo leyó
 *                     el teléfono)
 * @param pasos        solo en {@code HEALTH_CONNECT}, y opcional
 * @param fuente       etiqueta legible de la fuente («Galaxy Watch», «Tu teléfono»); {@code null} en {@code MANUAL}
 * @param dispositivo  {@code TELEFONO}, {@code RELOJ} u {@code OTRO}; {@code null} en {@code MANUAL}
 * @param leidaEn      cuándo leyó el teléfono; {@code null} en {@code MANUAL}
 * @param porRevisar   el servidor aceptó una lectura dudosa y la dejó marcada
 */
public record MedicionDeLaObligacion(BigDecimal valor, String origen, Integer pasos, String fuente, String dispositivo,
                                      Instant leidaEn, boolean porRevisar) {
}
