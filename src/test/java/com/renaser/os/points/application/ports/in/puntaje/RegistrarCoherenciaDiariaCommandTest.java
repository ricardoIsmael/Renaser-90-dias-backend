package com.renaser.os.points.application.ports.in.puntaje;

import com.renaser.os.points.application.ports.in.puntaje.RegistrarCoherenciaDiariaUseCase.RegistrarCoherenciaDiariaCommand;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrarCoherenciaDiariaCommandTest {

    private static UserId participante() {
        return UserId.of(UUID.randomUUID());
    }

    @Test
    void construyeUnComandoValidoSinExplotar() {
        var command = new RegistrarCoherenciaDiariaCommand(participante(), LocalDate.of(2026, 8, 24),
                BigDecimal.valueOf(85), true);

        assertThat(command.valor()).isEqualByComparingTo("85");
    }

    @Test
    void rechazaFechaNula() {
        assertThatThrownBy(() -> new RegistrarCoherenciaDiariaCommand(participante(), null, BigDecimal.TEN, false))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("el mismo CHECK 0..100 del baseline se valida en el borde del contrato del caso de uso, no solo en el dominio (CLAUDE.MD §5.4.3)")
    void rechazaValorFueraDeRangoAntesDeLlegarAlDominio() {
        assertThatThrownBy(() -> new RegistrarCoherenciaDiariaCommand(participante(), LocalDate.now(),
                BigDecimal.valueOf(100.5), true)).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> new RegistrarCoherenciaDiariaCommand(participante(), LocalDate.now(),
                BigDecimal.valueOf(-0.5), true)).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void aceptaLosBordesExactos0y100() {
        var enCero = new RegistrarCoherenciaDiariaCommand(participante(), LocalDate.now(), BigDecimal.ZERO, false);
        var enCien = new RegistrarCoherenciaDiariaCommand(participante(), LocalDate.now(), BigDecimal.valueOf(100),
                true);

        assertThat(enCero.valor()).isEqualByComparingTo("0");
        assertThat(enCien.valor()).isEqualByComparingTo("100");
    }
}
