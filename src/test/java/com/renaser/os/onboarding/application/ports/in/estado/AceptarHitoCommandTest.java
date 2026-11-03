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

        var command = new AceptarHitoCommand(usuarioId, HitoOnboarding.PACTO, null, null);

        assertThat(command.hito()).isEqualTo(HitoOnboarding.PACTO);
    }

    @Test
    void rechazaUsuarioIdNulo() {
        assertThatThrownBy(() -> new AceptarHitoCommand(null, HitoOnboarding.PACTO, null, null))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rechazaHitoNulo() {
        UserId usuarioId = UserId.of(UUID.randomUUID());
        assertThatThrownBy(() -> new AceptarHitoCommand(usuarioId, null, null, null))
                .isInstanceOf(ConstraintViolationException.class);
    }

    // spec 039 FR-011; decisión 2026-11-02 pagos fuera de términos
    @Test
    @DisplayName("el comando solo tiene usuarioId, hito y lo que firma en Términos (versión e inicio visto), sin huella de cronograma (decisión 2026-11-02 pagos fuera de términos) y sin rol (CLAUDE.MD §5.3.3)")
    void tieneSoloLosComponentesDeLaAceptacion() {
        assertThat(AceptarHitoCommand.class.getRecordComponents()).extracting(componente -> componente.getName())
                .containsExactly("usuarioId", "hito", "versionDeTerminosId", "inicioVisto");
    }
}
