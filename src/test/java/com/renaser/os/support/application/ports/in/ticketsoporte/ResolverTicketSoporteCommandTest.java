package com.renaser.os.support.application.ports.in.ticketsoporte;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.application.ports.in.ticketsoporte.ResolverTicketSoporteUseCase.ResolverTicketSoporteCommand;
import com.renaser.os.support.domain.model.ticketsoporte.TicketSoporteId;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResolverTicketSoporteCommandTest {

    private static String de(int longitud) {
        return "a".repeat(longitud);
    }

    @Test
    void adminNotesEsOpcional() {
        var command = new ResolverTicketSoporteCommand(TicketSoporteId.of(UUID.randomUUID()),
                UserId.of(UUID.randomUUID()), null);

        assertThat(command.adminNotes()).isNull();
    }

    @Test
    void adminNotesHastaCuatroMilCaracteresEsValido_4001Rechaza() {
        TicketSoporteId ticketId = TicketSoporteId.of(UUID.randomUUID());
        UserId actorId = UserId.of(UUID.randomUUID());

        assertThatCode(() -> new ResolverTicketSoporteCommand(ticketId, actorId, de(4000)))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> new ResolverTicketSoporteCommand(ticketId, actorId, de(4001)))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rechazaTicketIdNulo() {
        assertThatThrownBy(() -> new ResolverTicketSoporteCommand(null, UserId.of(UUID.randomUUID()), "notas"))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rechazaActorIdNulo() {
        assertThatThrownBy(() -> new ResolverTicketSoporteCommand(TicketSoporteId.of(UUID.randomUUID()), null, "notas"))
                .isInstanceOf(ConstraintViolationException.class);
    }
}
