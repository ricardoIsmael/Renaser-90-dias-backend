package com.renaser.os.rag.domain.model.conversacion;

import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversacionRenasiaTest {

    private final UserId usuarioId = UserId.of(UUID.randomUUID());
    private final Instant ahora = Instant.parse("2026-08-25T10:00:00Z");

    @Test
    void iniciarRechazaUsuarioIdNulo() {
        assertThatThrownBy(() -> ConversacionRenasia.iniciar(null, ahora))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void iniciarDejaCreadoYActualizadoEnElMismoInstante() {
        ConversacionRenasia conversacion = ConversacionRenasia.iniciar(usuarioId, ahora);

        assertThat(conversacion.usuarioId()).isEqualTo(usuarioId);
        assertThat(conversacion.creadoEn()).isEqualTo(ahora);
        assertThat(conversacion.actualizadoEn()).isEqualTo(ahora);
    }

    @Test
    void tocarActualizaSoloElTimestampSinMutarLaInstanciaOriginal() {
        ConversacionRenasia original = ConversacionRenasia.iniciar(usuarioId, ahora);
        Instant masTarde = ahora.plusSeconds(60);

        ConversacionRenasia tocada = original.tocar(masTarde);

        assertThat(original.actualizadoEn()).isEqualTo(ahora);
        assertThat(tocada.actualizadoEn()).isEqualTo(masTarde);
        assertThat(tocada.creadoEn()).isEqualTo(ahora);
    }

    /** La identidad del agregado ES el usuario (PK = FK, 1:1 real — docs/MODULO_RAG.md §2):
     * dos instancias del mismo usuario, aunque difieran en timestamps, son la misma
     * conversacion. Es la garantia de dominio que respalda "un aprendiz, una conversacion". */
    @Test
    void dosInstanciasDelMismoUsuarioSonLaMismaConversacion() {
        ConversacionRenasia recienIniciada = ConversacionRenasia.iniciar(usuarioId, ahora);
        ConversacionRenasia rehidratadaMasTarde = ConversacionRenasia.rehydrate(usuarioId, ahora,
                ahora.plusSeconds(3600));

        assertThat(recienIniciada).isEqualTo(rehidratadaMasTarde);
        assertThat(recienIniciada.hashCode()).isEqualTo(rehidratadaMasTarde.hashCode());
    }

    @Test
    void conversacionesDeUsuariosDistintosNuncaSonIguales() {
        ConversacionRenasia deA = ConversacionRenasia.iniciar(usuarioId, ahora);
        ConversacionRenasia deB = ConversacionRenasia.iniciar(UserId.of(UUID.randomUUID()), ahora);

        assertThat(deA).isNotEqualTo(deB);
    }
}
