package com.renaser.os.rag.application.services.circuito;

import com.renaser.os.rag.application.ports.in.circuito.CircuitoDeLaIa;
import com.renaser.os.rag.domain.model.circuito.ParteDeLaIa;
import com.renaser.os.shared.application.InterruptoresDePrueba;
import com.renaser.os.shared.domain.FuncionalidadApagadaException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lo manual se pregunta primero y manda (FR-017); el circuito después, porque puede tomar el turno de prueba. */
class InterruptoresDeSerTest {

    private static final UserId ACTOR = UserId.of(UUID.randomUUID());

    @Test
    @DisplayName("ia.voz apagada a mano: no hay voz y al circuito ni se le pregunta (no se gasta la prueba)")
    void vozApagadaAManoNoGastaLaPrueba() {
        CircuitoDeLaIa circuitoQueNoDebeConsultarse = new CircuitoDeLaIa() {
            @Override
            public void exigirPaso(ParteDeLaIa parte) {
                throw new AssertionError("con lo manual apagado no se pide paso al circuito");
            }

            @Override
            public boolean dejaPasar(ParteDeLaIa parte) {
                throw new AssertionError("con lo manual apagado no se pide paso al circuito");
            }

            @Override
            public void anotarFalla(ParteDeLaIa parte) {
            }

            @Override
            public void anotarExito(ParteDeLaIa parte) {
            }

            @Override
            public boolean cortado(ParteDeLaIa parte) {
                return false;
            }
        };
        InterruptoresDeSer ser = new InterruptoresDeSer(InterruptoresDePrueba.conApagadas("ia.voz"),
                circuitoQueNoDebeConsultarse);

        assertThat(ser.vozPara(ACTOR)).isFalse();
    }

    @Test
    @DisplayName("ia general apagada apaga la voz y el chat, con la clave de lo manual")
    void laMadreApaga() {
        InterruptoresDeSer ser = CircuitoDePrueba.cerrado().interruptoresConApagadas("ia");

        assertThat(ser.vozPara(ACTOR)).isFalse();
        assertThatThrownBy(() -> ser.exigirChat(ACTOR)).isInstanceOf(FuncionalidadApagadaException.class)
                .satisfies(e -> assertThat(((FuncionalidadApagadaException) e).clave()).isEqualTo("ia.chat"));
        assertThat(ser.puedeCompactarMemoria()).isFalse();
    }

    @Test
    @DisplayName("con lo manual prendido, el circuito abierto corta con el aviso automático")
    void circuitoAbierto() {
        InterruptoresDeSer ser = CircuitoDePrueba.cortando(ParteDeLaIa.CHAT, ParteDeLaIa.VOZ).interruptores();

        assertThatThrownBy(() -> ser.exigirChat(ACTOR)).isInstanceOf(FuncionalidadApagadaException.class)
                .hasMessage(EstadoAutomaticoDeLaIa.AVISO);
        assertThat(ser.vozPara(ACTOR)).isFalse();
        assertThat(ser.puedeCompactarMemoria()).as("no se llama a un proveedor que no responde").isFalse();
        assertThat(ser.propuestasPara(ACTOR)).as("las propuestas no tienen circuito").isTrue();
    }

    @Test
    @DisplayName("todo prendido: chat, voz, propuestas y memoria")
    void todoPrendido() {
        InterruptoresDeSer ser = CircuitoDePrueba.cerrado().interruptores();

        ser.exigirChat(ACTOR);
        assertThat(ser.vozPara(ACTOR)).isTrue();
        assertThat(ser.propuestasPara(ACTOR)).isTrue();
        assertThat(ser.puedeCompactarMemoria()).isTrue();
        assertThat(CircuitoDePrueba.cerrado().interruptoresConApagadas("ia.propuestas").propuestasPara(ACTOR))
                .isFalse();
    }
}
