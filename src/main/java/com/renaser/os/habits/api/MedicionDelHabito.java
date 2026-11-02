package com.renaser.os.habits.api;

import java.math.BigDecimal;

/**
 * Lo que `habits` sabe de la medicion de un habito medible (hoy KILOMETROS DIARIOS) para quien habla de el desde afuera: el acompanante de {@code rag}
 * (spec 038, tarea T021, sobre la spec 037). Es la misma {@code medicion} que ve la app en {@code GET /habit-tracks/today}, mas una pista que la app
 * no necesita y SER si: si la persona ya mide sus kilometros con el telefono.
 *
 * @param unidad            en que se mide, como la nombra `habits` ({@code KILOMETROS})
 * @param meta              la meta del dia (la de Administracion o la del programa); {@code null} si no se sabe
 * @param valorDelDia       lo ya registrado hoy; {@code null} mientras no se registro nada
 * @param origenDelDia      {@code MANUAL} (captura) o {@code HEALTH_CONNECT}; {@code null} mientras no se registro
 * @param fuenteDelDia      la etiqueta legible de la fuente (solo la lectura automatica la tiene), o {@code null}
 * @param conPasosConectados si la persona ya tiene algun dia de este habito medido por el telefono (Health Connect, hasta hoy). El servidor no puede saber si
 *                          los pasos estan conectados en ESTE momento (el permiso vive en el telefono): esta es la mejor senal que tiene, y es segura en los dos
 *                          sentidos, porque la fila de Training atiende tanto el «Registrar» automatico como la captura
 */
public record MedicionDelHabito(String unidad, BigDecimal meta, BigDecimal valorDelDia, String origenDelDia, String fuenteDelDia,
                                boolean conPasosConectados) {
}
