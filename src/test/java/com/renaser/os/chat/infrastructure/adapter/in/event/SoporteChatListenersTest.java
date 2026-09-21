package com.renaser.os.chat.infrastructure.adapter.in.event;

import com.renaser.os.chat.application.ports.in.conversacion.IncorporarUsuarioAlSoporteUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.RetirarDelSoporteUseCase;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.RolDeUsuarioCambiadoEvent;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UsuarioRegistradoEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Los dos avisos que disparan el chat de soporte (D-136). Unit puro: confirman que el listener
 * traduce el evento al caso de uso correcto y <b>no decide la composicion por su cuenta</b> —
 * quien mira el rol vigente y resuelve a quien toca es el dominio. Lo unico que el listener lee
 * del evento es la <i>direccion</i> del cambio, que es el dato que el evento viaja justamente
 * para eso. La entrega real via el outbox de Modulith es infraestructura de Spring y no se
 * reprueba aca (mismo criterio que {@code UsuarioRegistradoChatListenerTest}).
 */
@ExtendWith(MockitoExtension.class)
class SoporteChatListenersTest {

    private static final UserId USUARIO = UserId.of(UUID.fromString("11111111-1111-4111-8111-111111111111"));

    @Mock
    private IncorporarUsuarioAlSoporteUseCase incorporarUseCase;
    @Mock
    private RetirarDelSoporteUseCase retirarUseCase;

    @Test
    @DisplayName("el alta de un usuario lo incorpora al circuito de soporte")
    void elAltaDeUnUsuarioLoIncorporaAlCircuitoDeSoporte() {
        new UsuarioRegistradoSoporteListener(incorporarUseCase)
                .on(new UsuarioRegistradoEvent(USUARIO, Instant.parse("2026-09-16T03:00:00Z")));

        verify(incorporarUseCase).incorporar(USUARIO);
    }

    @Test
    @DisplayName("un cambio de rol tambien lo incorpora: el ascenso a ADMIN no puede pasar inadvertido")
    void unCambioDeRolTambienLoIncorpora() {
        listener().on(new RolDeUsuarioCambiadoEvent(USUARIO, UserRole.MENTOR, UserRole.ADMIN,
                Instant.parse("2026-09-16T03:00:00Z")));

        verify(incorporarUseCase).incorporar(USUARIO);
        verify(retirarUseCase, never()).retirarPorBajaDeStaff(USUARIO);
    }

    /**
     * Regresion de la auditoria de seguridad. <b>Falla contra el codigo viejo</b>, que tiraba
     * {@code rolAnterior}/{@code rolNuevo} y llamaba solo a incorporar — un metodo que unicamente
     * suma. La fila de participante que dejo la etapa de staff le sobrevivia a la baja, y con ella
     * el chat privado de cada aprendiz.
     */
    @Test
    @DisplayName("bajar de ADMIN a MENTOR lo retira del soporte: el evento viaja la direccion justamente para esto")
    void laBajaDeStaffLoRetiraDelSoporte() {
        listener().on(new RolDeUsuarioCambiadoEvent(USUARIO, UserRole.ADMIN, UserRole.MENTOR,
                Instant.parse("2026-09-16T03:00:00Z")));

        verify(retirarUseCase).retirarPorBajaDeStaff(USUARIO);
    }

    @Test
    @DisplayName("bajar de ALCHEMIST a TRAINEE tambien retira: el escalon mas bajo no es una excepcion")
    void laBajaHastaAprendizTambienRetira() {
        listener().on(new RolDeUsuarioCambiadoEvent(USUARIO, UserRole.ALCHEMIST, UserRole.TRAINEE,
                Instant.parse("2026-09-16T03:00:00Z")));

        verify(retirarUseCase).retirarPorBajaDeStaff(USUARIO);
    }

    /**
     * La excepcion a CH-11 es ACOTADA: solo revoca a quien dejo de cumplir la regla 1. Un cambio
     * entre dos roles que nunca fueron staff no tiene por que tocar ninguna fila — si lo hiciera,
     * esto pasaria a ser una reconciliacion, que es lo que CH-11 prohibe.
     */
    @Test
    @DisplayName("un cambio entre roles que nunca fueron staff no retira nada")
    void unCambioAjenoAlStaffNoRetiraNada() {
        listener().on(new RolDeUsuarioCambiadoEvent(USUARIO, UserRole.MENTOR, UserRole.MENTOR_LEAD,
                Instant.parse("2026-09-16T03:00:00Z")));

        verify(incorporarUseCase).incorporar(USUARIO);
        verify(retirarUseCase, never()).retirarPorBajaDeStaff(USUARIO);
    }

    @Test
    @DisplayName("moverse DENTRO del staff (ADMIN a ALCHEMIST) no retira: sigue cumpliendo la regla 1")
    void moverseDentroDelStaffNoRetira() {
        listener().on(new RolDeUsuarioCambiadoEvent(USUARIO, UserRole.ADMIN, UserRole.ALCHEMIST,
                Instant.parse("2026-09-16T03:00:00Z")));

        verify(retirarUseCase, never()).retirarPorBajaDeStaff(USUARIO);
    }

    private RolDeUsuarioCambiadoSoporteListener listener() {
        return new RolDeUsuarioCambiadoSoporteListener(incorporarUseCase, retirarUseCase);
    }
}
