package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.propuesta.ConsultarPropuestasDelTurnoUseCase.PedidoDeEvidencia;
import com.renaser.os.rag.domain.model.conversacion.DestinoDeEvidencia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #63: los textos de respaldo de una tarjeta de foto o de un ritual (los que ve una app sin la tarjeta y el historial) iban sin tildes, y el del ritual decía
 * «cuenta como te sientes» (falta el «cómo»). Son texto que lee la persona, español de Perú con tildes.
 */
class RespaldosDeEvidenciaConTildesTest {

    private static final String[] SIN_TILDES = {"boton", "camara", "subela", " accion"};

    private static PedidoDeEvidencia pedido(String titulo, boolean conPregunta, DestinoDeEvidencia destino) {
        return new PedidoDeEvidencia(UUID.randomUUID(), titulo, Instant.parse("2026-11-02T10:00:00Z"), Instant.parse("2026-11-03T05:00:00Z"), conPregunta, destino);
    }

    // issue #63
    @Test
    @DisplayName("la foto de un hábito: «botón», «cámara» y «súbela», con tildes")
    void fotoDeUnHabito() {
        String texto = ConversacionRenasiaService.textoDeEvidencia("JUGO VERDE");

        assertThat(texto).isEqualTo("\n\nFoto para registrar 'JUGO VERDE': si no ves el botón de la cámara, súbela desde Training.");
        assertThat(texto).doesNotContain(SIN_TILDES);
    }

    // issue #63
    @Test
    @DisplayName("un ritual se registra escribiendo: «cuenta cómo te sientes» y «botón»")
    void ritual() {
        String texto = ConversacionRenasiaService.textoDeRitual("RITUAL TIERRA - AGUA - FUEGO (mañana)");

        assertThat(texto).isEqualTo("\n\nPara registrar 'RITUAL TIERRA - AGUA - FUEGO (mañana)' cuenta cómo te sientes: "
                + "si no ves el botón para escribir, hazlo desde Training.");
        assertThat(texto).doesNotContain("cuenta como te sientes").doesNotContain("boton");
    }

    // issue #63
    @Test
    @DisplayName("la foto de una acción del día dice «acción», «botón», «cámara» y «súbela»")
    void accionDelDia() {
        String texto = ConversacionRenasiaService.textoDeEvidencia(pedido("Llamar a 3 clientes", false, DestinoDeEvidencia.ROCA));

        assertThat(texto).isEqualTo("\n\nFoto para registrar tu acción 'Llamar a 3 clientes': si no ves el botón de la cámara, súbela desde Training.");
        assertThat(texto).doesNotContain(SIN_TILDES);
    }

    // issue #63
    @Test
    @DisplayName("el pedido decide cuál: ritual con pregunta, hábito con foto")
    void elPedidoDecideElTexto() {
        assertThat(ConversacionRenasiaService.textoDeEvidencia(pedido("RITUAL DE LA NOCHE", true, DestinoDeEvidencia.HABITO)))
                .contains("cuenta cómo te sientes");
        assertThat(ConversacionRenasiaService.textoDeEvidencia(pedido("JUGO VERDE", false, DestinoDeEvidencia.HABITO)))
                .contains("la cámara");
    }
}
