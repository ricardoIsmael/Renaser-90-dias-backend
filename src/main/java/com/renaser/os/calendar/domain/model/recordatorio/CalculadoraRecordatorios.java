package com.renaser.os.calendar.domain.model.recordatorio;

import com.renaser.os.calendar.domain.model.evento.ReglaRecordatorio;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Puerto directo de {@code src/features/calendar/reminders.ts} (repo viejo) —
 * {@code reminderInstantsFor}/{@code diasDeVentana}. DOMINIO PURO.
 *
 * <p>{@code DIAS_ANTES} se porta LITERAL: resta {@code valor * 24h} exactas en el eje de
 * instantes, NO "el mismo dia calendario, N dias antes" — el comentario del JS original
 * dice una cosa ("a la misma hora de pared") pero el codigo hace otra (resta de
 * milisegundos fija). Se preserva el comportamiento REAL, no el comentario, para no
 * introducir un drift de comportamiento no pedido (CLAUDE.MD §5.3.4).
 */
public final class CalculadoraRecordatorios {

    private CalculadoraRecordatorios() {
    }

    public static List<InstanteRecordatorio> instantesPara(Instant inicioOcurrencia, List<ReglaRecordatorio> reglas,
                                                             ZoneId timezone, Instant ahora) {
        List<InstanteRecordatorio> resultado = new ArrayList<>();
        for (ReglaRecordatorio regla : reglas) {
            Instant enviarEn = switch (regla.tipo()) {
                case MINUTOS_ANTES -> inicioOcurrencia.minusSeconds(regla.valorNumero() * 60L);
                case DIAS_ANTES -> inicioOcurrencia.minusSeconds(regla.valorNumero() * 86_400L);
                case HORA_DEL_DIA -> aHoraDelDia(inicioOcurrencia, regla.valorHora(), timezone);
            };
            if (enviarEn.isAfter(ahora)) {
                resultado.add(new InstanteRecordatorio(enviarEn, inicioOcurrencia, regla));
            }
        }
        resultado.sort((a, b) -> a.enviarEn().compareTo(b.enviarEn()));
        return resultado;
    }

    /** El mismo dia calendario (en `timezone`) que la ocurrencia, a la hora de pared indicada. */
    private static Instant aHoraDelDia(Instant inicioOcurrencia, java.time.LocalTime hora, ZoneId timezone) {
        ZonedDateTime local = inicioOcurrencia.atZone(timezone);
        return local.toLocalDate().atTime(hora).atZone(timezone).toInstant();
    }

    /**
     * Cuantos dias por delante hay que mirar para que estas reglas lleguen a tiempo, nunca
     * menos de {@code minimo}. HORA_DEL_DIA no suma nada: siempre cae el mismo dia que la
     * ocurrencia.
     */
    public static int diasDeVentana(List<ReglaRecordatorio> reglas, int minimo) {
        int dias = minimo;
        for (ReglaRecordatorio regla : reglas) {
            if (regla.tipo() == com.renaser.os.calendar.domain.model.evento.TipoReglaRecordatorio.DIAS_ANTES) {
                dias = Math.max(dias, regla.valorNumero() + 1);
            } else if (regla.tipo() == com.renaser.os.calendar.domain.model.evento.TipoReglaRecordatorio.MINUTOS_ANTES) {
                dias = Math.max(dias, (int) Math.ceil(regla.valorNumero() / 1440.0) + 1);
            }
        }
        return dias;
    }
}
