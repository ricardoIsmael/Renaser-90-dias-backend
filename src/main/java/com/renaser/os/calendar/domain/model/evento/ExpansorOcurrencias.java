package com.renaser.os.calendar.domain.model.evento;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Puerto directo de {@code src/features/calendar/recurrence.ts} (repo viejo, DOMINIO
 * PURO — sin Spring, sin base de datos). Expande una serie recurrente (o un evento
 * suelto) en ocurrencias concretas dentro de un rango, aplicando excepciones.
 *
 * <p><b>Por que este puerto es mas simple que el original:</b> el JS reimplementaba
 * aritmetica de calendario consciente de zona horaria a mano sobre {@code Intl}, porque
 * JavaScript no tiene un tipo "fecha-hora local con zona" nativo. {@link ZonedDateTime} SI
 * lo tiene: {@code plusDays}/{@code plusMonths}/{@code minusDays} preservan la hora de
 * pared local y resuelven el desplazamiento (incluido cambio de horario de verano) en cada
 * paso — exactamente lo que {@code addLocalCalendarUnit} hacia a mano. {@code plusMonths}
 * ademas clampa el dia de mes igual que el JS (31 ene + 1 mes = 28/29 feb), sin codigo
 * adicional.
 *
 * <p>Se preserva la MISMA logica de negocio, incluida su asimetria documentada: el corte
 * de la serie (({@code hasta}/{@code repeticiones}/rango) se evalua contra el slot
 * ORIGINAL de cada candidato; el filtro final de "esta en el rango pedido" se evalua
 * contra el instante EFECTIVO (post-excepcion) — una ocurrencia movida fuera de su cadencia
 * natural puede no aparecer si su slot original cae fuera de la ventana de busqueda.
 */
public final class ExpansorOcurrencias {

    /** MAX_ITERATIONS del repo viejo — protege contra series sin `hasta`/`count` sobre un rango enorme. */
    private static final int MAX_ITERACIONES = 2000;

    private ExpansorOcurrencias() {
    }

    public static List<Ocurrencia> expandir(Instant iniciaEn, Integer duracionMinutos, ZoneId timezone,
                                              Recurrencia recurrencia, Instant desde, Instant hasta,
                                              List<Excepcion> excepciones) {
        Map<Instant, Excepcion> excepcionPorSlot = excepciones.stream()
                .collect(Collectors.toMap(Excepcion::inicioOcurrencia, Function.identity(), (a, b) -> a));

        List<Ocurrencia> resultado = new ArrayList<>();
        java.util.function.Consumer<Instant> recolectar = slot -> {
            Excepcion excepcion = excepcionPorSlot.get(slot);
            if (excepcion != null && excepcion.cancelada()) {
                return;
            }
            Instant efectivo = excepcion != null && excepcion.nuevoInicio() != null ? excepcion.nuevoInicio() : slot;
            if (efectivo.isBefore(desde) || efectivo.isAfter(hasta)) {
                return;
            }
            Integer duracion = excepcion != null && excepcion.nuevaDuracion() != null ? excepcion.nuevaDuracion()
                    : duracionMinutos;
            String titulo = excepcion != null ? excepcion.nuevoTitulo() : null;
            resultado.add(new Ocurrencia(slot, efectivo, duracion, titulo));
        };

        if (recurrencia == null) {
            recolectar.accept(iniciaEn);
            return resultado;
        }

        ZonedDateTime inicio = iniciaEn.atZone(timezone);
        int intervalo = recurrencia.intervalo();

        if (recurrencia.frecuencia() == FrecuenciaRecurrencia.SEMANAL && !recurrencia.diasSemana().isEmpty()) {
            expandirSemanalPorDias(inicio, intervalo, recurrencia, iniciaEn, hasta, recolectar);
        } else {
            expandirSimple(inicio, intervalo, recurrencia, iniciaEn, hasta, recolectar);
        }

        resultado.sort((a, b) -> a.iniciaEn().compareTo(b.iniciaEn()));
        return resultado;
    }

    private static void expandirSemanalPorDias(ZonedDateTime inicio, int intervalo, Recurrencia recurrencia,
                                                 Instant iniciaEn, Instant hasta,
                                                 java.util.function.Consumer<Instant> recolectar) {
        List<DayOfWeek> diasOrdenados = recurrencia.diasSemana().stream()
                .sorted((a, b) -> Integer.compare(a.getValue(), b.getValue())).toList();

        ZonedDateTime lunesBase = inicio.minusDays(inicio.getDayOfWeek().getValue() - 1L);
        int iteraciones = 0;
        int[] cuentaSerie = {0};

        while (iteraciones < MAX_ITERACIONES) {
            boolean pasoElRango = false;
            for (DayOfWeek dia : diasOrdenados) {
                ZonedDateTime candidatoZdt = lunesBase.plusDays(dia.getValue() - 1L);
                Instant candidato = candidatoZdt.toInstant();
                iteraciones++;
                if (candidato.isBefore(iniciaEn)) {
                    continue;
                }
                if (!dentroDeLaSerie(candidato, recurrencia, hasta, cuentaSerie[0])) {
                    pasoElRango = true;
                    break;
                }
                cuentaSerie[0]++;
                recolectar.accept(candidato);
                if (iteraciones >= MAX_ITERACIONES) {
                    break;
                }
            }
            if (pasoElRango || iteraciones >= MAX_ITERACIONES) {
                break;
            }
            lunesBase = lunesBase.plusDays(7L * intervalo);
        }
    }

    private static void expandirSimple(ZonedDateTime inicio, int intervalo, Recurrencia recurrencia,
                                        Instant iniciaEn, Instant hasta,
                                        java.util.function.Consumer<Instant> recolectar) {
        int n = 0;
        int iteraciones = 0;
        int cuentaSerie = 0;

        while (iteraciones < MAX_ITERACIONES) {
            ZonedDateTime candidatoZdt = switch (recurrencia.frecuencia()) {
                case DIARIA -> inicio.plusDays((long) intervalo * n);
                case MENSUAL -> inicio.plusMonths((long) intervalo * n);
                case SEMANAL -> inicio.plusDays(7L * intervalo * n);
            };
            Instant candidato = candidatoZdt.toInstant();

            if (!dentroDeLaSerie(candidato, recurrencia, hasta, cuentaSerie)) {
                break;
            }
            cuentaSerie++;
            recolectar.accept(candidato);
            iteraciones++;
            n++;
        }
    }

    /** withinSeriesBounds del repo viejo: evalua SIEMPRE contra el slot original, nunca el efectivo. */
    private static boolean dentroDeLaSerie(Instant candidato, Recurrencia recurrencia, Instant hasta,
                                            int cuentaSerie) {
        if (recurrencia.hasta() != null && candidato.isAfter(recurrencia.hasta())) {
            return false;
        }
        if (recurrencia.repeticiones() != null && cuentaSerie >= recurrencia.repeticiones()) {
            return false;
        }
        return !candidato.isAfter(hasta);
    }
}
