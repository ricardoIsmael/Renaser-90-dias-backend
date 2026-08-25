package com.renaser.os.calendar.domain.model.evento;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Puerto directo de recurrence.test.ts (repo viejo) — casos extraidos de ese archivo mas
 * los que documenta recurrence.ts en sus comentarios (clamping de mes, asimetria
 * slot-original vs efectivo).
 */
class ExpansorOcurrenciasTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Test
    void eventoSueltoDevuelveUnaSolaOcurrencia() {
        Instant inicio = Instant.parse("2026-09-01T19:00:00Z");
        List<Ocurrencia> ocurrencias = ExpansorOcurrencias.expandir(inicio, 60, LIMA, null,
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-30T00:00:00Z"), List.of());

        assertThat(ocurrencias).hasSize(1);
        assertThat(ocurrencias.get(0).iniciaEn()).isEqualTo(inicio);
        assertThat(ocurrencias.get(0).inicioOcurrencia()).isEqualTo(inicio);
    }

    @Test
    void eventoSueltoFueraDelRangoNoAparece() {
        Instant inicio = Instant.parse("2026-09-01T19:00:00Z");
        List<Ocurrencia> ocurrencias = ExpansorOcurrencias.expandir(inicio, 60, LIMA, null,
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-30T00:00:00Z"), List.of());

        assertThat(ocurrencias).isEmpty();
    }

    @Test
    void diariaConIntervaloDos() {
        Instant inicio = Instant.parse("2026-09-01T10:00:00Z");
        Recurrencia r = new Recurrencia(FrecuenciaRecurrencia.DIARIA, 2, null, 5, Set.of());

        List<Ocurrencia> ocurrencias = ExpansorOcurrencias.expandir(inicio, null, LIMA, r, inicio,
                Instant.parse("2026-09-30T00:00:00Z"), List.of());

        assertThat(ocurrencias).hasSize(5);
        assertThat(ocurrencias.get(0).iniciaEn()).isEqualTo("2026-09-01T10:00:00Z");
        assertThat(ocurrencias.get(1).iniciaEn()).isEqualTo("2026-09-03T10:00:00Z");
        assertThat(ocurrencias.get(4).iniciaEn()).isEqualTo("2026-09-09T10:00:00Z");
    }

    @Test
    void semanalConVariosDiasDeLaSemana() {
        // Martes 2026-09-01 19:00 UTC (America/Lima = UTC-5, asi que localmente es lunes 14:00 —
        // no importa para la aritmetica: lo que importa es que lunes/miercoles/viernes salgan cada semana).
        Instant inicio = Instant.parse("2026-09-01T19:00:00Z"); // martes
        Recurrencia r = new Recurrencia(FrecuenciaRecurrencia.SEMANAL, 1, null, null,
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));

        List<Ocurrencia> ocurrencias = ExpansorOcurrencias.expandir(inicio, null, LIMA, r, inicio,
                Instant.parse("2026-09-11T23:59:59Z"), List.of());

        // Semana 1 (la que contiene `inicio`, martes): el lunes de esa semana ya paso, solo
        // salen miercoles y viernes. Semana 2: lunes, miercoles y viernes completos.
        assertThat(ocurrencias).hasSize(5);
        assertThat(ocurrencias).allMatch(o -> {
            DayOfWeek dia = o.iniciaEn().atZone(LIMA).getDayOfWeek();
            return dia == DayOfWeek.MONDAY || dia == DayOfWeek.WEDNESDAY || dia == DayOfWeek.FRIDAY;
        });
    }

    @Test
    void mensualClampeaFinDeMes() {
        // 31 de enero + 1 mes -> 28 de febrero (2027 no es bisiesto), igual que el repo viejo.
        Instant inicio = Instant.parse("2027-01-31T15:00:00Z");
        Recurrencia r = new Recurrencia(FrecuenciaRecurrencia.MENSUAL, 1, null, 3, Set.of());

        List<Ocurrencia> ocurrencias = ExpansorOcurrencias.expandir(inicio, null, ZoneId.of("UTC"), r, inicio,
                Instant.parse("2027-12-31T00:00:00Z"), List.of());

        assertThat(ocurrencias).hasSize(3);
        assertThat(ocurrencias.get(0).iniciaEn().toString()).startsWith("2027-01-31");
        assertThat(ocurrencias.get(1).iniciaEn().toString()).startsWith("2027-02-28");
        // Marzo vuelve a tener 31 dias: no se queda pegado en 28 (re-clamping desde el original cada vez).
        assertThat(ocurrencias.get(2).iniciaEn().toString()).startsWith("2027-03-31");
    }

    @Test
    void repeticionesLimitaLaSerie() {
        Instant inicio = Instant.parse("2026-09-01T10:00:00Z");
        Recurrencia r = new Recurrencia(FrecuenciaRecurrencia.DIARIA, 1, null, 3, Set.of());

        List<Ocurrencia> ocurrencias = ExpansorOcurrencias.expandir(inicio, null, ZoneId.of("UTC"), r, inicio,
                Instant.parse("2026-12-01T00:00:00Z"), List.of());

        assertThat(ocurrencias).hasSize(3);
    }

    @Test
    void hastaLimitaLaSerie() {
        Instant inicio = Instant.parse("2026-09-01T10:00:00Z");
        Recurrencia r = new Recurrencia(FrecuenciaRecurrencia.DIARIA, 1, Instant.parse("2026-09-03T10:00:00Z"), null,
                Set.of());

        List<Ocurrencia> ocurrencias = ExpansorOcurrencias.expandir(inicio, null, ZoneId.of("UTC"), r, inicio,
                Instant.parse("2026-12-01T00:00:00Z"), List.of());

        assertThat(ocurrencias).hasSize(3); // 1, 2 y 3 de septiembre — el 4 ya supera `hasta`
    }

    @Test
    void excepcionCanceladaNoAparece() {
        Instant inicio = Instant.parse("2026-09-01T10:00:00Z");
        Recurrencia r = new Recurrencia(FrecuenciaRecurrencia.DIARIA, 1, null, 3, Set.of());
        Instant segundaOcurrencia = Instant.parse("2026-09-02T10:00:00Z");
        Excepcion cancelada = Excepcion.cancelar(EventoId.newId(), segundaOcurrencia);

        List<Ocurrencia> ocurrencias = ExpansorOcurrencias.expandir(inicio, null, ZoneId.of("UTC"), r, inicio,
                Instant.parse("2026-12-01T00:00:00Z"), List.of(cancelada));

        assertThat(ocurrencias).hasSize(2);
        assertThat(ocurrencias).noneMatch(o -> o.inicioOcurrencia().equals(segundaOcurrencia));
    }

    @Test
    void excepcionReprogramadaConservaLaClaveOriginalPeroMueveElEfectivo() {
        Instant inicio = Instant.parse("2026-09-01T10:00:00Z");
        Recurrencia r = new Recurrencia(FrecuenciaRecurrencia.DIARIA, 1, null, 3, Set.of());
        Instant primeraOcurrencia = inicio;
        Instant nuevoInicio = Instant.parse("2026-09-01T15:00:00Z");
        Excepcion movida = Excepcion.reprogramar(EventoId.newId(), primeraOcurrencia, nuevoInicio, 90, "Nuevo titulo");

        List<Ocurrencia> ocurrencias = ExpansorOcurrencias.expandir(inicio, 60, ZoneId.of("UTC"), r, inicio,
                Instant.parse("2026-12-01T00:00:00Z"), List.of(movida));

        Ocurrencia primera = ocurrencias.stream().filter(o -> o.inicioOcurrencia().equals(primeraOcurrencia))
                .findFirst().orElseThrow();
        assertThat(primera.iniciaEn()).isEqualTo(nuevoInicio);
        assertThat(primera.duracionMinutos()).isEqualTo(90);
        assertThat(primera.titulo()).isEqualTo("Nuevo titulo");
    }

    @Test
    void ocurrenciasSinRetitularTienenTituloNulo() {
        Instant inicio = Instant.parse("2026-09-01T10:00:00Z");
        List<Ocurrencia> ocurrencias = ExpansorOcurrencias.expandir(inicio, 60, ZoneId.of("UTC"), null, inicio,
                Instant.parse("2026-09-02T00:00:00Z"), List.of());

        assertThat(ocurrencias.get(0).titulo()).isNull();
    }
}
