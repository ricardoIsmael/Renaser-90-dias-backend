package com.renaser.os.habits.domain.model.habito;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HabitoTest {

    private static final Instant AHORA = Instant.parse("2026-08-26T10:00:00Z");

    private static DetallesHabito detalles() {
        return new DetallesHabito("Descripcion", "CUERPO", ExigenciaEvidencia.OPCIONAL, false, false);
    }

    @Test
    void crearDeSistemaConDetallesFijaTodosLosCamposDelAlta() {
        Habito habito = Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Agua tibia con limon",
                TipoHabito.CHECKBOX, new DetallesHabito("Toma agua", "CUERPO", ExigenciaEvidencia.OBLIGATORIA, true,
                true), AHORA);

        assertThat(habito.titulo()).isEqualTo("Agua tibia con limon");
        assertThat(habito.descripcion()).isEqualTo("Toma agua");
        assertThat(habito.categoriaClave()).isEqualTo("CUERPO");
        assertThat(habito.exigenciaEvidencia()).isEqualTo(ExigenciaEvidencia.OBLIGATORIA);
        assertThat(habito.esOpcional()).isTrue();
        assertThat(habito.obligatorioEnIntoxicacion()).isTrue();
        assertThat(habito.claveSistema()).isNull();
        assertThat(habito.esDeSistema()).isTrue();
        assertThat(habito.activo()).isTrue();
    }

    @Test
    void categoriaSeNormalizaAMayusculasYRecortada() {
        Habito habito = Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Titulo", TipoHabito.CHECKBOX,
                new DetallesHabito(null, "  cuerpo  ", ExigenciaEvidencia.OPCIONAL, false, false), AHORA);

        assertThat(habito.categoriaClave()).isEqualTo("CUERPO");
    }

    @Test
    void detallesConCategoriaVaciaEsRechazado() {
        assertThatThrownBy(() -> new DetallesHabito(null, "  ", ExigenciaEvidencia.OPCIONAL, false, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detallesConExigenciaNulaEsRechazado() {
        assertThatThrownBy(() -> new DetallesHabito(null, "CUERPO", null, false, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void activarReactivaUnHabitoDadoDeBaja() {
        Habito habito = Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Titulo", TipoHabito.CHECKBOX, detalles(),
                AHORA);
        habito.desactivar(AHORA);
        assertThat(habito.activo()).isFalse();

        Instant despues = AHORA.plusSeconds(60);
        habito.activar(despues);

        assertThat(habito.activo()).isTrue();
        assertThat(habito.actualizadoEn()).isEqualTo(despues);
    }

    @Test
    void actualizarDetallesReemplazaDescripcionCategoriaExigenciaYBanderas() {
        Habito habito = Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Titulo", TipoHabito.CHECKBOX, detalles(),
                AHORA);
        Instant despues = AHORA.plusSeconds(60);

        habito.actualizarDetalles(new DetallesHabito("Nueva descripcion", "MENTE", ExigenciaEvidencia.OBLIGATORIA,
                true, true), despues);

        assertThat(habito.descripcion()).isEqualTo("Nueva descripcion");
        assertThat(habito.categoriaClave()).isEqualTo("MENTE");
        assertThat(habito.exigenciaEvidencia()).isEqualTo(ExigenciaEvidencia.OBLIGATORIA);
        assertThat(habito.esOpcional()).isTrue();
        assertThat(habito.obligatorioEnIntoxicacion()).isTrue();
        assertThat(habito.actualizadoEn()).isEqualTo(despues);
    }

    /**
     * Invariante protegido explicito (CLAUDE.MD, encargo de esta tarea): {@code
     * actualizarDetalles} no expone ninguna forma de tocar {@code tipo} ni
     * {@code claveSistema} — no existe ningun metodo de instancia que los mute fuera del
     * constructor privado. Este test documenta la garantia por reflexion: si alguien agrega
     * un setter para cualquiera de los dos, este test lo detecta.
     */
    @Test
    void tipoYClaveSistemaNoTienenNingunMetodoMutadorPublico() {
        Habito habito = Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Titulo", TipoHabito.CHECKBOX, detalles(),
                AHORA);
        TipoHabito tipoOriginal = habito.tipo();
        String claveOriginal = habito.claveSistema();

        habito.actualizarDetalles(new DetallesHabito("otra", "ESPIRITU", ExigenciaEvidencia.OBLIGATORIA, true, true),
                AHORA.plusSeconds(1));
        habito.renombrar("Otro titulo", AHORA.plusSeconds(2));
        habito.desactivar(AHORA.plusSeconds(3));
        habito.activar(AHORA.plusSeconds(4));

        assertThat(habito.tipo()).isEqualTo(tipoOriginal);
        assertThat(habito.claveSistema()).isEqualTo(claveOriginal);
    }
}
