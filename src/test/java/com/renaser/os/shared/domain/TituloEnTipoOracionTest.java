package com.renaser.os.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #63: la MISMA regla que {@code tituloEnTipoOracion} de la app (src/features/habits/utils/tituloEnTipoOracion.ts), con los mismos casos que su prueba:
 * un título que llega TODO en mayúsculas se lee en tipo oración; el que ya tiene minúsculas se deja; lo de entre paréntesis no cuenta; SER, IA y TV siguen siendo siglas.
 */
class TituloEnTipoOracionTest {

    // issue #63
    @ParameterizedTest(name = "«{0}» → «{1}»")
    @CsvSource(delimiter = '|', value = {
            "AGUA TIBIA CON LIMÓN|Agua tibia con limón",
            "JUGO VERDE|Jugo verde",
            "ÚLTIMA COMIDA DEL DÍA|Última comida del día",
            "DÍA SIN CELULAR|Día sin celular",
            "ESCRITURA LIBRE NOCTURNA|Escritura libre nocturna",
            "POST DIARIO EN COMUNIDAD|Post diario en comunidad",
            "AUDIOTERAPIA SEMANAL|Audioterapia semanal",
            "DESCANSO PROFUNDO|Descanso profundo",
            "KILÓMETROS DIARIOS|Kilómetros diarios"})
    @DisplayName("lo que llega en mayúsculas pasa a tipo oración")
    void lasMayusculasPasanATipoOracion(String entrada, String esperado) {
        assertThat(TituloEnTipoOracion.de(entrada)).isEqualTo(esperado);
    }

    // issue #63
    @Test
    @DisplayName("lo de entre paréntesis no cuenta para decidir: «RITUAL TIERRA - AGUA - FUEGO (mañana)» sigue siendo un título en mayúsculas")
    void loDeEntreParentesisNoCuenta() {
        assertThat(TituloEnTipoOracion.de("RITUAL TIERRA - AGUA - FUEGO (mañana)")).isEqualTo("Ritual tierra - agua - fuego (mañana)");
        assertThat(TituloEnTipoOracion.de("RITUAL TIERRA - AGUA - FUEGO (mediodía)")).isEqualTo("Ritual tierra - agua - fuego (mediodía)");
        assertThat(TituloEnTipoOracion.de("RITUAL TIERRA - AGUA - FUEGO (noche)")).isEqualTo("Ritual tierra - agua - fuego (noche)");
        assertThat(TituloEnTipoOracion.de("RITUAL TIERRA - AGUA - FUEGO")).isEqualTo("Ritual tierra - agua - fuego");
        assertThat(TituloEnTipoOracion.de("RITUAL DE MAÑANA (domingo)")).isEqualTo("Ritual de mañana (domingo)");
        assertThat(TituloEnTipoOracion.de("AGUA E HIDRATACIÓN (domingo)")).isEqualTo("Agua e hidratación (domingo)");
    }

    // issue #63
    @Test
    @DisplayName("un paréntesis que también viene en mayúsculas se pasa a minúsculas")
    void unParentesisEnMayusculasSePasaAMinusculas() {
        assertThat(TituloEnTipoOracion.de("PRIMERA COMIDA (ROMPO EL AYUNO)")).isEqualTo("Primera comida (rompo el ayuno)");
    }

    // issue #63
    @Test
    @DisplayName("SER, IA y TV siguen en mayúsculas; RENASER queda «Renaser» y RENACER «Renacer», también a mitad de frase")
    void siglasYNombresPropios() {
        assertThat(TituloEnTipoOracion.de("SER")).isEqualTo("SER");
        assertThat(TituloEnTipoOracion.de("HABLAR CON SER")).isEqualTo("Hablar con SER");
        assertThat(TituloEnTipoOracion.de("SER CONSTANTE")).isEqualTo("SER constante");
        assertThat(TituloEnTipoOracion.de("USAR LA IA SIN TV")).isEqualTo("Usar la IA sin TV");
        assertThat(TituloEnTipoOracion.de("RENASER")).isEqualTo("Renaser");
        assertThat(TituloEnTipoOracion.de("LEER CON RENASER HOY")).isEqualTo("Leer con Renaser hoy");
        assertThat(TituloEnTipoOracion.de("PASTILLA RENACER")).isEqualTo("Pastilla Renacer");
    }

    // issue #63
    @Test
    @DisplayName("conserva los espacios y los signos; la inicial es la primera letra")
    void conservaEspaciosYSignos() {
        assertThat(TituloEnTipoOracion.de("  1. LEER 10 MIN  ")).isEqualTo("  1. Leer 10 min  ");
        assertThat(TituloEnTipoOracion.de("¿QUÉ SENTISTE?")).isEqualTo("¿Qué sentiste?");
    }

    // issue #63
    @Test
    @DisplayName("es idempotente")
    void esIdempotente() {
        for (String titulo : new String[]{"AGUA TIBIA CON LIMÓN", "RITUAL TIERRA - AGUA - FUEGO (mañana)", "HABLAR CON SER"}) {
            assertThat(TituloEnTipoOracion.de(TituloEnTipoOracion.de(titulo))).isEqualTo(TituloEnTipoOracion.de(titulo));
        }
    }

    // issue #63
    @ParameterizedTest(name = "«{0}» ya tiene minúsculas")
    @ValueSource(strings = {"Clase diaria", "Pastilla Renacer", "Mi jugo verde", "Agua TIBIA con limón",
            "Declaración y desintoxicación digital de 24 horas", "agua tibia"})
    @DisplayName("el que ya tiene minúsculas (también el que la persona renombró) se deja tal cual")
    void loQueYaTieneMinusculasSeDeja(String titulo) {
        assertThat(TituloEnTipoOracion.de(titulo)).isEqualTo(titulo);
    }

    // issue #63
    @Test
    @DisplayName("sin letras, vacío o nulo no hay nada que cambiar; un título solo entre paréntesis se decide por lo de adentro")
    void casosBorde() {
        assertThat(TituloEnTipoOracion.de("")).isEmpty();
        assertThat(TituloEnTipoOracion.de("123 - 45")).isEqualTo("123 - 45");
        assertThat(TituloEnTipoOracion.de(null)).isNull();
        assertThat(TituloEnTipoOracion.de("(MAÑANA)")).isEqualTo("(Mañana)");
        assertThat(TituloEnTipoOracion.de("(mañana)")).isEqualTo("(mañana)");
    }
}
