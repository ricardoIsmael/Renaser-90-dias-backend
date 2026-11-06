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

        assertThat(texto).isEqualTo("\n\nFoto para registrar 'Jugo verde': si no ves el botón de la cámara, súbela desde Entrenar.");
        assertThat(texto).doesNotContain(SIN_TILDES);
    }

    // issue #63
    @Test
    @DisplayName("un ritual se registra escribiendo: «cuenta cómo te sientes» y «botón»")
    void ritual() {
        String texto = ConversacionRenasiaService.textoDeRitual("RITUAL TIERRA - AGUA - FUEGO (mañana)");

        assertThat(texto).isEqualTo("\n\nPara registrar 'Ritual tierra - agua - fuego (mañana)' cuenta cómo te sientes: "
                + "si no ves el botón para escribir, hazlo desde Entrenar.");
        assertThat(texto).doesNotContain("cuenta como te sientes").doesNotContain("boton");
    }

    // issue #65
    @Test
    @DisplayName("el título del hábito va en tipo oración, como en la app: «RITUAL TIERRA - AGUA - FUEGO (noche)» se lee «Ritual tierra - agua - fuego (noche)»")
    void elTituloDelHabitoVaEnTipoOracion() {
        assertThat(ConversacionRenasiaService.textoDeRitual("RITUAL TIERRA - AGUA - FUEGO (noche)"))
                .contains("'Ritual tierra - agua - fuego (noche)'").doesNotContain("RITUAL");
        assertThat(ConversacionRenasiaService.textoDeEvidencia("AGUA TIBIA CON LIMÓN")).contains("'Agua tibia con limón'");
        // Un título que ya trae minúsculas (el que la persona puso al renombrar, «Clase diaria») se deja tal cual, y las siglas se quedan.
        assertThat(ConversacionRenasiaService.textoDeEvidencia("Clase diaria")).contains("'Clase diaria'");
        assertThat(ConversacionRenasiaService.textoDeEvidencia("MEDITAR CON SER")).contains("'Meditar con SER'");
        // Por el pedido (lo que usan el chat y la voz en vivo): el hábito en tipo oración, el ritual también.
        assertThat(ConversacionRenasiaService.textoDeEvidencia(pedido("RITUAL DE LA NOCHE", true, DestinoDeEvidencia.HABITO)))
                .contains("'Ritual de la noche'");
        assertThat(ConversacionRenasiaService.textoDeEvidencia(pedido("JUGO VERDE", false, DestinoDeEvidencia.HABITO)))
                .contains("'Jugo verde'");
    }

    // issue #63
    @Test
    @DisplayName("la foto de una acción del día dice «acción», «botón», «cámara» y «súbela», y su título (lo escribió la persona) no se toca")
    void accionDelDia() {
        String texto = ConversacionRenasiaService.textoDeEvidencia(pedido("Llamar a 3 clientes", false, DestinoDeEvidencia.ROCA));

        assertThat(texto).isEqualTo("\n\nFoto para registrar tu acción 'Llamar a 3 clientes': si no ves el botón de la cámara, súbela desde Entrenar.");
        assertThat(texto).doesNotContain(SIN_TILDES);
        assertThat(ConversacionRenasiaService.textoDeEvidencia(pedido("LLAMAR A 3 CLIENTES", false, DestinoDeEvidencia.ROCA)))
                .contains("tu acción 'LLAMAR A 3 CLIENTES'");
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
