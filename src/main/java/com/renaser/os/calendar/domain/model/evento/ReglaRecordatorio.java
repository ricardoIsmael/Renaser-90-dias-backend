package com.renaser.os.calendar.domain.model.evento;

import java.time.LocalTime;
import java.util.Objects;

/**
 * Una regla de recordatorio, forma verificada en reminders.ts (repo viejo): 3 clases,
 * intencion en vez de hora fija — "10 min antes" sigue siendo cierto si el evento se
 * mueve; "18:50" escrito a mano deja de serlo.
 *
 * <p>{@code orden} es la posicion 1..N tal como las guarda el admin (PK compuesta
 * {@code (evento_id, orden)} en {@code reglas_recordatorio_evento}) — no tiene significado
 * de negocio propio mas alla de distinguir filas duplicadas del mismo evento.
 *
 * <p>CHECK {@code valor_segun_tipo} del baseline: MINUTOS_ANTES/DIAS_ANTES llevan
 * {@code valorNumero}, HORA_DEL_DIA lleva {@code valorHora} — nunca ambos, nunca ninguno.
 */
public record ReglaRecordatorio(int orden, TipoReglaRecordatorio tipo, Integer valorNumero, LocalTime valorHora) {

    /** Ceiling de {@code minutesBefore}/{@code daysBefore} — MAX_DIAS_ANTES del repo viejo (reminders.ts). */
    public static final int MAX_DIAS_ANTES = 30;

    public ReglaRecordatorio {
        Objects.requireNonNull(tipo, "tipo es obligatorio");
        boolean llevaNumero = tipo == TipoReglaRecordatorio.MINUTOS_ANTES || tipo == TipoReglaRecordatorio.DIAS_ANTES;
        if (llevaNumero) {
            if (valorNumero == null || valorNumero <= 0) {
                throw new IllegalArgumentException(tipo + " requiere valorNumero positivo");
            }
            int techo = tipo == TipoReglaRecordatorio.DIAS_ANTES ? MAX_DIAS_ANTES : MAX_DIAS_ANTES * 24 * 60;
            if (valorNumero > techo) {
                throw new IllegalArgumentException(tipo + " no puede superar " + techo);
            }
            if (valorHora != null) {
                throw new IllegalArgumentException(tipo + " no admite valorHora");
            }
        } else {
            if (valorHora == null) {
                throw new IllegalArgumentException("HORA_DEL_DIA requiere valorHora");
            }
            if (valorNumero != null) {
                throw new IllegalArgumentException("HORA_DEL_DIA no admite valorNumero");
            }
        }
    }

    public static ReglaRecordatorio minutosAntes(int orden, int minutos) {
        return new ReglaRecordatorio(orden, TipoReglaRecordatorio.MINUTOS_ANTES, minutos, null);
    }

    public static ReglaRecordatorio diasAntes(int orden, int dias) {
        return new ReglaRecordatorio(orden, TipoReglaRecordatorio.DIAS_ANTES, dias, null);
    }

    public static ReglaRecordatorio horaDelDia(int orden, LocalTime hora) {
        return new ReglaRecordatorio(orden, TipoReglaRecordatorio.HORA_DEL_DIA, null, hora);
    }

    /** Misma clave de igualdad que usa `schema.ts` (repo viejo) para rechazar reglas duplicadas: tipo+valor, sin el orden. */
    public String claveDuplicado() {
        return tipo + ":" + (valorNumero != null ? valorNumero : valorHora);
    }
}
