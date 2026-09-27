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

    // --- diaProgramaDerivado: la cuenta que define el reloj (V20) ---------

    @Test
    void diaProgramaDerivadoCuentaElDiaDeInicioComoDiaUno() {
        ParticipacionPrograma p = ParticipacionPrograma.activarSeguimientoPersonal(UserId.of(UUID.randomUUID()), CLOCK);

        assertThat(p.diaProgramaDerivado(CLOCK.today())).isEqualTo(1);
        assertThat(p.diaProgramaDerivado(CLOCK.today().plusDays(1))).isEqualTo(2);
        assertThat(p.diaProgramaDerivado(CLOCK.today().plusDays(89))).isEqualTo(90);
    }

    @Test
    void diaProgramaDerivadoNuncaSuperaNoventa() {
        ParticipacionPrograma p = ParticipacionPrograma.activarSeguimientoPersonal(UserId.of(UUID.randomUUID()), CLOCK);

        assertThat(p.diaProgramaDerivado(CLOCK.today().plusDays(500))).isEqualTo(90);
    }

    @Test
    void diaProgramaDerivadoEsCeroMientrasElRelojNoArranco() {
        ParticipacionPrograma pausado = traineePausado();
        assertThat(pausado.diaProgramaDerivado(CLOCK.today())).isZero();

        ParticipacionPrograma activadoParaDespues = traineePausado();
        activadoParaDespues.activarPrograma(CLOCK.today().plusDays(2), CLOCK);
        assertThat(activadoParaDespues.diaProgramaDerivado(CLOCK.today().plusDays(1))).isZero();
    }

    @Test
    void diaProgramaDerivadoDescuentaLosDiasDeAjuste() {
        ParticipacionPrograma p = ParticipacionPrograma.activarSeguimientoPersonal(UserId.of(UUID.randomUUID()), CLOCK);
        // dia 40 de calendario, se lo devuelve al 34: 6 dias que no cuentan (viajo una semana)
        p.fijarDia(34, FixedClock.at(CLOCK.now().plusSeconds(39L * 86400)));

        assertThat(p.diasAjuste()).isEqualTo(6);
        assertThat(p.diaProgramaDerivado(CLOCK.today().plusDays(39))).isEqualTo(34);
        assertThat(p.diaProgramaDerivado(CLOCK.today().plusDays(40))).isEqualTo(35);
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

    // --- sincronizarDiaDelPrograma (barrido del reloj, V20) --------------

    @Test
    void sincronizarNoTocaUnParticipantePausado() {
        ParticipacionPrograma p = traineePausado();

        boolean cambio = p.sincronizarDiaDelPrograma(CLOCK.today(), CLOCK);

        assertThat(cambio).isFalse();
        assertThat(p.diaPrograma()).isZero();
    }

    @Test
    void sincronizarNoTocaNadaSiLaFechaDeInicioNoLlego() {
        ParticipacionPrograma p = traineePausado();
        p.activarPrograma(CLOCK.today().plusDays(2), CLOCK);

        boolean cambio = p.sincronizarDiaDelPrograma(CLOCK.today().plusDays(1), CLOCK);

        assertThat(cambio).isFalse();
        assertThat(p.diaPrograma()).isZero();
    }

    @Test
    void sincronizarPoneDiaUnoElDiaQueLlegaLaFechaDeInicio() {
        ParticipacionPrograma p = traineePausado();
        p.activarPrograma(CLOCK.today().plusDays(2), CLOCK);

        boolean cambio = p.sincronizarDiaDelPrograma(CLOCK.today().plusDays(2), CLOCK);

        assertThat(cambio).isTrue();
        assertThat(p.diaPrograma()).isEqualTo(1);
        assertThat(p.diaProgramaAvanzadoEl()).isEqualTo(CLOCK.today().plusDays(2));
    }

    @Test
    void sincronizarDosVecesElMismoDiaNoCambiaNadaLaSegundaVez() {
        ParticipacionPrograma p = traineePausado();
        p.activarPrograma(CLOCK.today().plusDays(1), CLOCK);
        p.sincronizarDiaDelPrograma(CLOCK.today().plusDays(1), CLOCK);
        assertThat(p.diaPrograma()).isEqualTo(1);

        boolean segundaCorridaMismoDia = p.sincronizarDiaDelPrograma(CLOCK.today().plusDays(1), CLOCK);

        assertThat(segundaCorridaMismoDia).isFalse();
        assertThat(p.diaPrograma()).isEqualTo(1);

        boolean corridaDelDiaSiguiente = p.sincronizarDiaDelPrograma(CLOCK.today().plusDays(2), CLOCK);

        assertThat(corridaDelDiaSiguiente).isTrue();
        assertThat(p.diaPrograma()).isEqualTo(2);
    }

    /**
     * La razon de ser del modelo derivado (V20, BITACORA E-91): con el modelo incremental
     * viejo, tres dias sin que el barrido corriera se perdian para siempre -- al volver
     * sumaba 1 y el aprendiz quedaba tres dias atrasado el resto del programa.
     */
    @Test
    void sincronizarSePoneAlDiaDeUnaAunqueNadieHayaCorridoEnTresDias() {
        ParticipacionPrograma p = traineePausado();
        p.activarPrograma(CLOCK.today().plusDays(1), CLOCK);
        p.sincronizarDiaDelPrograma(CLOCK.today().plusDays(1), CLOCK);
        assertThat(p.diaPrograma()).isEqualTo(1);

        boolean cambio = p.sincronizarDiaDelPrograma(CLOCK.today().plusDays(4), CLOCK);

        assertThat(cambio).isTrue();
        assertThat(p.diaPrograma()).isEqualTo(4);
    }

    /** El fixture arranca YA graduado desde el 2026-09-15: asi este test sigue verificando una
     * sola cosa —que el dia no pasa de 90— y no se mezcla con la graduacion, que tiene sus
     * propios tests abajo. Con `programaCompletado` en falso, la primera corrida devolveria
     * `true` (marcaria la graduacion), que es justo lo que verifica
     * {@link #unaFilaQueYaEstabaEnElDiaNoventaSeGraduaEnLaCorridaSiguiente}. */
    @Test
    void sincronizarRespetaElTopeDeNoventa() {
        ParticipacionPrograma p = ParticipacionPrograma.rehydrate(UserId.of(UUID.randomUUID()), null, null, 90,
                FasePrograma.PHASE_4_ASCENSION, LocalDate.of(2026, 1, 1), CLOCK.now(), ZoneId.of("America/Lima"),
                true, 0, CLOCK.now(), CLOCK.now(), null, null, CLOCK.now(), CLOCK.today());

        boolean cambio = p.sincronizarDiaDelPrograma(CLOCK.today(), CLOCK);

        assertThat(cambio).isFalse();
        assertThat(p.diaPrograma()).isEqualTo(90);
    }

    @Test
    void sincronizarRecalculaLaFaseAlCruzarUnUmbral() {
        ParticipacionPrograma p = ParticipacionPrograma.rehydrate(UserId.of(UUID.randomUUID()), null, null, 7,
                FasePrograma.PHASE_1_REBIRTH, CLOCK.today().minusDays(7), CLOCK.now(), ZoneId.of("America/Lima"),
                false, 0, CLOCK.now(), CLOCK.now(), null, null, null, CLOCK.today().minusDays(1));

        p.sincronizarDiaDelPrograma(CLOCK.today(), CLOCK);

        assertThat(p.diaPrograma()).isEqualTo(8);
        assertThat(p.fase()).isEqualTo(FasePrograma.PHASE_2_DEVELOPMENT);
    }

    @Test
    void sincronizarRechazaHoyEnZonaNulo() {
        ParticipacionPrograma p = traineePausado();

        assertThatThrownBy(() -> p.sincronizarDiaDelPrograma(null, CLOCK)).isInstanceOf(NullPointerException.class);
    }

    // --- graduacion al dia 90 (reglas confirmadas por el dueño el 2026-09-14) ---

    /**
     * Sin condiciones: llegar al dia 90 alcanza. No se miran habitos, ni puntos, ni firmas —
     * ver docs/FEATURE_POST_PROGRAM.md. Y `dia_post_programa` queda en 0.
     */
    @Test
    void sincronizarGraduaAlLlegarAlDiaNoventa() {
        ParticipacionPrograma p = traineePausado();
        p.activarPrograma(CLOCK.today().plusDays(1), CLOCK);

        boolean cambio = p.sincronizarDiaDelPrograma(p.fechaInicio().plusDays(89), CLOCK);

        assertThat(cambio).isTrue();
        assertThat(p.diaPrograma()).isEqualTo(90);
        assertThat(p.programaCompletado()).isTrue();
        assertThat(p.programaCompletadoEn()).isEqualTo(CLOCK.now());
        assertThat(p.diaPostPrograma()).isZero();
    }

    /** El dia 89 no gradua a nadie, por bien que venga el aprendiz. */
    @Test
    void sincronizarNoGraduaAntesDelDiaNoventa() {
        ParticipacionPrograma p = traineePausado();
        p.activarPrograma(CLOCK.today().plusDays(1), CLOCK);

        p.sincronizarDiaDelPrograma(p.fechaInicio().plusDays(88), CLOCK);

        assertThat(p.diaPrograma()).isEqualTo(89);
        assertThat(p.programaCompletado()).isFalse();
        assertThat(p.programaCompletadoEn()).isNull();
    }

    /**
     * La graduacion es DERIVADA, no un evento que haya que cazar el dia exacto (regla 02 §2).
     * Cinco dias sin que el barrido corriera —el backend caido— no le cuestan la graduacion a
     * nadie: la primera corrida que ocurra la marca igual. Atada al instante "hoy se cumplen
     * 90", este participante no se graduaba nunca.
     */
    @Test
    void graduaAunqueNadieHayaCorridoElBarridoElDiaNoventa() {
        ParticipacionPrograma p = traineePausado();
        p.activarPrograma(CLOCK.today().plusDays(1), CLOCK);

        p.sincronizarDiaDelPrograma(p.fechaInicio().plusDays(94), CLOCK);

        assertThat(p.diaPrograma()).isEqualTo(90);
        assertThat(p.programaCompletado()).isTrue();
    }

    /** El barrido corre cada hora: la fecha de graduacion no se puede mover en cada corrida. */
    @Test
    void laGraduacionSeEscribeUnaSolaVezAunqueElBarridoSigaCorriendo() {
        ParticipacionPrograma p = traineePausado();
        p.activarPrograma(CLOCK.today().plusDays(1), CLOCK);
        LocalDate diaNoventa = p.fechaInicio().plusDays(89);
        p.sincronizarDiaDelPrograma(diaNoventa, CLOCK);
        Instant graduadoEn = p.programaCompletadoEn();
        FixedClock unaHoraDespues = FixedClock.at(CLOCK.now().plusSeconds(3600));

        boolean segundaCorridaElMismoDia = p.sincronizarDiaDelPrograma(diaNoventa, unaHoraDespues);

        assertThat(segundaCorridaElMismoDia).isFalse();
        assertThat(p.programaCompletadoEn()).isEqualTo(graduadoEn);

        p.sincronizarDiaDelPrograma(diaNoventa.plusDays(1), FixedClock.at(CLOCK.now().plusSeconds(86400)));

        assertThat(p.programaCompletadoEn()).isEqualTo(graduadoEn);
        assertThat(p.diaPrograma()).isEqualTo(90);
    }

    /**
     * Las filas que hay hoy en la base: dia 90 materializado y la bandera en falso, porque
     * nadie la escribia. La primera corrida del barrido tiene que graduarlas y devolver
     * {@code true} —si devolviera false, el barrido no guardaria la fila y se quedarian sin
     * graduar para siempre— aunque el dia no cambie y ya se hayan sincronizado hoy.
     */
    @Test
    void unaFilaQueYaEstabaEnElDiaNoventaSeGraduaEnLaCorridaSiguiente() {
        ParticipacionPrograma p = ParticipacionPrograma.rehydrate(UserId.of(UUID.randomUUID()), null, null, 90,
                FasePrograma.PHASE_4_ASCENSION, CLOCK.today().minusDays(89), CLOCK.now(), ZoneId.of("America/Lima"),
                false, 0, CLOCK.now(), CLOCK.now(), null, null, null, CLOCK.today());

        boolean cambio = p.sincronizarDiaDelPrograma(CLOCK.today(), CLOCK);

        assertThat(cambio).isTrue();
        assertThat(p.programaCompletado()).isTrue();
        assertThat(p.diaPrograma()).isEqualTo(90);
    }

    /**
     * Retroceder a un graduado NO le borra la graduacion: `programa_completado_en` es la fecha
     * en que algo paso, no un calculo. La regla confirmada dice cuando se gradua y no contempla
     * revertirlo — des-graduar seria inventar una regla de negocio.
     */
    @Test
    void retrocederAUnGraduadoNoLeBorraLaGraduacion() {
        ParticipacionPrograma p = traineePausado();
        p.activarPrograma(CLOCK.today().plusDays(1), CLOCK);
        LocalDate diaNoventa = p.fechaInicio().plusDays(89);
        p.sincronizarDiaDelPrograma(diaNoventa, CLOCK);
        assertThat(p.programaCompletado()).isTrue();

        p.fijarDia(84, relojEn(diaNoventa, p));
        p.sincronizarDiaDelPrograma(diaNoventa, relojEn(diaNoventa, p));

        assertThat(p.diaPrograma()).isEqualTo(84);
        assertThat(p.programaCompletado()).isTrue();
    }

    // --- fijarDia: retroceder y que el reloj SIGA desde ahi (V20) ---------

    /**
     * El caso que motivo V20: "viaje dos semanas, devolveme al dia 34 para no perder el
     * puntaje". Retroceder tiene que PERSISTIR -- con el modelo viejo, que escribia
     * `diaPrograma` a mano, la corrida siguiente lo devolvia de un salto al dia real.
     */
    @Test
    void fijarDiaRetrocedeYElRelojSigueDesdeAhiAlDiaSiguiente() {
        ParticipacionPrograma p = traineePausado();
        p.activarPrograma(CLOCK.today().plusDays(1), CLOCK);
        LocalDate diaCuarenta = p.fechaInicio().plusDays(39);
        p.sincronizarDiaDelPrograma(diaCuarenta, CLOCK);
        assertThat(p.diaPrograma()).isEqualTo(40);

        p.fijarDia(34, relojEn(diaCuarenta, p));
        assertThat(p.diaPrograma()).isEqualTo(34);

        p.sincronizarDiaDelPrograma(diaCuarenta.plusDays(1), CLOCK);

        assertThat(p.diaPrograma()).isEqualTo(35);
    }

    /** Retroceder corre la graduacion: los 90 dias siguen siendo 90 dias vividos. */
    @Test
    void fijarDiaHaciaAtrasCorreLaFechaDeGraduacion() {
        ParticipacionPrograma p = traineePausado();
        p.activarPrograma(CLOCK.today().plusDays(1), CLOCK);
        LocalDate inicio = p.fechaInicio();
        assertThat(p.fechaGraduacionEsperada()).isEqualTo(inicio.plusDays(90));

        p.fijarDia(34, relojEn(inicio.plusDays(39), p));

        assertThat(p.diasAjuste()).isEqualTo(6);
        assertThat(p.fechaGraduacionEsperada()).isEqualTo(inicio.plusDays(96));
    }

    // --- CARACTERIZACION (riesgos del ajuste de dia, 2026-09-26): preguntas abiertas al dueño ---

    /**
     * CARACTERIZACION, no regla confirmada. Con el programa activado pero el Dia 1 todavia en el
     * futuro, {@code fijarDia} escribe el dia y NO el ajuste (la cuenta derivada no puede
     * representar "dia 5" antes del dia 1). El endpoint responde 204 y la bitacora registra el
     * cambio, pero al llegar la fecha de inicio el barrido deriva 1 y el ajuste desaparece.
     */
    @Test
    void caracterizacionFijarDiaAntesDelDiaUnoNoSobreviveAlArranque() {
        ParticipacionPrograma p = traineePausado();
        p.activarPrograma(CLOCK.today().plusDays(1), CLOCK);

        p.fijarDia(5, CLOCK);
        assertThat(p.diaPrograma()).isEqualTo(5);
        assertThat(p.diasAjuste()).isZero();

        p.sincronizarDiaDelPrograma(p.fechaInicio(), relojEn(p.fechaInicio(), p));

        assertThat(p.diaPrograma()).isEqualTo(1);
    }

    /**
     * CARACTERIZACION. Fijar 90 no gradua en el acto: gradua la corrida siguiente del barrido
     * (minuto :05 de cada hora), y desde ahi ya no se des-gradua. Un 90 puesto por error y
     * corregido despues de las :05 deja al aprendiz graduado para siempre.
     */
    @Test
    void caracterizacionFijarNoventaGraduaRecienEnElBarridoYNoSeDeshace() {
        ParticipacionPrograma p = traineePausado();
        p.activarPrograma(CLOCK.today().plusDays(1), CLOCK);
        LocalDate diaVeinte = p.fechaInicio().plusDays(19);

        p.fijarDia(90, relojEn(diaVeinte, p));
        assertThat(p.programaCompletado()).isFalse();

        p.sincronizarDiaDelPrograma(diaVeinte, relojEn(diaVeinte, p));
        assertThat(p.programaCompletado()).isTrue();

        p.fijarDia(20, relojEn(diaVeinte, p));
        assertThat(p.diaPrograma()).isEqualTo(20);
        assertThat(p.programaCompletado()).isTrue();
    }

    /** E-319: el dia vigente es el derivado aunque la columna siga en el de ayer. */
    @Test
    void diaVigenteEsElDerivadoAunqueElBarridoNoHayaCorrido() {
        ParticipacionPrograma p = traineePausado();
        p.activarPrograma(CLOCK.today().plusDays(1), CLOCK);
        LocalDate diaDiez = p.fechaInicio().plusDays(9);
        p.sincronizarDiaDelPrograma(diaDiez, relojEn(diaDiez, p));

        // 00:30 del dia siguiente en Lima = 05:30 UTC: el barrido de ese dia no corrio todavia.
        FixedClock pasadaLaMedianoche = FixedClock.at(
                diaDiez.plusDays(1).atTime(0, 30).atZone(p.timezone()).toInstant());

        assertThat(p.diaPrograma()).isEqualTo(10);
        assertThat(p.diaVigente(pasadaLaMedianoche)).isEqualTo(11);
    }

    /** Antes del Dia 1 no hay cuenta que derivar: manda lo guardado (igual que la lectura). */
    @Test
    void diaVigenteAntesDelDiaUnoEsElGuardado() {
        ParticipacionPrograma p = traineePausado();
        p.activarPrograma(CLOCK.today().plusDays(1), CLOCK);

        assertThat(p.diaVigente(CLOCK)).isZero();
    }

    /** Reloj posicionado al mediodia de `dia` en la zona del participante. */
    private static FixedClock relojEn(LocalDate dia, ParticipacionPrograma p) {
        return FixedClock.at(dia.atTime(12, 0).atZone(p.timezone()).toInstant());
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
