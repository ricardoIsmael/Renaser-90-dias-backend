package com.renaser.os.habits.domain.model.habito;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.ObjetoDeOtraFormacionException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec 023: un habito del catalogo es de UNA formacion; uno propio no es de ninguna. */
class HabitoPorFormacionTest {

    private static final Instant AHORA = Instant.parse("2026-10-31T15:00:00Z");
    private static final DetallesHabito DETALLES = new DetallesHabito("desc", "CUERPO", ExigenciaEvidencia.OBLIGATORIA, true, true);

    private static Habito delDiplomado() {
        return Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), Formacion.DIPLOMADO, "Estudio del dia", TipoHabito.CHECKBOX,
                new DetallesHabito("desc", "DIPLOMADO_ESTUDIO", ExigenciaEvidencia.OBLIGATORIA, true, true), AHORA);
    }

    @Test
    @DisplayName("sin decir formacion, un habito del catalogo es de la Escuela (el cliente anterior)")
    void porDefectoEscuela() {
        Habito habito = Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Agua", TipoHabito.CHECKBOX, DETALLES, AHORA);

        assertThat(habito.formacion()).isEqualTo(Formacion.ESCUELA);
        assertThat(habito.esDeFormacion(Formacion.ESCUELA)).isTrue();
        assertThat(habito.esDeFormacion(Formacion.DIPLOMADO)).isFalse();
    }

    @Test
    @DisplayName("un habito propio no es de ninguna formacion")
    void personalSinFormacion() {
        Habito propio = Habito.crearPersonal(HabitoId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()), "Mi hábito",
                TipoHabito.CHECKBOX, "CUERPO", null, null, AHORA);

        assertThat(propio.formacion()).isNull();
        assertThat(propio.esDeFormacion(Formacion.ESCUELA)).isFalse();
    }

    @Test
    @DisplayName("pedir un habito del catalogo de otra formacion se responde como si no existiera")
    void otraFormacionEs404() {
        Habito habito = delDiplomado();

        habito.exigirQueSeaVisibleEn(Formacion.DIPLOMADO);
        assertThatThrownBy(() -> habito.exigirQueSeaVisibleEn(Formacion.ESCUELA))
                .isInstanceOf(ObjetoDeOtraFormacionException.class)
                .hasMessage(ObjetoDeOtraFormacionException.MENSAJE);
        assertThatThrownBy(() -> habito.exigirQueSeaVisibleEn(Formacion.CERTIFICACION))
                .isInstanceOf(ObjetoDeOtraFormacionException.class);
    }

    @Test
    @DisplayName("un habito propio no se filtra por formacion: de quien es lo decide su dueño")
    void personalNoSeFiltra() {
        Habito propio = Habito.crearPersonal(HabitoId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()), "Mi hábito",
                TipoHabito.CHECKBOX, "CUERPO", null, null, AHORA);

        propio.exigirQueSeaVisibleEn(Formacion.CERTIFICACION);
    }

    @Test
    @DisplayName("copiar a otra formacion: mismo contenido, otra identidad, otra categoria, ya independiente")
    void copiar() {
        Habito original = delDiplomado();
        HabitoId idNuevo = HabitoId.of(UUID.randomUUID());

        Habito copia = original.copiarA(idNuevo, Formacion.CERTIFICACION, "CERTIFICACION_PRACTICA", AHORA.plusSeconds(10));

        assertThat(copia.id()).isEqualTo(idNuevo).isNotEqualTo(original.id());
        assertThat(copia.formacion()).isEqualTo(Formacion.CERTIFICACION);
        assertThat(copia.categoriaClave()).isEqualTo("CERTIFICACION_PRACTICA");
        assertThat(copia.titulo()).isEqualTo(original.titulo());
        assertThat(copia.descripcion()).isEqualTo(original.descripcion());
        assertThat(copia.tipo()).isEqualTo(original.tipo());
        assertThat(copia.exigenciaEvidencia()).isEqualTo(original.exigenciaEvidencia());
        assertThat(copia.esOpcional()).isEqualTo(original.esOpcional());
        assertThat(copia.obligatorioEnIntoxicacion()).isEqualTo(original.obligatorioEnIntoxicacion());
        assertThat(copia.activo()).isTrue();
        assertThat(copia.creadoEn()).isEqualTo(AHORA.plusSeconds(10));

        // Independiente: tocar uno no toca al otro.
        copia.renombrar("Otro nombre", AHORA.plusSeconds(20));
        copia.desactivar(AHORA.plusSeconds(20));
        assertThat(original.titulo()).isEqualTo("Estudio del dia");
        assertThat(original.activo()).isTrue();
        assertThat(original.formacion()).isEqualTo(Formacion.DIPLOMADO);
    }

    @Test
    @DisplayName("la copia de un habito inactivo nace inactiva")
    void copiaDeInactivo() {
        Habito original = delDiplomado();
        original.desactivar(AHORA);

        assertThat(original.copiarA(HabitoId.of(UUID.randomUUID()), Formacion.ESCUELA, "CUERPO", AHORA).activo()).isFalse();
    }

    @Test
    @DisplayName("un habito propio no se copia a otra formacion")
    void personalNoSeCopia() {
        Habito propio = Habito.crearPersonal(HabitoId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()), "Mi hábito",
                TipoHabito.CHECKBOX, "CUERPO", null, null, AHORA);

        assertThatThrownBy(() -> propio.copiarA(HabitoId.of(UUID.randomUUID()), Formacion.ESCUELA, "CUERPO", AHORA))
                .isInstanceOf(IllegalStateException.class);
    }
}
