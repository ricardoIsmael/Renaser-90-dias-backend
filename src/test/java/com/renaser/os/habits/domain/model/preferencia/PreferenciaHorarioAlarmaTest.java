package com.renaser.os.habits.domain.model.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 009 (V112, D-283): la combinacion de alarma GENERAL del habito vive en la preferencia. */
class PreferenciaHorarioAlarmaTest {

    private static final Instant AHORA = Instant.parse("2026-10-20T10:00:00Z");
    private static final Instant DESPUES = Instant.parse("2026-10-21T10:00:00Z");
    private final UserId actor = UserId.of(UUID.randomUUID());
    private final HabitoId habito = HabitoId.of(UUID.randomUUID());

    @Test
    void unaPreferenciaNuevaNoDiceNadaDeLaAlarma() {
        var preferencia = PreferenciaHorario.crear(actor, habito, LocalTime.of(7, 0), null, AHORA);

        assertThat(preferencia.alarma()).isNull();
    }

    @Test
    void actualizarLaAlarmaLaGuardaYNoTocaLoDemas() {
        var preferencia = PreferenciaHorario.crear(actor, habito, LocalTime.of(7, 0), LocalTime.of(9, 0), AHORA);
        preferencia.actualizarRecordatorioConAntelaciones(true, List.of(30, 10), AHORA);
        var vibrar = AjusteDeAlarma.de(CombinacionDeAlarma.de(false, true, false, true));

        preferencia.actualizarAlarma(vibrar, DESPUES);

        assertThat(preferencia.alarma()).isEqualTo(vibrar);
        assertThat(preferencia.actualizadoEn()).isEqualTo(DESPUES);
        assertThat(preferencia.horaDisparo()).isEqualTo(LocalTime.of(7, 0));
        assertThat(preferencia.antelacionesRecordatorio()).containsExactly(30, 10);
        assertThat(preferencia.recordatorioActivo()).isTrue();
    }

    @Test
    void actualizarConNuloOVacioVuelveALoDeHoy() {
        var preferencia = PreferenciaHorario.crear(actor, habito, LocalTime.of(7, 0), null, AHORA);
        preferencia.actualizarAlarma(AjusteDeAlarma.de(CombinacionDeAlarma.de(false, true, false, false)), AHORA);

        preferencia.actualizarAlarma(null, DESPUES);
        assertThat(preferencia.alarma()).isNull();

        preferencia.actualizarAlarma(AjusteDeAlarma.de(CombinacionDeAlarma.de(true, true, true, true)), AHORA);
        preferencia.actualizarAlarma(AjusteDeAlarma.hereda(), DESPUES);
        assertThat(preferencia.alarma()).as("un ajuste vacio es lo mismo que no tener ninguno").isNull();
    }

    @Test
    void rehydrateConservaLaAlarma() {
        var vibrar = AjusteDeAlarma.de(CombinacionDeAlarma.de(false, true, false, false));

        var preferencia = PreferenciaHorario.rehydrate(actor, habito, LocalTime.of(7, 0), null, true, 10,
                List.of(10), vibrar, AHORA, AHORA);

        assertThat(preferencia.alarma()).isEqualTo(vibrar);
        assertThat(PreferenciaHorario.rehydrate(actor, habito, LocalTime.of(7, 0), null, true, 10, List.of(10),
                AHORA, AHORA).alarma()).as("las firmas anteriores no la traen").isNull();
    }

    /** Una fila creada solo para guardar como suena no eligio hora ni aviso: no es un horario personalizado. */
    @Test
    void esSoloDeAlarmaCuandoNoTieneHoraNiAviso() {
        var vibrar = AjusteDeAlarma.de(CombinacionDeAlarma.de(false, true, false, false));
        var soloAlarma = PreferenciaHorario.crear(actor, habito, null, null, AHORA);
        soloAlarma.actualizarAlarma(vibrar, AHORA);

        assertThat(soloAlarma.esSoloDeAlarma()).isTrue();

        var conHora = PreferenciaHorario.crear(actor, habito, LocalTime.of(7, 0), null, AHORA);
        conHora.actualizarAlarma(vibrar, AHORA);
        assertThat(conHora.esSoloDeAlarma()).isFalse();

        var conAviso = PreferenciaHorario.crear(actor, habito, null, null, AHORA);
        conAviso.actualizarRecordatorioConAntelaciones(true, List.of(10), AHORA);
        conAviso.actualizarAlarma(vibrar, AHORA);
        assertThat(conAviso.esSoloDeAlarma()).isFalse();

        var sinAviso = PreferenciaHorario.crear(actor, habito, null, null, AHORA);
        sinAviso.actualizarRecordatorio(false, null, AHORA);
        sinAviso.actualizarAlarma(vibrar, AHORA);
        assertThat(sinAviso.esSoloDeAlarma()).as("apagar el aviso es una eleccion de la persona").isFalse();

        var sinAlarma = PreferenciaHorario.crear(actor, habito, null, null, AHORA);
        assertThat(sinAlarma.esSoloDeAlarma()).as("las filas de antes no cambian de significado").isFalse();
    }
}
