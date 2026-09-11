package com.renaser.os.community.domain.model.acompanamiento;

import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsignacionCelulaTest {

    private static final Instant T0 = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-01T00:00:00Z");
    private static final CelulaId CELULA = CelulaId.of(UUID.randomUUID());
    private static final UserId USUARIO = UserId.of(UUID.randomUUID());

    private static AsignacionCelula abierta(FuncionAcompanamiento funcion, Instant desde) {
        return AsignacionCelula.abrir(AsignacionId.of(UUID.randomUUID()), CELULA, USUARIO, funcion,
                desde, MotivoAsignacion.ADMINISTRATIVO, null, "op-" + funcion + "-" + desde);
    }

    @Test
    @DisplayName("una asignacion nace vigente y sin fecha de cierre")
    void naceVigente() {
        AsignacionCelula asignacion = abierta(FuncionAcompanamiento.MENTOR, T0);

        assertThat(asignacion.vigente()).isTrue();
        assertThat(asignacion.periodo().fin()).isNull();
        assertThat(asignacion.vigenteEn(T1)).isTrue();
    }

    @Test
    @DisplayName("cerrar registra la hora real de ejecucion, no una fecha retroactiva inventada")
    void cerrarUsaHoraReal() {
        AsignacionCelula asignacion = abierta(FuncionAcompanamiento.MENTOR, T0);

        asignacion.cerrar(T1, MotivoAsignacion.ROTACION);

        assertThat(asignacion.vigente()).isFalse();
        assertThat(asignacion.periodo().fin()).isEqualTo(T1);
        assertThat(asignacion.vigenteEn(T1)).isFalse();
        assertThat(asignacion.vigenteEn(T1.minusMillis(1))).isTrue();
    }

    @Test
    @DisplayName("cerrar dos veces es un error de programa, no un cierre silencioso")
    void cerrarDosVecesFalla() {
        AsignacionCelula asignacion = abierta(FuncionAcompanamiento.MENTOR, T0);
        asignacion.cerrar(T1, MotivoAsignacion.ROTACION);

        assertThatThrownBy(() -> asignacion.cerrar(T1.plusSeconds(1), MotivoAsignacion.ROTACION))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("la clave de operacion es obligatoria: sin ella el comando no es idempotente")
    void claveDeOperacionObligatoria() {
        assertThatThrownBy(() -> AsignacionCelula.abrir(AsignacionId.of(UUID.randomUUID()), CELULA, USUARIO,
                FuncionAcompanamiento.APRENDIZ, T0, MotivoAsignacion.TRASLADO, null, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("un job no necesita un actor humano falso")
    void actorNuloEsJob() {
        AsignacionCelula asignacion = abierta(FuncionAcompanamiento.APRENDIZ, T0);

        assertThat(asignacion.actorId()).isNull();
        assertThat(asignacion.ejecutadaPorSistema()).isTrue();
    }
}
