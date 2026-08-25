package com.renaser.os.support.application.ports.in.ticketsoporte;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.application.ports.in.ticketsoporte.AbrirTicketSoporteUseCase.AbrirTicketSoporteCommand;
import com.renaser.os.support.domain.model.ticketsoporte.CategoriaSoporte;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * docs/MODULO_SUPPORT.md §0.2: asunto maximo 200 caracteres, mensaje minimo 10 maximo 4000,
 * clientLog opcional maximo 4000.
 */
class AbrirTicketSoporteCommandTest {

    private static UserId usuario() {
        return UserId.of(UUID.randomUUID());
    }

    private static String de(int longitud) {
        return "a".repeat(longitud);
    }

    @Test
    void categoriaEsOpcionalEnElComando_seDefaulteaEnElServicio() {
        var command = new AbrirTicketSoporteCommand(usuario(), null, "Asunto", de(10), null, null, null);

        assertThat(command.categoria()).isNull();
    }

    @Test
    @DisplayName("asunto: hasta 200 caracteres es valido, 201 rechaza (schema.ts)")
    void asuntoHastaDoscientosCaracteres() {
        assertThatCode(() -> new AbrirTicketSoporteCommand(usuario(), CategoriaSoporte.OTRO, de(200), de(10), null,
                null, null)).doesNotThrowAnyException();

        assertThatThrownBy(() -> new AbrirTicketSoporteCommand(usuario(), CategoriaSoporte.OTRO, de(201), de(10),
                null, null, null)).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("mensaje: minimo 10, maximo 4000 caracteres (schema.ts)")
    void mensajeEntreDiezYCuatroMilCaracteres() {
        assertThatThrownBy(() -> new AbrirTicketSoporteCommand(usuario(), CategoriaSoporte.OTRO, "Asunto", de(9),
                null, null, null)).isInstanceOf(ConstraintViolationException.class);

        assertThatCode(() -> new AbrirTicketSoporteCommand(usuario(), CategoriaSoporte.OTRO, "Asunto", de(10), null,
                null, null)).doesNotThrowAnyException();

        assertThatCode(() -> new AbrirTicketSoporteCommand(usuario(), CategoriaSoporte.OTRO, "Asunto", de(4000),
                null, null, null)).doesNotThrowAnyException();

        assertThatThrownBy(() -> new AbrirTicketSoporteCommand(usuario(), CategoriaSoporte.OTRO, "Asunto", de(4001),
                null, null, null)).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("clientLog: opcional, maximo 4000 caracteres (ring buffer, nunca el body completo)")
    void clientLogEsOpcionalConMaximoDeCuatroMil() {
        assertThatCode(() -> new AbrirTicketSoporteCommand(usuario(), CategoriaSoporte.OTRO, "Asunto", de(10), null,
                null, null)).doesNotThrowAnyException();

        assertThatCode(() -> new AbrirTicketSoporteCommand(usuario(), CategoriaSoporte.OTRO, "Asunto", de(10),
                de(4000), null, null)).doesNotThrowAnyException();

        assertThatThrownBy(() -> new AbrirTicketSoporteCommand(usuario(), CategoriaSoporte.OTRO, "Asunto", de(10),
                de(4001), null, null)).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rechazaUsuarioIdNulo() {
        assertThatThrownBy(() -> new AbrirTicketSoporteCommand(null, CategoriaSoporte.OTRO, "Asunto", de(10), null,
                null, null)).isInstanceOf(ConstraintViolationException.class);
    }
}
