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

    /**
     * Los avisos de MENSAJE de una ocurrencia (spec 041, FR-014): uno por antelacion, {@code minutos} antes del inicio, y solo los que
     * TODAVIA llegan a tiempo. Un evento creado o editado 20 minutos antes de empezar no genera «1 dia antes» ni «1 hora antes»: su momento
     * ya paso (como en RenaserOs). Son independientes de las reglas del aviso del telefono. Una antelacion repetida o no positiva se ignora.
     */
    public static List<InstanteDeMensaje> instantesDeMensaje(Instant inicioOcurrencia, List<Integer> antelacionesMin,
                                                              Instant ahora) {
        return instantesDeMensaje(inicioOcurrencia, antelacionesMin, ahora, ahora);
    }

    /**
     * Cuanto puede haber pasado la hora de un aviso de mensaje y todavia generarse: el mismo tope de 15 minutos de atraso con que sale
     * (spec 041, FR-016; {@code notifications.VeredictoDelMensaje.ATRASO_MAXIMO}). Hace falta porque el barrido corre cada 5 minutos EN
     * PUNTO y las horas de los eventos van de 5 en 5: el «10 min antes» cae justo en un turno, y con «solo lo que es posterior a ahora» se
     * perdia siempre que el evento se creaba entre 10 y 15 minutos antes (issue #85).
     */
    public static final java.time.Duration GRACIA_DE_GENERACION_DE_MENSAJE = java.time.Duration.ofMinutes(15);

    /**
     * Como {@link #instantesDeMensaje(Instant, List, Instant)}, pero con la regla completa de FR-014: no se genera un aviso cuyo momento ya
     * habia pasado cuando el evento se creo o se edito por ultima vez ({@code ultimoCambio}). Uno cuyo momento llego DESPUES de ese cambio
     * se genera aunque ya haya pasado, si no lleva mas de {@link #GRACIA_DE_GENERACION_DE_MENSAJE} de atraso y el evento no empezo: el
     * despachador lo manda en el turno siguiente.
     */
    public static List<InstanteDeMensaje> instantesDeMensaje(Instant inicioOcurrencia, List<Integer> antelacionesMin,
                                                              Instant ahora, Instant ultimoCambio) {
        List<InstanteDeMensaje> resultado = new ArrayList<>();
        if (!inicioOcurrencia.isAfter(ahora)) {
            return resultado;
        }
        Instant masTemprano = ahora.minus(GRACIA_DE_GENERACION_DE_MENSAJE);
        for (Integer minutos : antelacionesMin.stream().filter(m -> m != null && m > 0).distinct().toList()) {
            Instant enviarEn = inicioOcurrencia.minusSeconds(minutos * 60L);
            boolean todaviaNoLlega = enviarEn.isAfter(ahora);
            boolean llegoDespuesDelCambioYConPocoAtraso = ultimoCambio != null && enviarEn.isAfter(ultimoCambio)
                    && !enviarEn.isBefore(masTemprano);
            if (todaviaNoLlega || llegoDespuesDelCambioYConPocoAtraso) {
                resultado.add(new InstanteDeMensaje(enviarEn, inicioOcurrencia, minutos));
            }
        }
        resultado.sort((a, b) -> a.enviarEn().compareTo(b.enviarEn()));
        return resultado;
    }

    /** Cuantos dias por delante hay que mirar para que el aviso de mensaje mas lejano se genere a tiempo (el mismo margen de un dia que las reglas). */
    public static int diasDeVentanaDeMensaje(List<Integer> antelacionesMin, int minimo) {
        int dias = minimo;
        for (Integer minutos : antelacionesMin) {
            if (minutos != null && minutos > 0) {
                dias = Math.max(dias, (int) Math.ceil(minutos / 1440.0) + 1);
            }
        }
        return dias;
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
