package com.renaser.os.onboarding.application.ports.in.estado;

import com.renaser.os.onboarding.application.ports.in.estado.AceptarHitoOnboardingUseCase.AceptarHitoCommand;
import com.renaser.os.onboarding.domain.model.estado.HitoOnboarding;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AceptarHitoCommandTest {

    @Test
    void construyeUnComandoValidoSinExplotar() {
        UserId usuarioId = UserId.of(UUID.randomUUID());

        var command = new AceptarHitoCommand(usuarioId, HitoOnboarding.PACTO);

        assertThat(command.hito()).isEqualTo(HitoOnboarding.PACTO);
    }

    @Test
    void rechazaUsuarioIdNulo() {
        assertThatThrownBy(() -> new AceptarHitoCommand(null, HitoOnboarding.PACTO))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rechazaHitoNulo() {
        UserId usuarioId = UserId.of(UUID.randomUUID());
        assertThatThrownBy(() -> new AceptarHitoCommand(usuarioId, null))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("el comando SOLO tiene usuarioId + hito (CLAUDE.MD §5.3.3)")
    void tieneSoloDosComponentes() {
        assertThat(AceptarHitoCommand.class.getRecordComponents()).hasSize(2);
    }
}
