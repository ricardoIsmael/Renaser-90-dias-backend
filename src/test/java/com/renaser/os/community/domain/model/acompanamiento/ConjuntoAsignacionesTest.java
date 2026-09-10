package com.renaser.os.community.domain.model.acompanamiento;

import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las invariantes temporales que el dominio debe sostener antes de tocar la base. La base
 * tiene las suyas (índices únicos parciales, T09) porque un check-then-insert no gana una
 * carrera; estas existen para que el error salga como regla de negocio y no como violación
 * de constraint traducida a 500.
 */
class ConjuntoAsignacionesTest {

    private static final Instant AGOSTO = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant SETIEMBRE = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant OCTUBRE = Instant.parse("2026-10-01T00:00:00Z");

    private static final CelulaId GRUPO = CelulaId.of(UUID.randomUUID());
    private static final CelulaId OTRO_GRUPO = CelulaId.of(UUID.randomUUID());

    private static AsignacionCelula asignacion(CelulaId celula, UserId usuario, FuncionAcompanamiento funcion,
                                                Instant desde, Instant hasta) {
        AsignacionCelula a = AsignacionCelula.abrir(AsignacionId.of(UUID.randomUUID()), celula, usuario, funcion,
                desde, MotivoAsignacion.ADMINISTRATIVO, null, UUID.randomUUID().toString());
        if (hasta != null) {
            a.cerrar(hasta, MotivoAsignacion.ROTACION);
        }
        return a;
    }

    @Test
    @DisplayName("dos mentores vigentes a la vez en el mismo grupo es imposible")
    void unSoloMentorVigentePorGrupo() {
        UserId mentorA = UserId.of(UUID.randomUUID());
        UserId mentorB = UserId.of(UUID.randomUUID());

        ConjuntoAsignaciones actual = ConjuntoAsignaciones.de(List.of(
                asignacion(GRUPO, mentorA, FuncionAcompanamiento.MENTOR, SETIEMBRE, null)));

        assertThatThrownBy(() -> actual.verificarPuedeAbrir(
                asignacion(GRUPO, mentorB, FuncionAcompanamiento.MENTOR, OCTUBRE, null)))
                .isInstanceOf(AsignacionInvalidaException.class)
                .hasMessageContaining("mentor");
    }

    @Test
    @DisplayName("el relevo si es valido: A cierra en el mismo instante en que B abre")
    void relevoLimpioEsValido() {
        UserId mentorA = UserId.of(UUID.randomUUID());
        UserId mentorB = UserId.of(UUID.randomUUID());

        ConjuntoAsignaciones actual = ConjuntoAsignaciones.de(List.of(
                asignacion(GRUPO, mentorA, FuncionAcompanamiento.MENTOR, SETIEMBRE, OCTUBRE)));

        actual.verificarPuedeAbrir(asignacion(GRUPO, mentorB, FuncionAcompanamiento.MENTOR, OCTUBRE, null));
    }

    @Test
    @DisplayName("varias guias y soportes a la vez son legitimos: no son exclusivos")
    void guiasYSoporteNoSonExclusivos() {
        ConjuntoAsignaciones actual = ConjuntoAsignaciones.de(List.of(
                asignacion(GRUPO, UserId.of(UUID.randomUUID()), FuncionAcompanamiento.GUIA, SETIEMBRE, null),
                asignacion(GRUPO, UserId.of(UUID.randomUUID()), FuncionAcompanamiento.SOPORTE, SETIEMBRE, null)));

        actual.verificarPuedeAbrir(
                asignacion(GRUPO, UserId.of(UUID.randomUUID()), FuncionAcompanamiento.GUIA, OCTUBRE, null));
    }

    @Test
    @DisplayName("un aprendiz no puede tener dos grupos regulares vigentes")
    void unSoloGrupoRegularPorAprendiz() {
        UserId aprendiz = UserId.of(UUID.randomUUID());

        ConjuntoAsignaciones actual = ConjuntoAsignaciones.de(List.of(
                asignacion(GRUPO, aprendiz, FuncionAcompanamiento.APRENDIZ, SETIEMBRE, null)));

        assertThatThrownBy(() -> actual.verificarPuedeAbrir(
                asignacion(OTRO_GRUPO, aprendiz, FuncionAcompanamiento.APRENDIZ, OCTUBRE, null)))
                .isInstanceOf(AsignacionInvalidaException.class);
    }

    @Test
    @DisplayName("repetir la misma clave de operacion no abre otro intervalo")
    void claveDeOperacionEsIdempotente() {
        UserId aprendiz = UserId.of(UUID.randomUUID());
        AsignacionCelula primera = AsignacionCelula.abrir(AsignacionId.of(UUID.randomUUID()), GRUPO, aprendiz,
                FuncionAcompanamiento.APRENDIZ, SETIEMBRE, MotivoAsignacion.TRASLADO, null, "traslado-2026-09");

        ConjuntoAsignaciones actual = ConjuntoAsignaciones.de(List.of(primera));

        assertThat(actual.yaAplicada("traslado-2026-09")).contains(primera);
        assertThat(actual.yaAplicada("traslado-2026-10")).isEmpty();
    }

    @Test
    @DisplayName("el mentor vigente y los aprendices vigentes se leen por instante, no por la ultima fila")
    void composicionVigenteEnUnInstante() {
        UserId mentorViejo = UserId.of(UUID.randomUUID());
        UserId mentorNuevo = UserId.of(UUID.randomUUID());
        UserId aprendiz = UserId.of(UUID.randomUUID());

        ConjuntoAsignaciones conjunto = ConjuntoAsignaciones.de(List.of(
                asignacion(GRUPO, mentorViejo, FuncionAcompanamiento.MENTOR, AGOSTO, OCTUBRE),
                asignacion(GRUPO, mentorNuevo, FuncionAcompanamiento.MENTOR, OCTUBRE, null),
                asignacion(GRUPO, aprendiz, FuncionAcompanamiento.APRENDIZ, AGOSTO, null)));

        assertThat(conjunto.mentorVigenteEn(GRUPO, SETIEMBRE)).contains(mentorViejo);
        assertThat(conjunto.mentorVigenteEn(GRUPO, OCTUBRE)).contains(mentorNuevo);
        assertThat(conjunto.aprendicesVigentesEn(GRUPO, OCTUBRE)).containsExactly(aprendiz);
    }

    @Test
    @DisplayName("sin mentor vigente el grupo existe igual: cobertura, no ausencia de grupo")
    void grupoSinMentorSigueSiendoGrupo() {
        UserId aprendiz = UserId.of(UUID.randomUUID());
        ConjuntoAsignaciones conjunto = ConjuntoAsignaciones.de(List.of(
                asignacion(GRUPO, aprendiz, FuncionAcompanamiento.APRENDIZ, AGOSTO, null),
                asignacion(GRUPO, UserId.of(UUID.randomUUID()), FuncionAcompanamiento.SOPORTE, AGOSTO, null)));

        assertThat(conjunto.mentorVigenteEn(GRUPO, OCTUBRE)).isEmpty();
        assertThat(conjunto.coberturaEn(GRUPO, OCTUBRE)).isEqualTo(CoberturaCelula.SOPORTE);
    }

    @Test
    @DisplayName("sin mentor y sin soporte la cobertura es un hueco explicito")
    void sinCoberturaEsVisible() {
        ConjuntoAsignaciones conjunto = ConjuntoAsignaciones.de(List.of(
                asignacion(GRUPO, UserId.of(UUID.randomUUID()), FuncionAcompanamiento.APRENDIZ, AGOSTO, null)));

        assertThat(conjunto.coberturaEn(GRUPO, OCTUBRE)).isEqualTo(CoberturaCelula.SIN_COBERTURA);
    }

    @Test
    @DisplayName("un mentor no puede liderar dos grupos regulares a la vez (celulas.mentor_id UNIQUE)")
    void unGrupoRegularPorMentor() {
        UserId mentor = UserId.of(UUID.randomUUID());

        ConjuntoAsignaciones actual = ConjuntoAsignaciones.de(List.of(
                asignacion(GRUPO, mentor, FuncionAcompanamiento.MENTOR, SETIEMBRE, null)));

        assertThatThrownBy(() -> actual.verificarPuedeAbrir(
                asignacion(OTRO_GRUPO, mentor, FuncionAcompanamiento.MENTOR, OCTUBRE, null)))
                .isInstanceOf(AsignacionInvalidaException.class);
    }
}
