package com.renaser.os.users.domain.model.participante;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParticipacionProgramaTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Test
    void activarSeguimientoPersonalArrancaEnDiaUnoFaseInicialYZonaPorDefecto() {
        UserId participanteId = UserId.of(UUID.randomUUID());

        ParticipacionPrograma p = ParticipacionPrograma.activarSeguimientoPersonal(participanteId, CLOCK);

        assertThat(p.diaPrograma()).isEqualTo(1);
        assertThat(p.fase()).isEqualTo(FasePrograma.PHASE_1_REBIRTH);
        assertThat(p.timezone()).isEqualTo(ZoneId.of("America/Lima"));
        assertThat(p.fechaInicio()).isEqualTo(CLOCK.today());
        assertThat(p.mentorId()).isNull();
        assertThat(p.celulaId()).isNull();
        assertThat(p.estaActivado()).isTrue();
        assertThat(p.programaCompletado()).isFalse();
    }

    @Test
    void activarSeguimientoPersonalRechazaParticipanteIdNulo() {
        assertThatThrownBy(() -> ParticipacionPrograma.activarSeguimientoPersonal(null, CLOCK))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fechaGraduacionEsperadaEsFechaInicioMasNoventaDias() {
        ParticipacionPrograma p = ParticipacionPrograma.activarSeguimientoPersonal(UserId.of(UUID.randomUUID()), CLOCK);

        assertThat(p.fechaGraduacionEsperada()).isEqualTo(CLOCK.today().plusDays(90));
    }

    @Test
    void avanzarDiaIncrementaYActualizaTimestamp() {
        ParticipacionPrograma p = ParticipacionPrograma.activarSeguimientoPersonal(UserId.of(UUID.randomUUID()), CLOCK);
        FixedClock later = FixedClock.at(CLOCK.now().plusSeconds(3600));

        p.avanzarDia(later);

        assertThat(p.diaPrograma()).isEqualTo(2);
        assertThat(p.actualizadoEn()).isEqualTo(later.now());
    }

    @Test
    void avanzarDiaNuncaSuperaNoventa() {
        ParticipacionPrograma p = ParticipacionPrograma.rehydrate(UserId.of(UUID.randomUUID()), null, null, 90,
                FasePrograma.PHASE_4_ASCENSION, LocalDate.of(2026, 1, 1), CLOCK.now(), ZoneId.of("America/Lima"),
                false, 0, CLOCK.now(), CLOCK.now());

        p.avanzarDia(CLOCK);

        assertThat(p.diaPrograma()).isEqualTo(90);
    }

    @Test
    void asignarMentorCambiaElMentorYElTimestamp() {
        ParticipacionPrograma p = ParticipacionPrograma.activarSeguimientoPersonal(UserId.of(UUID.randomUUID()), CLOCK);
        UserId mentorId = UserId.of(UUID.randomUUID());
        FixedClock later = FixedClock.at(CLOCK.now().plusSeconds(60));

        p.asignarMentor(mentorId, later);

        assertThat(p.mentorId()).isEqualTo(mentorId);
        assertThat(p.actualizadoEn()).isEqualTo(later.now());
    }

    @Test
    void asignarMentorRechazaMentorIdNulo() {
        ParticipacionPrograma p = ParticipacionPrograma.activarSeguimientoPersonal(UserId.of(UUID.randomUUID()), CLOCK);

        assertThatThrownBy(() -> p.asignarMentor(null, CLOCK)).isInstanceOf(NullPointerException.class);
    }

    // ─── fijarDia (panel admin de aprendices, gap #7) ──────────────────────

    @Test
    void fijarDiaEstableceElDiaExactoYActualizaTimestamp() {
        ParticipacionPrograma p = ParticipacionPrograma.activarSeguimientoPersonal(UserId.of(UUID.randomUUID()), CLOCK);
        FixedClock later = FixedClock.at(CLOCK.now().plusSeconds(60));

        p.fijarDia(45, later);

        assertThat(p.diaPrograma()).isEqualTo(45);
        assertThat(p.actualizadoEn()).isEqualTo(later.now());
    }

    @Test
    void fijarDiaAceptaElPisoCero() {
        ParticipacionPrograma p = ParticipacionPrograma.activarSeguimientoPersonal(UserId.of(UUID.randomUUID()), CLOCK);

        p.fijarDia(0, CLOCK);

        assertThat(p.diaPrograma()).isZero();
    }

    @Test
    void fijarDiaAceptaElTopeNoventa() {
        ParticipacionPrograma p = ParticipacionPrograma.activarSeguimientoPersonal(UserId.of(UUID.randomUUID()), CLOCK);

        p.fijarDia(90, CLOCK);

        assertThat(p.diaPrograma()).isEqualTo(90);
    }

    @Test
    void fijarDiaRechazaValorNegativo() {
        ParticipacionPrograma p = ParticipacionPrograma.activarSeguimientoPersonal(UserId.of(UUID.randomUUID()), CLOCK);

        assertThatThrownBy(() -> p.fijarDia(-1, CLOCK)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fijarDiaRechazaValorMayorANoventa() {
        ParticipacionPrograma p = ParticipacionPrograma.activarSeguimientoPersonal(UserId.of(UUID.randomUUID()), CLOCK);

        assertThatThrownBy(() -> p.fijarDia(91, CLOCK)).isInstanceOf(IllegalArgumentException.class);
    }

    // ─── asignarCelula / quitarCelula (panel admin, gap #25) ───────────────

    @Test
    void asignarCelulaCambiaLaCelulaYElTimestamp() {
        ParticipacionPrograma p = ParticipacionPrograma.activarSeguimientoPersonal(UserId.of(UUID.randomUUID()), CLOCK);
        UUID celulaId = UUID.randomUUID();
        FixedClock later = FixedClock.at(CLOCK.now().plusSeconds(60));

        p.asignarCelula(celulaId, later);

        assertThat(p.celulaId()).isEqualTo(celulaId);
        assertThat(p.actualizadoEn()).isEqualTo(later.now());
    }

    @Test
    void asignarCelulaRechazaCelulaIdNula() {
        ParticipacionPrograma p = ParticipacionPrograma.activarSeguimientoPersonal(UserId.of(UUID.randomUUID()), CLOCK);

        assertThatThrownBy(() -> p.asignarCelula(null, CLOCK)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void quitarCelulaLimpiaElCampoYActualizaTimestamp() {
        ParticipacionPrograma p = ParticipacionPrograma.activarSeguimientoPersonal(UserId.of(UUID.randomUUID()), CLOCK);
        p.asignarCelula(UUID.randomUUID(), CLOCK);
        FixedClock later = FixedClock.at(CLOCK.now().plusSeconds(60));

        p.quitarCelula(later);

        assertThat(p.celulaId()).isNull();
        assertThat(p.actualizadoEn()).isEqualTo(later.now());
    }

    @Test
    void quitarCelulaEsIdempotenteCuandoYaNoTieneCelula() {
        ParticipacionPrograma p = ParticipacionPrograma.activarSeguimientoPersonal(UserId.of(UUID.randomUUID()), CLOCK);

        p.quitarCelula(CLOCK);

        assertThat(p.celulaId()).isNull();
    }

    // ─── renombrarRetoPersonal (hueco #1, U-05) ────────────────────────────

    @Test
    void nuevaParticipacionArrancaSinRetoPersonalNiTipoDeMeta() {
        ParticipacionPrograma p = ParticipacionPrograma.activarSeguimientoPersonal(UserId.of(UUID.randomUUID()), CLOCK);

        assertThat(p.nombreRetoPersonal()).isNull();
        assertThat(p.tipoMeta()).isNull();
        assertThat(p.programaCompletadoEn()).isNull();
    }

    @Test
    void renombrarRetoPersonalCambiaElNombreYElTimestamp() {
        ParticipacionPrograma p = ParticipacionPrograma.activarSeguimientoPersonal(UserId.of(UUID.randomUUID()), CLOCK);
        FixedClock later = FixedClock.at(CLOCK.now().plusSeconds(60));

        p.renombrarRetoPersonal("Correr una maraton", later);

        assertThat(p.nombreRetoPersonal()).isEqualTo("Correr una maraton");
        assertThat(p.actualizadoEn()).isEqualTo(later.now());
    }

    @Test
    void rehydrateConLosTresCamposNuevosLosExponePorElGetter() {
        ParticipacionPrograma p = ParticipacionPrograma.rehydrate(UserId.of(UUID.randomUUID()), null, null, 90,
                FasePrograma.PHASE_4_ASCENSION, LocalDate.of(2026, 1, 1), CLOCK.now(), ZoneId.of("America/Lima"),
                true, 5, CLOCK.now(), CLOCK.now(), TipoMeta.PHYSICAL, "Correr una maraton", CLOCK.now());

        assertThat(p.tipoMeta()).isEqualTo(TipoMeta.PHYSICAL);
        assertThat(p.nombreRetoPersonal()).isEqualTo("Correr una maraton");
        assertThat(p.programaCompletadoEn()).isEqualTo(CLOCK.now());
        assertThat(p.programaCompletado()).isTrue();
        assertThat(p.diaPostPrograma()).isEqualTo(5);
    }

    // ─── activarPrograma / opcionesDeActivacion (D-66) ─────────────────────

    private ParticipacionPrograma traineePausado() {
        return ParticipacionPrograma.inscribirTraineeAprobado(UserId.of(UUID.randomUUID()), CLOCK);
    }

    /** Corregido tras revision del dueño del proyecto: HOY queda fuera del rango — el
     * reloj avanza a medianoche y firmar de tarde dejaria un Dia 1 de pocas horas. */
    @Test
    void activarProgramaRechazaEmpezarHoy() {
        ParticipacionPrograma p = traineePausado();

        assertThatThrownBy(() -> p.activarPrograma(CLOCK.today(), CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(p.estaActivado()).isFalse();
    }

    @Test
    void activarProgramaAceptaElBordeDeManiana() {
        ParticipacionPrograma p = traineePausado();

        p.activarPrograma(CLOCK.today().plusDays(1), CLOCK);

        assertThat(p.estaActivado()).isTrue();
        assertThat(p.fechaInicio()).isEqualTo(CLOCK.today().plusDays(1));
        assertThat(p.diaPrograma()).isZero(); // arranca en 0: el cron lo sube cuando llegue el dia
        assertThat(p.diaProgramaAvanzadoEl()).isNull();
        assertThat(p.programaActivadoEn()).isEqualTo(CLOCK.now());
    }

    @Test
    void activarProgramaConUnaFechaFuturaDejaElDiaEnCeroHastaQueLlegue() {
        ParticipacionPrograma p = traineePausado();

        p.activarPrograma(CLOCK.today().plusDays(2), CLOCK);

        assertThat(p.estaActivado()).isTrue();
        assertThat(p.fechaInicio()).isEqualTo(CLOCK.today().plusDays(2));
        assertThat(p.diaPrograma()).isZero();
        assertThat(p.diaProgramaAvanzadoEl()).isNull();
    }

    @Test
    void activarProgramaAceptaElBordeDeTresDias() {
        ParticipacionPrograma p = traineePausado();

        p.activarPrograma(CLOCK.today().plusDays(3), CLOCK);

        assertThat(p.fechaInicio()).isEqualTo(CLOCK.today().plusDays(3));
    }

    @Test
    void activarProgramaRechazaUnaFechaPasada() {
        ParticipacionPrograma p = traineePausado();

        assertThatThrownBy(() -> p.activarPrograma(CLOCK.today().minusDays(1), CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void activarProgramaRechazaMasDeTresDiasDeEspera() {
        ParticipacionPrograma p = traineePausado();

        assertThatThrownBy(() -> p.activarPrograma(CLOCK.today().plusDays(4), CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void activarProgramaRechazaReactivarConUnaFechaDistinta() {
        ParticipacionPrograma p = traineePausado();
        p.activarPrograma(CLOCK.today().plusDays(1), CLOCK);

        assertThatThrownBy(() -> p.activarPrograma(CLOCK.today().plusDays(2), CLOCK))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Distincion tecnica, no de negocio (E-?): un reintento de red con la MISMA fecha
     * ya activada es un no-op, nunca un error — así lo pidio el dueño del proyecto
     * explicitamente para este endpoint. */
    @Test
    void activarProgramaConLaMismaFechaYaActivadaEsUnNoOpSilencioso() {
        ParticipacionPrograma p = traineePausado();
        p.activarPrograma(CLOCK.today().plusDays(1), CLOCK);
        Instant activadoEnOriginal = p.programaActivadoEn();

        assertThatCode(() -> p.activarPrograma(CLOCK.today().plusDays(1), CLOCK)).doesNotThrowAnyException();

        assertThat(p.fechaInicio()).isEqualTo(CLOCK.today().plusDays(1));
        assertThat(p.programaActivadoEn()).isEqualTo(activadoEnOriginal); // no se toco nada
    }

    @Test
    void activarProgramaRechazaFechaElegidaNula() {
        ParticipacionPrograma p = traineePausado();

        assertThatThrownBy(() -> p.activarPrograma(null, CLOCK)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void opcionesDeActivacionDevuelveManianaYLosDosSiguientesSinIncluirHoy() {
        ParticipacionPrograma p = traineePausado();

        assertThat(p.opcionesDeActivacion(CLOCK)).containsExactly(CLOCK.today().plusDays(1),
                CLOCK.today().plusDays(2), CLOCK.today().plusDays(3));
    }

    // ─── avanzarDiaDelPrograma (cron nocturno, D-66) ────────────────────────

    @Test
    void avanzarDiaDelProgramaNoAvanzaUnParticipantePausado() {
        ParticipacionPrograma p = traineePausado();

        boolean avanzo = p.avanzarDiaDelPrograma(CLOCK.today(), CLOCK);

        assertThat(avanzo).isFalse();
        assertThat(p.diaPrograma()).isZero();
    }

    @Test
    void avanzarDiaDelProgramaNoAvanzaSiLaFechaDeInicioNoLlego() {
        ParticipacionPrograma p = traineePausado();
        p.activarPrograma(CLOCK.today().plusDays(2), CLOCK);

        boolean avanzo = p.avanzarDiaDelPrograma(CLOCK.today().plusDays(1), CLOCK);

        assertThat(avanzo).isFalse();
        assertThat(p.diaPrograma()).isZero();
    }

    @Test
    void avanzarDiaDelProgramaAvanzaElDiaQueLlegaLaFechaDeInicio() {
        ParticipacionPrograma p = traineePausado();
        p.activarPrograma(CLOCK.today().plusDays(2), CLOCK);

        boolean avanzo = p.avanzarDiaDelPrograma(CLOCK.today().plusDays(2), CLOCK);

        assertThat(avanzo).isTrue();
        assertThat(p.diaPrograma()).isEqualTo(1);
        assertThat(p.diaProgramaAvanzadoEl()).isEqualTo(CLOCK.today().plusDays(2));
    }

    @Test
    void avanzarDiaDelProgramaEsIdempotentePorDiaCalendario() {
        ParticipacionPrograma p = traineePausado();
        p.activarPrograma(CLOCK.today().plusDays(1), CLOCK);
        p.avanzarDiaDelPrograma(CLOCK.today().plusDays(1), CLOCK);
        assertThat(p.diaPrograma()).isEqualTo(1);

        boolean segundaCorridaMismoDia = p.avanzarDiaDelPrograma(CLOCK.today().plusDays(1), CLOCK);

        assertThat(segundaCorridaMismoDia).isFalse();
        assertThat(p.diaPrograma()).isEqualTo(1);

        boolean corridaDelDiaSiguiente = p.avanzarDiaDelPrograma(CLOCK.today().plusDays(2), CLOCK);

        assertThat(corridaDelDiaSiguiente).isTrue();
        assertThat(p.diaPrograma()).isEqualTo(2);
    }

    @Test
    void avanzarDiaDelProgramaRespetaElTopeDeNoventa() {
        ParticipacionPrograma p = ParticipacionPrograma.rehydrate(UserId.of(UUID.randomUUID()), null, null, 90,
                FasePrograma.PHASE_4_ASCENSION, LocalDate.of(2026, 1, 1), CLOCK.now(), ZoneId.of("America/Lima"),
                false, 0, CLOCK.now(), CLOCK.now(), null, null, null, CLOCK.today().minusDays(1));

        boolean avanzo = p.avanzarDiaDelPrograma(CLOCK.today(), CLOCK);

        assertThat(avanzo).isFalse();
        assertThat(p.diaPrograma()).isEqualTo(90);
    }

    @Test
    void avanzarDiaDelProgramaRecalculaLaFaseAlCruzarUnUmbral() {
        ParticipacionPrograma p = ParticipacionPrograma.rehydrate(UserId.of(UUID.randomUUID()), null, null, 7,
                FasePrograma.PHASE_1_REBIRTH, LocalDate.of(2026, 1, 1), CLOCK.now(), ZoneId.of("America/Lima"),
                false, 0, CLOCK.now(), CLOCK.now(), null, null, null, CLOCK.today().minusDays(1));

        p.avanzarDiaDelPrograma(CLOCK.today(), CLOCK);

        assertThat(p.diaPrograma()).isEqualTo(8);
        assertThat(p.fase()).isEqualTo(FasePrograma.PHASE_2_DEVELOPMENT);
    }

    @Test
    void avanzarDiaDelProgramaRechazaHoyEnZonaNulo() {
        ParticipacionPrograma p = traineePausado();

        assertThatThrownBy(() -> p.avanzarDiaDelPrograma(null, CLOCK)).isInstanceOf(NullPointerException.class);
    }

    // ─── fijarDia recalcula la fase (D-66: corrige el bug real de dos fases en el mismo dia) ─

    @Test
    void fijarDiaRecalculaLaFaseAunqueVengaDeUnaFaseVieja() {
        ParticipacionPrograma p = ParticipacionPrograma.rehydrate(UserId.of(UUID.randomUUID()), null, null, 7,
                FasePrograma.PHASE_1_REBIRTH, LocalDate.of(2026, 1, 1), CLOCK.now(), ZoneId.of("America/Lima"),
                false, 0, CLOCK.now(), CLOCK.now());

        p.fijarDia(35, CLOCK);

        assertThat(p.fase()).isEqualTo(FasePrograma.PHASE_3_ALCHEMIST_WARRIOR);
    }
}
