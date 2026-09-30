package com.renaser.os.rocks.domain.model.rocamaestra;

import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El objetivo de 90 dias: como se define, que queda fijo (D-234) y que no acepta. */
class RocaMaestraTest {

    private static final RocaMaestraId ID = RocaMaestraId.of(UUID.randomUUID());
    private static final UserId PARTICIPANTE = UserId.of(UUID.randomUUID());
    private static final Instant AYER = Instant.parse("2026-09-06T10:00:00Z");
    private static final Instant HOY = Instant.parse("2026-09-07T10:00:00Z");

    private static RocaMaestra conMeta() {
        return RocaMaestra.definir(ID, PARTICIPANTE, EjeObjetivo.TRABAJO,
                OBJETIVO,
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

    // ---------------------------------------------------------------------------------------
    // D-234: una vez definida, la Roca Maestra queda fija; solo se registra el avance
    // ---------------------------------------------------------------------------------------

    private static final String OBJETIVO = "Facturar 30.000 USD en contratos high-ticket";

    private static RocaMaestra conBase() {
        return RocaMaestra.definir(ID, PARTICIPANTE, EjeObjetivo.CUERPO, "Pesar 75 kg",
                MetaCuantitativa.desde(new BigDecimal("82"), new BigDecimal("75"), "kg"), AYER);
    }

    @Test
    @DisplayName("D-234: la misma definicion se acepta sin tocar nada (la activacion del Mapa reintenta)")
    void laMismaDefinicionEsIdempotente() {
        RocaMaestra roca = conMeta();

        RocaMaestra recibida = roca.recibirDefinicion("  " + OBJETIVO + " ",
                new MetaCuantitativa(new BigDecimal("30000.00"), new BigDecimal("0.00"), "USD", null), HOY);

        assertThat(recibida).as("misma roca, ni siquiera cambia la fecha de actualizacion").isEqualTo(roca);
    }

    @Test
    @DisplayName("D-234: la misma definicion con punto de partida tambien es idempotente")
    void laMismaDefinicionConBaseEsIdempotente() {
        RocaMaestra roca = conBase();

        assertThat(roca.recibirDefinicion("Pesar 75 kg",
                MetaCuantitativa.desde(new BigDecimal("82.00"), new BigDecimal("75.00"), "kg"), HOY)).isEqualTo(roca);
    }

    @Test
    @DisplayName("D-234: un objetivo sin meta, mandado igual, se acepta")
    void objetivoCualitativoIgualSeAcepta() {
        RocaMaestra roca = RocaMaestra.definir(ID, PARTICIPANTE, EjeObjetivo.RELACIONES,
                "Recuperar la confianza con mi hijo", null, AYER);

        assertThat(roca.recibirDefinicion("Recuperar la confianza con mi hijo", null, HOY)).isEqualTo(roca);
    }

    @Test
    @DisplayName("D-234: otro avance con todo lo demas igual es registrar progreso, no cambiar la meta")
    void soloCambiaElAvance() {
        RocaMaestra recibida = conBase().recibirDefinicion("Pesar 75 kg",
                new MetaCuantitativa(new BigDecimal("75"), new BigDecimal("79"), "kg", new BigDecimal("82")), HOY);

        assertThat(recibida.id()).isEqualTo(ID);
        assertThat(recibida.creadoEn()).as("la fecha de creacion no se mueve").isEqualTo(AYER);
        assertThat(recibida.actualizadoEn()).isEqualTo(HOY);
        assertThat(recibida.meta().avance()).isEqualByComparingTo("79");
        assertThat(recibida.meta().lineaBase()).isEqualByComparingTo("82");
        assertThat(recibida.meta().porcentaje()).isEqualTo(42);
    }

    @Test
    @DisplayName("D-234: registrarAvance conserva objetivo, meta, unidad y punto de partida")
    void registrarAvanceConservaLoFijo() {
        RocaMaestra avanzada = conMeta().registrarAvance(new BigDecimal("19500"), HOY);

        assertThat(avanzada.objetivo()).isEqualTo(OBJETIVO);
        assertThat(avanzada.meta().objetivo()).isEqualByComparingTo("30000");
        assertThat(avanzada.meta().unidad()).isEqualTo("USD");
        assertThat(avanzada.meta().porcentaje()).isEqualTo(65);
    }

    @Test
    @DisplayName("D-234: un objetivo sin meta no tiene avance que registrar")
    void sinMetaNoHayAvance() {
        RocaMaestra roca = RocaMaestra.definir(ID, PARTICIPANTE, EjeObjetivo.RELACIONES, "Estar presente", null, AYER);

        assertThatThrownBy(() -> roca.registrarAvance(BigDecimal.ONE, HOY)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("D-234: cambiar la frase del objetivo se rechaza")
    void cambiarElObjetivoSeRechaza() {
        assertThatThrownBy(() -> conMeta().recibirDefinicion("Facturar 45.000 USD",
                MetaCuantitativa.nueva(new BigDecimal("30000"), "USD"), HOY))
                .isInstanceOf(RocaMaestraFijaException.class)
                .hasMessageContaining("quedó fijo en tu Mapa de Renacimiento")
                .hasMessageContaining("objetivos semanales y tus acciones diarias");
    }

    @Test
    @DisplayName("D-234: cambiar la meta se rechaza")
    void cambiarLaMetaSeRechaza() {
        assertThatThrownBy(() -> conMeta().recibirDefinicion(OBJETIVO,
                MetaCuantitativa.nueva(new BigDecimal("45000"), "USD"), HOY))
                .isInstanceOf(RocaMaestraFijaException.class);
    }

    @Test
    @DisplayName("D-234: cambiar la unidad se rechaza")
    void cambiarLaUnidadSeRechaza() {
        assertThatThrownBy(() -> conMeta().recibirDefinicion(OBJETIVO,
                MetaCuantitativa.nueva(new BigDecimal("30000"), "PEN"), HOY))
                .isInstanceOf(RocaMaestraFijaException.class);
    }

    @Test
    @DisplayName("D-234: cambiar el punto de partida se rechaza, tambien ponerle uno a una roca vieja sin el")
    void cambiarLaLineaBaseSeRechaza() {
        assertThatThrownBy(() -> conBase().recibirDefinicion("Pesar 75 kg",
                MetaCuantitativa.desde(new BigDecimal("85"), new BigDecimal("75"), "kg"), HOY))
                .isInstanceOf(RocaMaestraFijaException.class);
        assertThatThrownBy(() -> conMeta().recibirDefinicion(OBJETIVO,
                new MetaCuantitativa(new BigDecimal("30000"), BigDecimal.ZERO, "USD", BigDecimal.ZERO), HOY))
                .isInstanceOf(RocaMaestraFijaException.class);
    }

    @Test
    @DisplayName("D-234: sacarle la meta o ponersela a un objetivo que no la tenia se rechaza")
    void agregarOQuitarLaMetaSeRechaza() {
        assertThatThrownBy(() -> conMeta().recibirDefinicion(OBJETIVO, null, HOY))
                .isInstanceOf(RocaMaestraFijaException.class);
        RocaMaestra sinMeta = RocaMaestra.definir(ID, PARTICIPANTE, EjeObjetivo.TRABAJO, OBJETIVO, null, AYER);
        assertThatThrownBy(() -> sinMeta.recibirDefinicion(OBJETIVO,
                MetaCuantitativa.nueva(new BigDecimal("30000"), "USD"), HOY))
                .isInstanceOf(RocaMaestraFijaException.class);
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
