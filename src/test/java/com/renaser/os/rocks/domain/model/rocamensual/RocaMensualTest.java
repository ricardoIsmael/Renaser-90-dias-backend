package com.renaser.os.rocks.domain.model.rocamensual;

import com.renaser.os.rocks.domain.model.rocamaestra.MetaCuantitativa;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El tramo mensual de un objetivo de 90 dias: como se define, como se corrige y que no acepta. */
class RocaMensualTest {

    private static final RocaMensualId ID = RocaMensualId.of(UUID.randomUUID());
    private static final RocaMaestraId MAESTRA = RocaMaestraId.of(UUID.randomUUID());
    private static final Instant AYER = Instant.parse("2026-09-06T10:00:00Z");
    private static final Instant HOY = Instant.parse("2026-09-07T10:00:00Z");

    private static RocaMensual conMeta() {
        return RocaMensual.definir(ID, MAESTRA, 2, "Facturar 10.000 USD este mes",
                MetaCuantitativa.nueva(new BigDecimal("10000"), "USD"), AYER);
    }

    @Test
    @DisplayName("recien definido, creacion y actualizacion son el mismo instante")
    void alDefinirseLasDosFechasCoinciden() {
        RocaMensual mensual = conMeta();

        assertThat(mensual.creadoEn()).isEqualTo(AYER);
        assertThat(mensual.actualizadoEn()).isEqualTo(AYER);
        assertThat(mensual.tieneMeta()).isTrue();
    }

    @Test
    @DisplayName("un tramo puramente cualitativo es valido: no todo se cuenta")
    void aceptaTramoSinMeta() {
        RocaMensual mensual = RocaMensual.definir(ID, MAESTRA, 1, "Volver a entrenar tres veces por semana",
                null, AYER);

        assertThat(mensual.tieneMeta()).isFalse();
        assertThat(mensual.meta()).isNull();
    }

    @Test
    @DisplayName("el mes tiene que caer dentro de los tres del programa")
    void rechazaUnMesFueraDeRango() {
        assertThatThrownBy(() -> RocaMensual.definir(ID, MAESTRA, 0, "titulo", null, AYER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RocaMensual.definir(ID, MAESTRA, 4, "titulo", null, AYER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("un titulo vacio o en blanco no define nada")
    void rechazaTituloVacio() {
        assertThatThrownBy(() -> RocaMensual.definir(ID, MAESTRA, 1, "", null, AYER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RocaMensual.definir(ID, MAESTRA, 1, "   ", null, AYER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** La columna es {@code text} y no acota: si no lo hace el agregado, un cliente manda megabytes. */
    @Test
    @DisplayName("un titulo de mas de 500 caracteres se rechaza; 500 justos se aceptan")
    void rechazaTituloDemasiadoLargo() {
        assertThatThrownBy(() -> RocaMensual.definir(ID, MAESTRA, 1, "a".repeat(RocaMensual.MAX_TITULO + 1),
                null, AYER))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(RocaMensual.definir(ID, MAESTRA, 1, "a".repeat(RocaMensual.MAX_TITULO), null, AYER).titulo())
                .hasSize(RocaMensual.MAX_TITULO);
    }

    @Test
    @DisplayName("corregirlo conserva identidad, maestra, mes y fecha de creacion: es el mismo tramo")
    void redefinirConservaIdentidad() {
        RocaMensual corregido = conMeta().redefinir("Facturar 12.000 USD este mes",
                new MetaCuantitativa(new BigDecimal("12000"), new BigDecimal("6000"), "USD"), HOY);

        assertThat(corregido.id()).isEqualTo(ID);
        assertThat(corregido.rocaMaestraId()).as("sigue colgando del mismo objetivo de 90 dias").isEqualTo(MAESTRA);
        assertThat(corregido.numeroMes()).as("corregir el tramo no lo mueve de mes").isEqualTo(2);
        assertThat(corregido.creadoEn()).as("la fecha de creacion no se mueve").isEqualTo(AYER);
        assertThat(corregido.actualizadoEn()).isEqualTo(HOY);
        assertThat(corregido.titulo()).isEqualTo("Facturar 12.000 USD este mes");
        assertThat(corregido.meta().porcentaje()).isEqualTo(50);
    }

    @Test
    @DisplayName("el dia de cierre lo decide el mes, no quien consuma el dato")
    void diaDeCierreSaleDelMes() {
        assertThat(RocaMensual.definir(ID, MAESTRA, 1, "t", null, AYER).diaDeCierre()).isEqualTo(30);
        assertThat(conMeta().diaDeCierre()).isEqualTo(60);
        assertThat(RocaMensual.definir(ID, MAESTRA, 3, "t", null, AYER).diaDeCierre()).isEqualTo(90);
    }
}
