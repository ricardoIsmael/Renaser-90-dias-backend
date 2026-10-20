package com.renaser.os.points.domain.model.semaforo.mensaje;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FR-005: el texto sale sin llaves ni huecos, con los datos de ese momento. */
class RedaccionDelMensajeTest {

    private static MensajeDelSemaforo mensaje(String texto) {
        return new MensajeDelSemaforo("a999", GrupoDelMensaje.AMARILLO, texto, "azul-reloj", true);
    }

    private static final DatosDelMensaje TODO = new DatosDelMensaje("María del Carmen Quispe", 15,
            "El Ciclo Alquímico", "JUGO VERDE", "Clase diaria", 2);

    @Test
    @DisplayName("reemplaza las seis variables")
    void reemplazaLasSeisVariables() {
        MensajeDelSemaforo todas = mensaje("{nombre} día {dia} en {fase}: {habitoFlojo}, {habitoFuerte}, {faltan}.");

        String texto = RedaccionDelMensaje.redactar(todas, TODO);

        assertThat(texto).isEqualTo("María día 15 en El Ciclo Alquímico: jugo verde, Clase diaria, 2.");
    }

    @Test
    @DisplayName("usa solo el primer nombre")
    void usaElPrimerNombre() {
        assertThat(RedaccionDelMensaje.redactar(mensaje("{nombre}, vamos."), TODO)).isEqualTo("María, vamos.");
    }

    @Test
    @DisplayName("el resultado nunca contiene llaves")
    void nuncaQuedanLlaves() {
        DatosDelMensaje conLlaves = new DatosDelMensaje("{Ana}", 3, "{fase}", "{x} CORRER {y}", null, 1);

        String texto = RedaccionDelMensaje.redactar(
                mensaje("{nombre} en {fase} día {dia}: {habitoFlojo}. Faltan {faltan}."), conLlaves);

        assertThat(texto).doesNotContain("{").doesNotContain("}");
    }

    @Test
    @DisplayName("un hábito del catálogo, que se guarda en mayúsculas, se escribe en minúsculas dentro de la frase")
    void habitoEnMayusculasVaEnMinusculas() {
        assertThat(RedaccionDelMensaje.redactar(mensaje("Dedica tiempo a {habitoFlojo}."), TODO))
                .isEqualTo("Dedica tiempo a jugo verde.");
    }

    @Test
    @DisplayName("un hábito con alguna minúscula (el que la persona renombró o «Pastilla Renacer») se deja como está")
    void habitoMixtoSeDejaIgual() {
        DatosDelMensaje datos = new DatosDelMensaje(null, null, null, "Pastilla Renacer", null, null);

        assertThat(RedaccionDelMensaje.redactar(mensaje("Dedica tiempo a {habitoFlojo}."), datos))
                .isEqualTo("Dedica tiempo a Pastilla Renacer.");
    }

    @Test
    @DisplayName("con un solo pendiente, «faltan 1» se escribe «falta 1»")
    void conUnoSeEscribeEnSingular() {
        DatosDelMensaje uno = new DatosDelMensaje("Ana", null, null, null, null, 1);

        assertThat(RedaccionDelMensaje.redactar(mensaje("Te faltan {faltan} para subir al siguiente color."), uno))
                .isEqualTo("Te falta 1 para subir al siguiente color.");
        assertThat(RedaccionDelMensaje.redactar(mensaje("{nombre}, faltan {faltan} para avanzar."), uno))
                .isEqualTo("Ana, falta 1 para avanzar.");
    }

    @Test
    @DisplayName("con dos o más pendientes se queda en plural")
    void conDosSeQuedaEnPlural() {
        assertThat(RedaccionDelMensaje.redactar(mensaje("Te faltan {faltan} para subir."), TODO))
                .isEqualTo("Te faltan 2 para subir.");
    }

    @Test
    @DisplayName("un mensaje sin variables sale tal cual")
    void sinVariablesSaleTalCual() {
        assertThat(RedaccionDelMensaje.redactar(mensaje("Buen trabajo."), DatosDelMensaje.sinDatos()))
                .isEqualTo("Buen trabajo.");
    }

    @Test
    @DisplayName("si falta un dato avisa en vez de dejar un hueco")
    void siFaltaUnDatoFalla() {
        assertThatThrownBy(() -> RedaccionDelMensaje.redactar(mensaje("{nombre}, vamos."), DatosDelMensaje.sinDatos()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("{nombre}");
    }
}
