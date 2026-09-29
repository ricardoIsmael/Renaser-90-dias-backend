package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.AbrirChatsConAcompananteUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.CompletarChatsDeAprendicesUseCase.ResultadoCompletar;
import com.renaser.os.chat.application.ports.in.conversacion.RellenarConversacionesDeSoporteUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.RellenarConversacionesDeSoporteUseCase.ResultadoRelleno;
import com.renaser.os.chat.application.ports.out.participante.AcompanamientoDelGrupoPort;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El barrido que completa los chats de los aprendices (D-224): delega en los dos caminos que ya los
 * crean, y un grupo que falla no deja sin chats a los demás (regla 02 §4).
 */
@ExtendWith(MockitoExtension.class)
class CompletarChatsDeAprendicesServiceTest {

    private static final UUID GRUPO_A = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID GRUPO_B = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");
    private static final UserId ANA = UserId.of(UUID.fromString("11111111-1111-4111-8111-111111111111"));

    @Mock
    private RellenarConversacionesDeSoporteUseCase soportes;
    @Mock
    private AbrirChatsConAcompananteUseCase chatsDeDos;
    @Mock
    private AcompanamientoDelGrupoPort acompanamientoPort;

    private CompletarChatsDeAprendicesService service;

    @BeforeEach
    void preparar() {
        service = new CompletarChatsDeAprendicesService(soportes, chatsDeDos, acompanamientoPort);
    }

    @Test
    @DisplayName("rellena los soportes sin actor y abre los chats de dos de cada grupo operativo")
    void completaSoportesYChatsDeDos() {
        when(soportes.rellenarPendientes()).thenReturn(new ResultadoRelleno(10, 3, 7, 0));
        when(acompanamientoPort.gruposOperativos()).thenReturn(List.of(GRUPO_A, GRUPO_B));
        when(chatsDeDos.abrirParaGrupo(GRUPO_A)).thenReturn(2);
        when(chatsDeDos.abrirParaGrupo(GRUPO_B)).thenReturn(1);

        ResultadoCompletar resultado = service.completarTodos();

        assertThat(resultado).isEqualTo(new ResultadoCompletar(3, 0, 2, 3, 0));
        assertThat(resultado.incompleto()).isFalse();
        verify(soportes, never()).rellenar(any());
    }

    @Test
    @DisplayName("un grupo que falla se cuenta y no detiene a los demás")
    void unGrupoQueFallaNoDetieneAlResto() {
        when(soportes.rellenarPendientes()).thenReturn(new ResultadoRelleno(0, 0, 0, 0));
        when(acompanamientoPort.gruposOperativos()).thenReturn(List.of(GRUPO_A, GRUPO_B));
        when(chatsDeDos.abrirParaGrupo(GRUPO_A)).thenThrow(new IllegalStateException("fallo simulado"));
        when(chatsDeDos.abrirParaGrupo(GRUPO_B)).thenReturn(1);

        ResultadoCompletar resultado = service.completarTodos();

        assertThat(resultado).isEqualTo(new ResultadoCompletar(0, 0, 2, 1, 1));
        assertThat(resultado.incompleto()).isTrue();
    }

    @Test
    @DisplayName("para una persona: su soporte y los chats de SUS grupos; un fallo no se propaga (lo reintenta el barrido)")
    void unaPersona() {
        when(soportes.rellenarDe(ANA)).thenReturn(true);
        when(acompanamientoPort.gruposOperativosDe(ANA)).thenReturn(List.of(GRUPO_A));
        when(chatsDeDos.abrirParaGrupo(GRUPO_A)).thenThrow(new IllegalStateException("fallo simulado"));

        assertThatCode(() -> service.completarDe(ANA)).doesNotThrowAnyException();

        verify(chatsDeDos).abrirParaGrupo(GRUPO_A);
        verify(acompanamientoPort, never()).gruposOperativos();
    }
}
