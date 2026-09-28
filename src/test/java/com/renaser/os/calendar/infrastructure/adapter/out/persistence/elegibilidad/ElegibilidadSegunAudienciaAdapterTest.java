package com.renaser.os.calendar.infrastructure.adapter.out.persistence.elegibilidad;

import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HALLAZGO-A2 (E-362): la elegibilidad no le agrega nada a la audiencia que el evento declara. Contra el
 * adaptador anterior ({@code ElegibilidadEventoNoOpAdapter}, siempre {@code false}) esta prueba falla, y con
 * ella ningun aprendiz veia una Mentoria del Alquimista.
 *
 * <p>Si el dueño define un criterio extra (pregunta abierta de D-213), esta prueba tiene que FALLAR: es el
 * recordatorio de que el criterio cambio, no un contrato a preservar.
 */
class ElegibilidadSegunAudienciaAdapterTest {

    private final ElegibilidadSegunAudienciaAdapter adapter = new ElegibilidadSegunAudienciaAdapter();

    @ParameterizedTest
    @EnumSource(TipoEvento.class)
    @DisplayName("E-362: ningun tipo de evento le suma un criterio a la audiencia mientras el dueño no lo defina")
    void laAudienciaDecide(TipoEvento tipoEvento) {
        assertThat(adapter.esElegible(UserId.of(UUID.randomUUID()), tipoEvento)).isTrue();
    }
}
