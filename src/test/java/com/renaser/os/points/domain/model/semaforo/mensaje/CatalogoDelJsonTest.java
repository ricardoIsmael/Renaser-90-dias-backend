package com.renaser.os.points.domain.model.semaforo.mensaje;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El catálogo de mensajes del semáforo (D-277, spec 006) tal como lo entregó el dueño
 * ({@code specs/006-.../data/mensajes-semaforo.json}) y como lo siembra {@code V110}. Dos cosas se prueban: que el
 * texto cumple lo que se prometió (tuteo, largo, variables conocidas, stickers y colores coherentes) y que la
 * migración no se desvió del JSON.
 */
class CatalogoDelJsonTest {

    private static final Path JSON = Path.of("specs/006-mensaje-semaforo-y-podio/data/mensajes-semaforo.json");
    private static final Path MIGRACION = Path.of("src/main/resources/db/migration/V110__mensajes_del_semaforo.sql");

    /** Los 15 stickers del búho que lleva la app (FR-008). */
    private static final Set<String> STICKERS = Set.of(
            "dorado-orgulloso", "dorado-celebrando", "dorado-guino", "dorado-sereno", "dorado-trofeo",
            "azul-animando", "azul-pensativo", "azul-reloj", "azul-firme", "azul-decepcionado",
            "azul-ofrece-ala", "azul-despertando", "azul-entrenando", "azul-vs-macaco", "azul-sorprendido");

    private static final Pattern VOSEO = Pattern.compile(
            "\\b(vos|podés|hacé|tenés|usted|sos|querés|sabés|mirá|decí|poné|andá|venís|sentís|pensás|empezá|dejá)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

    private static List<Fila> catalogo;

    private record Fila(String id, GrupoDelMensaje grupo, String texto, String sticker, Set<Variable> declaradas) {
    }

    @BeforeAll
    static void leerElJson() throws IOException {
        JsonNode raiz = new ObjectMapper().readTree(Files.readString(JSON));
        List<Fila> filas = new ArrayList<>();
        for (JsonNode nodo : raiz) {
            filas.add(new Fila(nodo.get("id").asText(), grupoDe(nodo), nodo.get("texto").asText(),
                    nodo.get("sticker").asText(), variablesDeclaradas(nodo.get("variables"))));
        }
        catalogo = filas;
    }

    @Test
    @DisplayName("hay 115 mensajes: 35 VERDE, 35 AMARILLO, 30 ROJO y 3 por cada caso sin color")
    void hayLosMensajesPrometidos() {
        Map<GrupoDelMensaje, Long> porGrupo = new EnumMap<>(GrupoDelMensaje.class);
        catalogo.forEach(f -> porGrupo.merge(f.grupo(), 1L, Long::sum));

        assertThat(catalogo).hasSize(115);
        assertThat(porGrupo).containsEntry(GrupoDelMensaje.VERDE, 35L).containsEntry(GrupoDelMensaje.AMARILLO, 35L)
                .containsEntry(GrupoDelMensaje.ROJO, 30L).containsEntry(GrupoDelMensaje.INICIO, 3L)
                .containsEntry(GrupoDelMensaje.SIN_PROGRAMA, 3L).containsEntry(GrupoDelMensaje.PAUSA, 3L)
                .containsEntry(GrupoDelMensaje.CUENTA_PAUSADA, 3L).containsEntry(GrupoDelMensaje.GRADUADO, 3L);
        assertThat(catalogo.stream().map(Fila::id).distinct()).hasSize(115);
    }

    @Test
    @DisplayName("ningún texto pasa de 140 caracteres contando 10 por variable")
    void ningunTextoEsLargo() {
        for (Fila fila : catalogo) {
            String conVariablesCompletas = fila.texto().replaceAll("\\{[^}]*}", "x".repeat(10));
            assertThat(conVariablesCompletas.length()).as(fila.id()).isLessThanOrEqualTo(140);
        }
    }

    @Test
    @DisplayName("no hay voseo: el español de Perú es de tuteo")
    void noHayVoseo() {
        for (Fila fila : catalogo) {
            Matcher voseo = VOSEO.matcher(fila.texto());
            assertThat(voseo.find()).as("%s: %s", fila.id(), fila.texto()).isFalse();
        }
    }

    @Test
    @DisplayName("las variables del texto son conocidas y coinciden con las declaradas")
    void lasVariablesCoinciden() {
        for (Fila fila : catalogo) {
            MensajeDelSemaforo mensaje = new MensajeDelSemaforo(fila.id(), fila.grupo(), fila.texto(),
                    fila.sticker(), true);
            assertThat(mensaje.variables()).as(fila.id()).isEqualTo(fila.declaradas());
        }
    }

    @Test
    @DisplayName("todos los stickers son uno de los 15")
    void losStickersSonLosQueTieneLaApp() {
        assertThat(catalogo).extracting(Fila::sticker).isSubsetOf(STICKERS);
    }

    @Test
    @DisplayName("los dorados, que celebran logros, solo van en VERDE y GRADUADO")
    void losDoradosSoloCelebran() {
        for (Fila fila : catalogo) {
            if (fila.sticker().startsWith("dorado-")) {
                assertThat(fila.grupo()).as(fila.id()).isIn(GrupoDelMensaje.VERDE, GrupoDelMensaje.GRADUADO);
            }
        }
    }

    @Test
    @DisplayName("{faltan} y {habitoFlojo} no aparecen donde no hay color que mejorar")
    void sinColorNoHayFaltanNiHabitoFlojo() {
        for (Fila fila : catalogo) {
            if (!fila.grupo().esDeColor()) {
                assertThat(fila.declaradas()).as(fila.id()).doesNotContain(Variable.FALTAN, Variable.HABITO_FLOJO);
            }
        }
    }

    @Test
    @DisplayName("{faltan} solo aparece en AMARILLO y ROJO, que son donde se puede subir de color")
    void faltanSoloDondeSePuedeSubir() {
        for (Fila fila : catalogo) {
            if (fila.declaradas().contains(Variable.FALTAN)) {
                assertThat(fila.grupo().admiteCuantoFalta()).as(fila.id()).isTrue();
            }
        }
    }

    @Test
    @DisplayName("V110 siembra exactamente los mismos 115 mensajes del JSON")
    void laMigracionSiembraElJson() throws IOException {
        String sql = Files.readString(MIGRACION);
        Matcher fila = Pattern.compile("^\\s+\\('(\\w+)', '(\\w+)', '((?:[^']|'')*)', '([\\w-]+)'\\)[,;]\\s*$",
                Pattern.MULTILINE).matcher(sql);
        Map<String, String> sembrados = new TreeMap<>();
        while (fila.find()) {
            sembrados.put(fila.group(1), fila.group(2) + "|" + fila.group(3).replace("''", "'") + "|" + fila.group(4));
        }
        Map<String, String> delJson = new TreeMap<>();
        catalogo.forEach(f -> delJson.put(f.id(), f.grupo() + "|" + f.texto() + "|" + f.sticker()));

        assertThat(sembrados).isEqualTo(delJson);
    }

    private static GrupoDelMensaje grupoDe(JsonNode nodo) {
        String color = nodo.get("color").asText();
        return GrupoDelMensaje.valueOf("SIN_COLOR".equals(color) ? nodo.get("caso").asText() : color);
    }

    private static Set<Variable> variablesDeclaradas(JsonNode variables) {
        Set<Variable> declaradas = EnumSet.noneOf(Variable.class);
        for (JsonNode variable : variables) {
            declaradas.addAll(Variable.enTexto("{" + variable.asText() + "}"));
        }
        return declaradas;
    }
}
