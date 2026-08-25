package com.renaser.os.calendar.domain.model.recordatorio;

import com.renaser.os.calendar.domain.model.evento.ReglaRecordatorio;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Puerto directo de reminderInstantsFor()/diasDeVentana() (reminders.ts, repo viejo). */
class CalculadoraRecordatoriosTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Instant AHORA = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant OCURRENCIA = Instant.parse("2026-09-10T19:00:00Z"); // 14:00 Lima

    @Test
    void minutosAntesRestaMinutosExactos() {
        ReglaRecordatorio regla = ReglaRecordatorio.minutosAntes(1, 10);
        List<InstanteRecordatorio> instantes = CalculadoraRecordatorios.instantesPara(OCURRENCIA, List.of(regla),
                LIMA, AHORA);

        assertThat(instantes).hasSize(1);
        assertThat(instantes.get(0).enviarEn()).isEqualTo(OCURRENCIA.minusSeconds(600));
    }

    @Test
    void diasAntesRestaVeinticuatroHorasExactasPorDia() {
        // Puerto LITERAL del repo viejo: resta milisegundos fijos, NO "mismo dia calendario N
        // dias antes" con reajuste de zona horaria — ver javadoc de CalculadoraRecordatorios.
        ReglaRecordatorio regla = ReglaRecordatorio.diasAntes(1, 1);
        List<InstanteRecordatorio> instantes = CalculadoraRecordatorios.instantesPara(OCURRENCIA, List.of(regla),
                LIMA, AHORA);

        assertThat(instantes.get(0).enviarEn()).isEqualTo(OCURRENCIA.minusSeconds(86_400));
    }

    @Test
    void horaDelDiaResuelveAlMismoDiaEnLaZonaDelEvento() {
        ReglaRecordatorio regla = ReglaRecordatorio.horaDelDia(1, LocalTime.of(6, 0));
        List<InstanteRecordatorio> instantes = CalculadoraRecordatorios.instantesPara(OCURRENCIA, List.of(regla),
                LIMA, AHORA);

        // 06:00 Lima del 10-sep = 11:00 UTC del mismo dia.
        assertThat(instantes.get(0).enviarEn()).isEqualTo(Instant.parse("2026-09-10T11:00:00Z"));
    }

    @Test
    void instantesYaPasadosSeDescartan() {
        ReglaRecordatorio regla = ReglaRecordatorio.minutosAntes(1, 10);
        Instant ahoraDespuesDelEnvio = OCURRENCIA; // el propio inicio de la ocurrencia ya paso el envio de 10 min antes
        List<InstanteRecordatorio> instantes = CalculadoraRecordatorios.instantesPara(OCURRENCIA, List.of(regla),
                LIMA, ahoraDespuesDelEnvio);

        assertThat(instantes).isEmpty();
    }

    @Test
    void seOrdenanPorInstanteDeEnvio() {
        ReglaRecordatorio diaAntes = ReglaRecordatorio.diasAntes(1, 1);
        ReglaRecordatorio minutosAntes = ReglaRecordatorio.minutosAntes(2, 10);
        List<InstanteRecordatorio> instantes = CalculadoraRecordatorios.instantesPara(OCURRENCIA,
                List.of(minutosAntes, diaAntes), LIMA, AHORA);

        assertThat(instantes).hasSize(2);
        assertThat(instantes.get(0).regla().tipo()).isEqualTo(com.renaser.os.calendar.domain.model.evento.TipoReglaRecordatorio.DIAS_ANTES);
        assertThat(instantes.get(1).regla().tipo()).isEqualTo(com.renaser.os.calendar.domain.model.evento.TipoReglaRecordatorio.MINUTOS_ANTES);
    }

    @Test
    void diasDeVentanaUsaElMinimoSiNingunaReglaLoSupera() {
        int dias = CalculadoraRecordatorios.diasDeVentana(List.of(ReglaRecordatorio.minutosAntes(1, 10)), 3);
        assertThat(dias).isEqualTo(3);
    }

    @Test
    void diasDeVentanaCreceConDiasAntes() {
        // 7 dias antes exige mirar al menos 8 dias por delante (regla.valor + 1).
        int dias = CalculadoraRecordatorios.diasDeVentana(List.of(ReglaRecordatorio.diasAntes(1, 7)), 3);
        assertThat(dias).isEqualTo(8);
    }

    @Test
    void diasDeVentanaCreceConMinutosAntesGrandes() {
        // 3 dias en minutos (4320) exige ceil(4320/1440)+1 = 4 dias.
        int dias = CalculadoraRecordatorios.diasDeVentana(List.of(ReglaRecordatorio.minutosAntes(1, 4320)), 3);
        assertThat(dias).isEqualTo(4);
    }

    @Test
    void horaDelDiaNoAmpliaLaVentana() {
        int dias = CalculadoraRecordatorios.diasDeVentana(List.of(ReglaRecordatorio.horaDelDia(1, LocalTime.NOON)), 3);
        assertThat(dias).isEqualTo(3);
    }
}
