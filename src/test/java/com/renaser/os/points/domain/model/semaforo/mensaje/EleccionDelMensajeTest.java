package com.renaser.os.points.domain.model.semaforo.mensaje;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntUnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FR-003, FR-004, FR-005: cómo se elige el mensaje del día. Dominio puro, con un azar fijo. */
class EleccionDelMensajeTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 19);
    private static final IntUnaryOperator SIEMPRE_EL_PRIMERO = n -> 0;
    private static final IntUnaryOperator SIEMPRE_EL_ULTIMO = n -> n - 1;

    private static MensajeDelSemaforo mensaje(String id, String texto) {
        return new MensajeDelSemaforo(id, GrupoDelMensaje.AMARILLO, texto, "azul-reloj", true);
    }

    private static final MensajeDelSemaforo SIN_DATOS_1 = mensaje("a001", "Todavía puedes levantar el ritmo.");
    private static final MensajeDelSemaforo SIN_DATOS_2 = mensaje("a002", "Haz hoy una cosa pendiente.");
    private static final MensajeDelSemaforo CON_NOMBRE = mensaje("a003", "{nombre}, hoy sí se puede.");
    private static final MensajeDelSemaforo CON_FLOJO = mensaje("a004", "Dedica tiempo a {habitoFlojo}.");
    private static final MensajeDelSemaforo CON_FALTAN = mensaje("a005", "Te faltan {faltan} para subir.");

    private static final List<MensajeDelSemaforo> DEL_GRUPO =
            List.of(SIN_DATOS_1, SIN_DATOS_2, CON_NOMBRE, CON_FLOJO, CON_FALTAN);

    private static DatosDelMensaje solo(String nombre) {
        return new DatosDelMensaje(nombre, null, null, null, null, null);
    }

    private static Optional<MensajeDelSemaforo> elegir(IntUnaryOperator azar, List<MensajeDelSemaforo> delGrupo,
                                                       DatosDelMensaje datos, MensajesVistos vistos) {
        return new EleccionDelMensaje(azar, CanalDelMensaje.HOY).elegir(delGrupo, datos, vistos);
    }

    private static Optional<MensajeDelSemaforo> elegirEnElChat(IntUnaryOperator azar, List<MensajeDelSemaforo> delGrupo,
                                                               DatosDelMensaje datos, MensajesVistos vistos) {
        return new EleccionDelMensaje(azar, CanalDelMensaje.CHAT).elegir(delGrupo, datos, vistos);
    }

    @Test
    @DisplayName("nunca elige un mensaje que pida un dato que la persona no tiene")
    void filtraPorLasVariablesDisponibles() {
        for (int indice = 0; indice < 2; indice++) {
            int fijo = indice;
            Optional<MensajeDelSemaforo> elegido = elegir(n -> Math.min(fijo, n - 1), DEL_GRUPO,
                    DatosDelMensaje.sinDatos(), MensajesVistos.ninguno());

            assertThat(elegido).get().isIn(SIN_DATOS_1, SIN_DATOS_2);
        }
    }

    @Test
    @DisplayName("con nombre, los mensajes que lo piden entran como candidatos")
    void unDatoHabilitaLosMensajesQueLoPiden() {
        Optional<MensajeDelSemaforo> elegido = elegir(SIEMPRE_EL_ULTIMO, DEL_GRUPO, solo("María"),
                MensajesVistos.ninguno());

        assertThat(elegido).contains(CON_NOMBRE);
    }

    @Test
    @DisplayName("quita los vistos en los últimos 14 días")
    void noRepiteLosVistos() {
        MensajesVistos vistos = new MensajesVistos(Set.of("a001"), Set.of());

        Optional<MensajeDelSemaforo> elegido = elegir(SIEMPRE_EL_PRIMERO, DEL_GRUPO, DatosDelMensaje.sinDatos(), vistos);

        assertThat(elegido).contains(SIN_DATOS_2);
    }

    @Test
    @DisplayName("si ya vio todos los de su grupo, vuelve a elegir entre todos menos el de ayer")
    void catalogoAgotadoQuitaSoloElDeAyer() {
        MensajesVistos vistos = new MensajesVistos(Set.of("a001", "a002"), Set.of("a001"));

        Optional<MensajeDelSemaforo> elegido = elegir(SIEMPRE_EL_PRIMERO, DEL_GRUPO, DatosDelMensaje.sinDatos(), vistos);

        assertThat(elegido).contains(SIN_DATOS_2);
    }

    @Test
    @DisplayName("si el de ayer era el único posible, se vuelve a elegir entre todos")
    void siSoloQuedabaElDeAyerSeUsa() {
        MensajesVistos vistos = new MensajesVistos(Set.of("a001"), Set.of("a001"));

        Optional<MensajeDelSemaforo> elegido = elegir(SIEMPRE_EL_PRIMERO, List.of(SIN_DATOS_1),
                DatosDelMensaje.sinDatos(), vistos);

        assertThat(elegido).contains(SIN_DATOS_1);
    }

    @Test
    @DisplayName("elige con el azar: el índice que da el azar es el mensaje")
    void eligeSegunElAzar() {
        List<MensajeDelSemaforo> dos = List.of(SIN_DATOS_1, SIN_DATOS_2);

        assertThat(elegir(SIEMPRE_EL_PRIMERO, dos, DatosDelMensaje.sinDatos(), MensajesVistos.ninguno()))
                .contains(SIN_DATOS_1);
        assertThat(elegir(SIEMPRE_EL_ULTIMO, dos, DatosDelMensaje.sinDatos(), MensajesVistos.ninguno()))
                .contains(SIN_DATOS_2);
    }

    @Test
    @DisplayName("si en el grupo ningún mensaje tiene sus datos, no hay mensaje: no se inventa uno")
    void sinNingunCandidatoNoHayMensaje() {
        List<MensajeDelSemaforo> todosConVariables = List.of(CON_NOMBRE, CON_FLOJO, CON_FALTAN);

        Optional<MensajeDelSemaforo> elegido = elegir(SIEMPRE_EL_PRIMERO, todosConVariables,
                DatosDelMensaje.sinDatos(), MensajesVistos.ninguno());

        assertThat(elegido).isEmpty();
    }

    @Test
    @DisplayName("un mensaje desactivado no se elige")
    void ignoraLosInactivos() {
        MensajeDelSemaforo apagado = new MensajeDelSemaforo("a009", GrupoDelMensaje.AMARILLO, "Apagado.",
                "azul-reloj", false);

        Optional<MensajeDelSemaforo> elegido = elegir(SIEMPRE_EL_PRIMERO, List.of(apagado),
                DatosDelMensaje.sinDatos(), MensajesVistos.ninguno());

        assertThat(elegido).isEmpty();
    }

    @Test
    @DisplayName("un azar que se sale del rango es un error, no una elección silenciosa")
    void unAzarFueraDeRangoFalla() {
        assertThatThrownBy(() -> elegir(n -> n, DEL_GRUPO, solo("Ana"), MensajesVistos.ninguno()))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    // ── el canal del chat (D-281) ───────────────────────────────────────────

    @Test
    @DisplayName("en el chat nunca se elige un mensaje con {faltan}, aunque la persona tenga ese dato")
    void elChatNoEligeLosQueDicenCuantoFalta() {
        DatosDelMensaje conTodo = new DatosDelMensaje("Ana", 5, "El Espejo", "lectura", "jugo verde", 2);

        for (int indice = 0; indice < DEL_GRUPO.size(); indice++) {
            int fijo = indice;
            Optional<MensajeDelSemaforo> elegido = elegirEnElChat(n -> Math.min(fijo, n - 1), DEL_GRUPO, conTodo,
                    MensajesVistos.ninguno());

            assertThat(elegido).as("azar en %d", fijo).get().isNotEqualTo(CON_FALTAN);
        }
        assertThat(elegir(SIEMPRE_EL_ULTIMO, DEL_GRUPO, conTodo, MensajesVistos.ninguno()))
                .as("en Hoy el mismo grupo sí puede elegirlo").contains(CON_FALTAN);
    }

    @Test
    @DisplayName("si en el grupo solo quedan mensajes con {faltan}, el chat no manda mensaje: no se inventa uno")
    void elChatSinCandidatosNoTieneMensaje() {
        DatosDelMensaje conTodo = new DatosDelMensaje("Ana", 5, "El Espejo", "lectura", "jugo verde", 2);

        assertThat(elegirEnElChat(SIEMPRE_EL_PRIMERO, List.of(CON_FALTAN), conTodo, MensajesVistos.ninguno()))
                .isEmpty();
    }

    @Test
    @DisplayName("el mensaje del chat es distinto del que la persona vio en Hoy ese día")
    void distintoDelDeHoy() {
        MensajesVistos vistos = new MensajesVistos(Set.of(), Set.of(), Set.of("a001"));

        assertThat(elegirEnElChat(SIEMPRE_EL_PRIMERO, DEL_GRUPO, DatosDelMensaje.sinDatos(), vistos))
                .contains(SIN_DATOS_2);
    }

    @Test
    @DisplayName("lo que Hoy mostró ese día no vuelve ni cuando ya vio todo su grupo: sin otro posible, no hay mensaje")
    void loDeHoyNoSeRepiteNiConElCatalogoAgotado() {
        MensajesVistos vistos = new MensajesVistos(Set.of("a001"), Set.of("a001"), Set.of("a001"));

        assertThat(elegirEnElChat(SIEMPRE_EL_PRIMERO, List.of(SIN_DATOS_1), DatosDelMensaje.sinDatos(), vistos))
                .isEmpty();
    }

    @Test
    @DisplayName("la regla de no repetir cuenta lo visto en cualquiera de los dos canales")
    void noRepiteLoVistoEnElOtroCanal() {
        MensajesVistos deHoyHaceTresDias = MensajesVistos.de(
                List.of(new MensajeVisto(HOY.minusDays(3), "a001", CanalDelMensaje.HOY)), HOY, CanalDelMensaje.CHAT);
        MensajesVistos delChatHaceDosDias = MensajesVistos.de(
                List.of(new MensajeVisto(HOY.minusDays(2), "a001", CanalDelMensaje.CHAT)), HOY, CanalDelMensaje.HOY);

        assertThat(elegirEnElChat(SIEMPRE_EL_PRIMERO, DEL_GRUPO, DatosDelMensaje.sinDatos(), deHoyHaceTresDias))
                .as("en el chat, lo que Hoy mostró hace 3 días cuenta como visto").contains(SIN_DATOS_2);
        assertThat(elegir(SIEMPRE_EL_PRIMERO, DEL_GRUPO, DatosDelMensaje.sinDatos(), delChatHaceDosDias))
                .as("en Hoy, lo que el chat mostró hace 2 días cuenta como visto").contains(SIN_DATOS_2);
    }
}
