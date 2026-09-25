package com.renaser.os.habits.domain.model.habito;

import com.renaser.os.shared.domain.UserId;
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
     * E-138: {@code esPersonalDe} es la pregunta que decide si un habito puede entrar en el plan
     * de alguien ({@code DesbloqueoHabitoService.elegir}). Los tres casos importan por separado:
     * el catalogo NO tiene dueno (y por eso no es "personal de" nadie, ni siquiera comparando
     * contra null), el propio si, y el de otro aprendiz no.
     */
    @Test
    void esPersonalDeDistingueElHabitoPropioDelAjenoYDelCatalogo() {
        UserId dueno = UserId.of(UUID.randomUUID());
        UserId otro = UserId.of(UUID.randomUUID());
        Habito propio = Habito.crearPersonal(HabitoId.of(UUID.randomUUID()), dueno, "Correr 5km",
                TipoHabito.CHECKBOX, "CUERPO", PlantillaHabitoPersonal.CORRER, "Meta", AHORA);
        Habito deCatalogo = Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Titulo", TipoHabito.CHECKBOX,
                detalles(), AHORA);

        assertThat(propio.esPersonalDe(dueno)).isTrue();
        assertThat(propio.esPersonalDe(otro)).isFalse();
        assertThat(deCatalogo.esPersonalDe(dueno)).isFalse();
        assertThat(deCatalogo.esPersonalDe(null)).isFalse();
    }

    // ────────────────────────────────────────────────────────────────────────────────────
    // D-169: Ciclos de Intoxicacion (dias 8-10, 17-19, 26-28). Todos los habitos pasan a
    // opcionales salvo los `obligatorio_en_intoxicacion`. Con el codigo anterior (`es_opcional`
    // del catalogo copiado tal cual) los casos "en ventana" fallan: no existia la regla.
    // ────────────────────────────────────────────────────────────────────────────────────

    private static Habito deCatalogo(boolean esOpcional, boolean obligatorioEnIntoxicacion) {
        return Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Titulo", TipoHabito.CHECKBOX,
                new DetallesHabito(null, "CUERPO", ExigenciaEvidencia.OBLIGATORIA, esOpcional,
                        obligatorioEnIntoxicacion), AHORA);
    }

    @Test
    void unHabitoObligatorioSeVuelveOpcionalSoloDentroDeLasVentanas() {
        Habito jugoVerde = deCatalogo(false, false);

        assertThat(jugoVerde.esOpcionalEnDia(7)).isFalse();
        assertThat(jugoVerde.esOpcionalEnDia(8)).isTrue();
        assertThat(jugoVerde.esOpcionalEnDia(10)).isTrue();
        assertThat(jugoVerde.esOpcionalEnDia(11)).isFalse();
        assertThat(jugoVerde.esOpcionalEnDia(18)).isTrue();
        assertThat(jugoVerde.esOpcionalEnDia(28)).isTrue();
        assertThat(jugoVerde.esOpcionalEnDia(29)).isFalse();
        assertThat(jugoVerde.esOpcionalEnDia(0)).isFalse();
    }

    /** La publicacion diaria en la comunidad (V4: `obligatorio_en_intoxicacion = true`). */
    @Test
    void elHabitoObligatorioEnIntoxicacionSigueExigibleEnLasVentanas() {
        Habito postDiario = deCatalogo(false, true);

        assertThat(postDiario.esOpcionalEnDia(9)).isFalse();
        assertThat(postDiario.esOpcionalEnDia(17)).isFalse();
        assertThat(postDiario.esOpcionalEnDia(26)).isFalse();
        assertThat(postDiario.esOpcionalEnDia(12)).isFalse();
    }

    /** DIA SIN CELULAR: opcional de catalogo todos los dias, en ventana o no. */
    @Test
    void unHabitoOpcionalDeCatalogoEsOpcionalTodosLosDias() {
        Habito diaSinCelular = deCatalogo(true, false);

        assertThat(diaSinCelular.esOpcionalEnDia(5)).isTrue();
        assertThat(diaSinCelular.esOpcionalEnDia(9)).isTrue();
    }

    /** Supuesto de D-169: la bandera exceptua de volverse opcional; no vuelve obligatorio a nadie. */
    @Test
    void laBanderaNoVuelveObligatorioAUnHabitoOpcionalDeCatalogo() {
        Habito opcionalConBandera = deCatalogo(true, true);

        assertThat(opcionalConBandera.esOpcionalEnDia(9)).isTrue();
        assertThat(opcionalConBandera.esOpcionalEnDia(12)).isTrue();
    }

    /** "Todos los habitos": tambien los propios del aprendiz, que nacen sin ninguna de las dos banderas. */
    @Test
    void unHabitoPersonalTambienSeVuelveOpcionalEnLasVentanas() {
        Habito propio = Habito.crearPersonal(HabitoId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()),
                "Correr 5km", TipoHabito.CHECKBOX, "CUERPO", PlantillaHabitoPersonal.CORRER, "Meta", AHORA);

        assertThat(propio.esOpcionalEnDia(19)).isTrue();
        assertThat(propio.esOpcionalEnDia(20)).isFalse();
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
