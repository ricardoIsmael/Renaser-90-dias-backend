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
}
