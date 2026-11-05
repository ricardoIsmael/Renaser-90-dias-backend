package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import com.renaser.os.calendar.domain.model.evento.Evento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El formulario del evento NO cambia (spec 041, FR-040 y FR-031): no hay eleccion de WhatsApp por evento ni «a quien le llega» por evento. Eso lo decide un valor de
 * configuracion global ({@code renaser.eventos.mensajes.audiencia}). Un APK anterior sigue creando y editando eventos igual, y la tabla {@code eventos} no suma ninguna
 * columna (lo comprueba {@code EntregasDeWhatsAppJdbcAdapterIT}).
 */
class FormularioDelEventoSinWhatsAppTest {

    private static final List<String> PALABRAS = List.of("whatsapp", "mensaje", "message", "audienciamensaje", "avisos");

    private static List<String> nombresDeLosCamposDe(Class<?> clase) {
        return Arrays.stream(clase.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    // spec 041 FR-040
    @Test
    @DisplayName("lo que se envia al crear o editar un evento no tiene ningun campo de WhatsApp ni de a quien le llega el mensaje")
    void elPedidoNoCambia() {
        assertThat(nombresDeLosCamposDe(EventoRequest.class)).noneMatch(n -> PALABRAS.stream().anyMatch(p -> n.toLowerCase().contains(p)));
    }

    // spec 041 FR-040
    @Test
    @DisplayName("lo que el servidor devuelve de un evento tampoco: la app anterior lo lee exactamente como antes")
    void laRespuestaNoCambia() {
        assertThat(nombresDeLosCamposDe(EventoResponse.class)).noneMatch(n -> PALABRAS.stream().anyMatch(p -> n.toLowerCase().contains(p)));
    }

    // spec 041 FR-040
    @Test
    @DisplayName("el evento del dominio no guarda ninguna eleccion de WhatsApp: la audiencia del mensaje es configuracion global")
    void elEventoNoGuardaLaEleccion() {
        List<String> campos = Arrays.stream(Evento.class.getDeclaredFields()).map(Field::getName).toList();

        assertThat(campos).noneMatch(n -> n.toLowerCase().contains("whatsapp") || n.toLowerCase().contains("mensaje"));
    }
}
