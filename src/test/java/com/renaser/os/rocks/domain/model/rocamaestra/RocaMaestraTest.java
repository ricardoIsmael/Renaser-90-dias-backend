package com.renaser.os.rocks.domain.model.rocamaestra;

import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El objetivo de 90 dias: como se define, como se corrige y que no acepta. */
class RocaMaestraTest {

    private static final RocaMaestraId ID = RocaMaestraId.of(UUID.randomUUID());
    private static final UserId PARTICIPANTE = UserId.of(UUID.randomUUID());
    private static final Instant AYER = Instant.parse("2026-09-06T10:00:00Z");
    private static final Instant HOY = Instant.parse("2026-09-07T10:00:00Z");

    private static RocaMaestra conMeta() {
        return RocaMaestra.definir(ID, PARTICIPANTE, EjeObjetivo.TRABAJO,
                "Facturar 30.000 USD en contratos high-ticket",
                MetaCuantitativa.nueva(new BigDecimal("30000"), "USD"), AYER);
    }

    @Test
    @DisplayName("recien definida, creacion y actualizacion son el mismo instante")
    void alDefinirseLasDosFechasCoinciden() {
        RocaMaestra roca = conMeta();

        assertThat(roca.creadoEn()).isEqualTo(AYER);
        assertThat(roca.actualizadoEn()).isEqualTo(AYER);
        assertThat(roca.tieneMeta()).isTrue();
    }

    @Test
    @DisplayName("un objetivo puramente cualitativo es valido: no todo se cuenta")
    void aceptaObjetivoSinMeta() {
        RocaMaestra roca = RocaMaestra.definir(ID, PARTICIPANTE, EjeObjetivo.RELACIONES,
                "Recuperar la confianza con mi hijo", null, AYER);

        assertThat(roca.tieneMeta()).isFalse();
        assertThat(roca.meta()).isNull();
    }

    @Test
    @DisplayName("corregirla conserva identidad y fecha de creacion: es la misma meta, no una nueva")
    void redefinirConservaIdentidad() {
        RocaMaestra corregida = conMeta().redefinir("Facturar 45.000 USD",
                new MetaCuantitativa(new BigDecimal("45000"), new BigDecimal("19500"), "USD"), HOY);

        assertThat(corregida.id()).isEqualTo(ID);
        assertThat(corregida.creadoEn()).as("la fecha de creacion no se mueve").isEqualTo(AYER);
        assertThat(corregida.actualizadoEn()).isEqualTo(HOY);
        assertThat(corregida.objetivo()).isEqualTo("Facturar 45.000 USD");
        assertThat(corregida.meta().porcentaje()).isEqualTo(43);
    }

    @Test
    @DisplayName("se le puede sacar la parte medible a un objetivo que la tenia")
    void redefinirPuedeQuitarLaMeta() {
        assertThat(conMeta().redefinir("Ya no lo mido con un numero", null, HOY).tieneMeta()).isFalse();
    }

    @Test
    @DisplayName("un objetivo vacio no dice nada")
    void rechazaObjetivoVacio() {
        assertThatThrownBy(() -> RocaMaestra.definir(ID, PARTICIPANTE, EjeObjetivo.CUERPO, "  ", null, AYER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("el objetivo se acota: la columna es text y sin tope entra cualquier cosa")
    void rechazaObjetivoDemasiadoLargo() {
        String largo = "x".repeat(RocaMaestra.MAX_OBJETIVO + 1);

        assertThatThrownBy(() -> RocaMaestra.definir(ID, PARTICIPANTE, EjeObjetivo.CUERPO, largo, null, AYER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("el objetivo se guarda sin espacios de sobra")
    void recortaElObjetivo() {
        assertThat(RocaMaestra.definir(ID, PARTICIPANTE, EjeObjetivo.CUERPO, "  Bajar a 68 kg  ", null, AYER)
                .objetivo()).isEqualTo("Bajar a 68 kg");
    }
}
