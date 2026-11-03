package com.renaser.os.rag.domain.model.aviso;

import com.renaser.os.rag.domain.model.aviso.AvisosEnChat.DatosDelAviso;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Unitaria de dominio pura: que tipos pasan al chat y como se reemplazan los marcadores. */
class AvisosEnChatTest {

    private static final DatosDelAviso DATOS = new DatosDelAviso("Meditar", LocalTime.of(21, 5), 10, 30);

    private static AvisosEnChat prendidos(Set<String> tipos) {
        return new AvisosEnChat(true, tipos, Map.of(
                "INICIO", "{habito} empieza a las {hora} ({puntos} pts, en {minutos} min)",
                "POR_VENCER", "Se vence {habito} a las {hora}"));
    }

    @Test
    void conElInterruptorApagadoNingunTipoPasa() {
        var avisos = new AvisosEnChat(false, Set.of("INICIO"), Map.of("INICIO", "texto"));

        assertThat(avisos.aplicaA("INICIO")).isFalse();
        assertThat(avisos.redactar("INICIO", DATOS)).isEmpty();
        assertThat(AvisosEnChat.apagados().aplicaA("INICIO")).isFalse();
    }

    @Test
    void reemplazaTodosLosMarcadoresConLosDatosDelAviso() {
        assertThat(prendidos(Set.of("INICIO")).redactar("INICIO", DATOS))
                .contains("Meditar empieza a las 21:05 (10 pts, en 30 min)");
    }

    @Test
    void unTipoNoElegidoODesconocidoNoPasa() {
        var soloInicio = prendidos(Set.of("INICIO"));

        assertThat(soloInicio.aplicaA("POR_VENCER")).isFalse();
        assertThat(soloInicio.aplicaA("OTRO")).isFalse();
        assertThat(soloInicio.aplicaA(null)).isFalse();
    }

    @Test
    void unaPlantillaVaciaApagaEseTipoAunqueEsteElegido() {
        var avisos = new AvisosEnChat(true, Set.of("INICIO"), Map.of("INICIO", "   "));

        assertThat(avisos.aplicaA("INICIO")).isFalse();
    }

    @Test
    void unTituloConMarcadoresNoSeReemplaza() {
        var datos = new DatosDelAviso("Leer {hora}", LocalTime.of(7, 0), 10, 15);

        assertThat(prendidos(Set.of("POR_VENCER")).redactar("POR_VENCER", datos))
                .contains("Se vence Leer {hora} a las 07:00");
    }

    // issue #63
    @Test
    void elTituloDelCatalogoEnMayusculasSeLeeEnTipoOracion() {
        var avisos = new AvisosEnChat(true, Set.of("INICIO"), Map.of("INICIO", "Tu hábito {habito} empieza a las {hora}. Hay {puntos} puntos en juego."));

        assertThat(avisos.redactar("INICIO", new DatosDelAviso("ÚLTIMA COMIDA DEL DÍA", LocalTime.of(18, 0), 10, 15)))
                .contains("Tu hábito Última comida del día empieza a las 18:00. Hay 10 puntos en juego.");
        assertThat(avisos.redactar("INICIO", new DatosDelAviso("RITUAL TIERRA - AGUA - FUEGO (mañana)", LocalTime.of(6, 0), 8, 15)))
                .contains("Tu hábito Ritual tierra - agua - fuego (mañana) empieza a las 06:00. Hay 8 puntos en juego.");
        assertThat(avisos.redactar("INICIO", new DatosDelAviso("HABLAR CON SER", LocalTime.of(9, 0), 8, 15)))
                .contains("Tu hábito Hablar con SER empieza a las 09:00. Hay 8 puntos en juego.");
    }

    // issue #63
    @Test
    void unTituloQueYaTieneMinusculasSeDejaComoLoEscribio() {
        var avisos = new AvisosEnChat(true, Set.of("INICIO"), Map.of("INICIO", "Tu hábito {habito} empieza a las {hora}."));

        assertThat(avisos.redactar("INICIO", new DatosDelAviso("Clase diaria", LocalTime.of(14, 59), 10, 15)))
                .contains("Tu hábito Clase diaria empieza a las 14:59.");
        assertThat(avisos.redactar("INICIO", new DatosDelAviso("Mi jugo verde", LocalTime.of(7, 0), 10, 15)))
                .contains("Tu hábito Mi jugo verde empieza a las 07:00.");
    }
}
