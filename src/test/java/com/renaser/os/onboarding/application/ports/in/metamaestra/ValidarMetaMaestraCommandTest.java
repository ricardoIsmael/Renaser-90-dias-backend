package com.renaser.os.onboarding.application.ports.in.metamaestra;

import com.renaser.os.onboarding.application.ports.in.metamaestra.ValidarMetaMaestraUseCase.ValidarMetaMaestraCommand;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidarMetaMaestraCommandTest {

    @Test
    void construyeUnComandoValidoSinExplotar() {
        UserId actorId = UserId.of(UUID.randomUUID());

        var command = new ValidarMetaMaestraCommand(actorId, "Mi meta maestra completa");

        assertThat(command.actorId()).isEqualTo(actorId);
        assertThat(command.texto()).isEqualTo("Mi meta maestra completa");
    }

    @Test
    void rechazaActorIdNulo() {
        assertThatThrownBy(() -> new ValidarMetaMaestraCommand(null, "texto"))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rechazaTextoVacio() {
        UserId actorId = UserId.of(UUID.randomUUID());
        assertThatThrownBy(() -> new ValidarMetaMaestraCommand(actorId, " "))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rechazaTextoDeMasDe3000Caracteres() {
        UserId actorId = UserId.of(UUID.randomUUID());
        String textoLargo = "a".repeat(3001);
        assertThatThrownBy(() -> new ValidarMetaMaestraCommand(actorId, textoLargo))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void aceptaTextoDeExactamente3000Caracteres() {
        UserId actorId = UserId.of(UUID.randomUUID());
        String textoLimite = "a".repeat(3000);

        var command = new ValidarMetaMaestraCommand(actorId, textoLimite);

        assertThat(command.texto()).hasSize(3000);
    }
}
