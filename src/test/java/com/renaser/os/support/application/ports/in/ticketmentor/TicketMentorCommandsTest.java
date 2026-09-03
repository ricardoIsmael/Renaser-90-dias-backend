package com.renaser.os.support.application.ports.in.ticketmentor;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.application.ports.in.ticketmentor.AbrirTicketMentorUseCase.AbrirTicketMentorCommand;
import com.renaser.os.support.application.ports.in.ticketmentor.BuscarBibliotecaUseCase.BuscarBibliotecaCommand;
import com.renaser.os.support.application.ports.in.ticketmentor.GuardarEnBibliotecaUseCase.GuardarEnBibliotecaCommand;
import com.renaser.os.support.application.ports.in.ticketmentor.ResponderTicketMentorUseCase.ResponderTicketMentorCommand;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentorId;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validacion self-validating (CLAUDE.MD §5.4.3, nivel "contrato del caso de uso") de los
 * comandos de tickets al mentor. NOTA: a diferencia de AbrirTicketSoporteCommand, estos
 * comandos solo validan @NotNull/@NotBlank -- no tienen limite de longitud maxima (@Size)
 * pese a que docs/MODULO_SUPPORT.md §0.1 documenta un maximo de caracteres para los 3
 * campos de apertura, para la respuesta del mentor y para la busqueda en biblioteca.
 */
class TicketMentorCommandsTest {

    private static UserId id() {
        return UserId.of(UUID.randomUUID());
    }

    @Test
    void abrirRechazaParticipanteIdNulo() {
        assertThatThrownBy(() -> new AbrirTicketMentorCommand(null, "bloqueo", "solucion", "impacto"))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void abrirRechazaCamposEnBlanco() {
        assertThatThrownBy(() -> new AbrirTicketMentorCommand(id(), " ", "solucion", "impacto"))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> new AbrirTicketMentorCommand(id(), "bloqueo", null, "impacto"))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> new AbrirTicketMentorCommand(id(), "bloqueo", "solucion", ""))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void responderRechazaRespuestaEnBlanco() {
        assertThatThrownBy(() -> new ResponderTicketMentorCommand(TicketMentorId.of(UUID.randomUUID()), id(), "   "))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void responderRechazaTicketIdNulo() {
        assertThatThrownBy(() -> new ResponderTicketMentorCommand(null, id(), "respuesta"))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void buscarRechazaQueryEnBlanco() {
        assertThatThrownBy(() -> new BuscarBibliotecaCommand(id(), " "))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void guardarRechazaActorIdNulo() {
        assertThatThrownBy(() -> new GuardarEnBibliotecaCommand(TicketMentorId.of(UUID.randomUUID()), null))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void losCuatroComandosSonValidosConDatosCorrectos() {
        assertThat(new AbrirTicketMentorCommand(id(), "a", "b", "c").descripcionBloqueo()).isEqualTo("a");
        assertThat(new ResponderTicketMentorCommand(TicketMentorId.of(UUID.randomUUID()), id(), "r").respuesta())
                .isEqualTo("r");
        assertThat(new BuscarBibliotecaCommand(id(), "q").query()).isEqualTo("q");
        assertThat(new GuardarEnBibliotecaCommand(TicketMentorId.of(UUID.randomUUID()), id()).actorId()).isNotNull();
    }
}
