package com.renaser.os.shared.application;

import com.renaser.os.shared.domain.ProcesoPospuestoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las dos formas en que un proceso respeta su interruptor (spec 002). */
class ProcesosConInterruptorTest {

    @Test
    @DisplayName("salta este turno solo si su funcionalidad (o su madre) está apagada")
    void saltaEsteTurno() {
        InterruptoresDePrueba interruptores = InterruptoresDePrueba.conApagadas("chat");

        assertThat(ProcesosConInterruptor.saltaEsteTurno(interruptores, "chat.soporte", "prueba")).isTrue();
        assertThat(ProcesosConInterruptor.saltaEsteTurno(interruptores, "muro", "prueba")).isFalse();
    }

    @Test
    @DisplayName("posponer lanza ProcesoPospuestoException para que el outbox lo reintente; prendida no hace nada")
    void posponer() {
        InterruptoresDePrueba interruptores = InterruptoresDePrueba.conApagadas("notificaciones");

        assertThatThrownBy(() -> ProcesosConInterruptor.posponerSiEstaApagada(interruptores, "notificaciones", "x"))
                .isInstanceOf(ProcesoPospuestoException.class).hasMessageContaining("notificaciones");
        assertThatCode(() -> ProcesosConInterruptor.posponerSiEstaApagada(interruptores, "chat", "x"))
                .doesNotThrowAnyException();
    }
}
