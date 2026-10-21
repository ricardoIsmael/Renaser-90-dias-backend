package com.renaser.os.points.domain.model.semaforo.mensaje;

import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las piezas chicas del modelo: variables del texto, datos de la persona y lo que ya vio. */
class ModeloDelMensajeTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 19);

    @Test
    @DisplayName("las variables se derivan del texto, no se guardan")
    void lasVariablesSalenDelTexto() {
        MensajeDelSemaforo mensaje = new MensajeDelSemaforo("a001", GrupoDelMensaje.AMARILLO,
                "{nombre}, faltan {faltan} y {nombre} otra vez.", "azul-reloj", true);

        assertThat(mensaje.variables()).containsExactlyInAnyOrder(Variable.NOMBRE, Variable.FALTAN);
    }

    @Test
    @DisplayName("un texto con una variable que no existe se rechaza al construirlo: no llegaría a la persona con llaves")
    void unaVariableDesconocidaSeRechaza() {
        assertThatThrownBy(() -> new MensajeDelSemaforo("a001", GrupoDelMensaje.AMARILLO, "{nobre}, hola.",
                "azul-reloj", true)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nobre");
    }

    @Test
    @DisplayName("una llave suelta también se rechaza")
    void unaLlaveSueltaSeRechaza() {
        assertThatThrownBy(() -> Variable.enTexto("Hola {nombre"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Variable.enTexto("Hola nombre}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Variable.enTexto("Hola {{nombre}}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("un texto de más de 300 caracteres se rechaza: el contrato promete 300")
    void elTextoTieneTope() {
        assertThatThrownBy(() -> new MensajeDelSemaforo("a001", GrupoDelMensaje.VERDE, "x".repeat(301),
                "dorado-sereno", true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("los datos vacíos, el día 0 o «faltan 0» cuentan como que no se saben")
    void losDatosSinSentidoNoSeSaben() {
        DatosDelMensaje datos = new DatosDelMensaje("  ", 0, " ", "", null, 0);

        for (Variable variable : Variable.values()) {
            assertThat(datos.tiene(variable)).as("%s", variable).isFalse();
        }
        assertThat(datos.tieneTodas(Set.of())).isTrue();
    }

    @Test
    @DisplayName("el nombre se da completo y se usa el primero")
    void elPrimerNombre() {
        DatosDelMensaje datos = new DatosDelMensaje("  Ana   Lucía Pérez ", 5, "El Espejo", null, null, 2);

        assertThat(datos.valorDe(Variable.NOMBRE)).isEqualTo("Ana");
        assertThat(datos.valorDe(Variable.DIA)).isEqualTo("5");
        assertThat(datos.valorDe(Variable.FALTAN)).isEqualTo("2");
        assertThat(datos.tieneTodas(Set.of(Variable.NOMBRE, Variable.DIA, Variable.FASE))).isTrue();
        assertThat(datos.tieneTodas(Set.of(Variable.NOMBRE, Variable.HABITO_FLOJO))).isFalse();
    }

    @Test
    @DisplayName("lo visto: los últimos 14 días hasta ayer, y aparte lo de ayer; lo de hoy y lo más viejo no cuentan")
    void loVisto() {
        MensajesVistos vistos = MensajesVistos.de(List.of(
                visto(HOY.minusDays(15), "v001", CanalDelMensaje.HOY),
                visto(HOY.minusDays(14), "v002", CanalDelMensaje.HOY),
                visto(HOY.minusDays(2), "v003", CanalDelMensaje.HOY),
                visto(HOY.minusDays(1), "v004", CanalDelMensaje.HOY),
                visto(HOY, "v005", CanalDelMensaje.HOY)), HOY, CanalDelMensaje.HOY);

        assertThat(vistos.enLaVentana()).containsExactlyInAnyOrder("v002", "v003", "v004");
        assertThat(vistos.deAyer()).containsExactly("v004");
        assertThat(vistos.hoyEnElOtroCanal()).as("lo de hoy del mismo canal no es «el otro canal»").isEmpty();
        assertThat(MensajesVistos.desde(HOY)).isEqualTo(HOY.minusDays(14));
    }

    @Test
    @DisplayName("D-281: la ventana de 14 días suma los dos canales")
    void laVentanaSumaLosDosCanales() {
        MensajesVistos vistos = MensajesVistos.de(List.of(
                visto(HOY.minusDays(5), "a001", CanalDelMensaje.HOY),
                visto(HOY.minusDays(1), "a002", CanalDelMensaje.CHAT),
                visto(HOY.minusDays(14), "a003", CanalDelMensaje.CHAT)), HOY, CanalDelMensaje.HOY);

        assertThat(vistos.enLaVentana()).containsExactlyInAnyOrder("a001", "a002", "a003");
        assertThat(vistos.deAyer()).containsExactly("a002");
    }

    @Test
    @DisplayName("D-281: lo que el OTRO canal mostró hoy va aparte, de cualquier grupo, y es lo que no se repite hoy")
    void loDeHoyDelOtroCanal() {
        List<MensajeVisto> filas = List.of(
                visto(HOY, "v001", CanalDelMensaje.HOY),
                visto(HOY, "a009", CanalDelMensaje.HOY),
                visto(HOY, "r003", CanalDelMensaje.CHAT));

        assertThat(MensajesVistos.de(filas, HOY, CanalDelMensaje.CHAT).hoyEnElOtroCanal())
                .containsExactlyInAnyOrder("v001", "a009");
        assertThat(MensajesVistos.de(filas, HOY, CanalDelMensaje.HOY).hoyEnElOtroCanal())
                .containsExactly("r003");
        assertThat(MensajesVistos.de(filas, HOY, CanalDelMensaje.CHAT).enLaVentana())
                .as("lo de hoy no entra en la ventana, que llega hasta ayer").isEmpty();
    }

    @Test
    @DisplayName("la clave de la elección exige todos sus datos")
    void laClaveExigeTodo() {
        UserId persona = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> new ClaveDeLaEleccion(null, HOY, GrupoDelMensaje.VERDE, CanalDelMensaje.CHAT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ClaveDeLaEleccion(persona, HOY, GrupoDelMensaje.VERDE, null))
                .isInstanceOf(NullPointerException.class);
        assertThat(new ClaveDeLaEleccion(persona, HOY, GrupoDelMensaje.VERDE, CanalDelMensaje.CHAT).canal())
                .isEqualTo(CanalDelMensaje.CHAT);
    }

    private static MensajeVisto visto(LocalDate fecha, String id, CanalDelMensaje canal) {
        return new MensajeVisto(fecha, id, canal);
    }
}
