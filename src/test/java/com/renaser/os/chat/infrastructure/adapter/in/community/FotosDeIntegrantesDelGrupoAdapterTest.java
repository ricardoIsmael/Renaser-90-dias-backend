package com.renaser.os.chat.infrastructure.adapter.in.community;

import com.renaser.os.chat.application.ports.in.conversacion.VerFotosDelChatUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.VerFotosDelChatUseCase.TarjetasDelGrupo;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Lo que chat le contesta a community sobre las fotos de la gente de un grupo (D-206). */
class FotosDeIntegrantesDelGrupoAdapterTest {

    private static final UUID GRUPO = UUID.randomUUID();
    private static final ConversacionId CHAT = ConversacionId.of(UUID.randomUUID());
    private static final UserId MENTOR = UserId.of(UUID.randomUUID());
    private static final UserId ANA = UserId.of(UUID.randomUUID());

    private final VerFotosDelChatUseCase fotos = mock(VerFotosDelChatUseCase.class);
    private final FotosDeIntegrantesDelGrupoAdapter adapter = new FotosDeIntegrantesDelGrupoAdapter(fotos);

    @Test
    @DisplayName("cada integrante que lleva tarjeta, con la ruta de la suya en el chat del grupo")
    void rutasEnElChatDelGrupo() {
        when(fotos.tarjetasDelGrupo(GRUPO, List.of(MENTOR, ANA)))
                .thenReturn(Optional.of(new TarjetasDelGrupo(CHAT, List.of(MENTOR, ANA))));

        assertThat(adapter.rutasDeLasFotos(GRUPO, List.of(MENTOR, ANA)))
                .containsEntry(MENTOR, "/api/v1/chat/conversations/" + CHAT + "/miembros/" + MENTOR + "/foto")
                .containsEntry(ANA, "/api/v1/chat/conversations/" + CHAT + "/miembros/" + ANA + "/foto")
                .hasSize(2);
    }

    @Test
    @DisplayName("quien no lleva tarjeta (modo «su foto si la subió») no figura: la app muestra su foto")
    void quienNoLlevaTarjetaNoFigura() {
        when(fotos.tarjetasDelGrupo(GRUPO, List.of(MENTOR, ANA)))
                .thenReturn(Optional.of(new TarjetasDelGrupo(CHAT, List.of(ANA))));

        assertThat(adapter.rutasDeLasFotos(GRUPO, List.of(MENTOR, ANA))).containsOnlyKeys(ANA);
    }

    @Test
    @DisplayName("un grupo sin chat no tiene dónde servirlas: sin rutas")
    void sinChatNoHayRutas() {
        when(fotos.tarjetasDelGrupo(GRUPO, List.of(ANA))).thenReturn(Optional.empty());

        assertThat(adapter.rutasDeLasFotos(GRUPO, List.of(ANA))).isEmpty();
    }

    @Test
    @DisplayName("sin integrantes no consulta nada")
    void sinIntegrantesNoConsulta() {
        assertThat(adapter.rutasDeLasFotos(GRUPO, List.of())).isEmpty();
        verifyNoInteractions(fotos);
    }
}
