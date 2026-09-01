package com.renaser.os.users.application.ports.in.accountrequest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubmitAccountRequestCommandTest {

    @Test
    void construyeUnComandoConDatosValidosSinExplotar() {
        var command = SubmitAccountRequestUseCase.SubmitAccountRequestCommand.porFormulario(
                "valido@renaser.com", "Ana", "+51999999999", "Lima", "token-verificacion",
                "una-contrasena-de-12", "127.0.0.1");

        assertThat(command.email()).isEqualTo("valido@renaser.com");
    }

    @Test
    void aceptaContrasenaNullParaElAltaPorProveedorSocial() {
        var command = SubmitAccountRequestUseCase.SubmitAccountRequestCommand.porFormulario(
                "valido@renaser.com", "Ana", "+51999999999", "Lima", "token-verificacion",
                null, "127.0.0.1");

        assertThat(command.contrasena()).isNull();
    }

    /**
     * D-61 (2026-09-01): el telefono dejo de ser {@code @NotBlank}. Antes, este mismo comando con
     * {@code phone: null} explotaba con {@code ConstraintViolationException} — que es el 400 que
     * recibia el frontend, que ya habia dejado de mandar el campo.
     */
    @Test
    void aceptaTelefonoNullPorqueSePideEnLaFichaInicialDelOnboarding() {
        var command = SubmitAccountRequestUseCase.SubmitAccountRequestCommand.porFormulario(
                "valido@renaser.com", "Ana", null, "Lima", "token-verificacion",
                "una-contrasena-de-12", "127.0.0.1");

        assertThat(command.phone()).isNull();
    }

    /** Mismo motivo, por la otra puerta: el alta social nunca trae telefono (Google no lo da). */
    @Test
    void aceptaTelefonoNullTambienEnElAltaPorProveedorSocial() {
        var command = SubmitAccountRequestUseCase.SubmitAccountRequestCommand.porProveedorSocial(
                "social@renaser.com", "Sofia Social", null, null, "token-verificacion",
                "127.0.0.1", com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad.GOOGLE,
                "google-sub-1");

        assertThat(command.phone()).isNull();
        assertThat(command.contrasena()).isNull();
    }

    @Test
    void rechazaUnaContrasenaMasCortaQueElMinimo() {
        assertThatThrownBy(() -> SubmitAccountRequestUseCase.SubmitAccountRequestCommand.porFormulario(
                "valido@renaser.com", "Ana", "+51999999999", "Lima", "token-verificacion",
                "corta", "127.0.0.1"))
                .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
    }

    @Test
    void noFiltraLaContrasenaNiElTokenEnElToString() {
        var command = SubmitAccountRequestUseCase.SubmitAccountRequestCommand.porFormulario(
                "valido@renaser.com", "Ana", "+51999999999", "Lima", "token-secreto",
                "una-contrasena-de-12", "127.0.0.1");

        assertThat(command.toString()).doesNotContain("una-contrasena-de-12", "token-secreto");
    }
}
