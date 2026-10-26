package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.in.ConsultarMisFuncionalidadesUseCase.FuncionalidadApagada;
import com.renaser.os.plataforma.application.ports.in.ConsultarMisFuncionalidadesUseCase.MisFuncionalidades;
import com.renaser.os.plataforma.domain.model.despliegue.DespliegueDeFuncionalidad;
import com.renaser.os.plataforma.domain.model.estado.EstadoDeFuncionalidad;
import com.renaser.os.plataforma.domain.model.estado.ReglaDeDisponibilidad;
import com.renaser.os.shared.application.EstadoAutomaticoDeFuncionalidades;
import com.renaser.os.shared.domain.FuncionalidadNoDisponibleException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * La regla del despliegue dentro de la evaluación real de la fase 1 (contrato fase 2 §1 y §3.2): quien está fuera la
 * recibe como «no disponible» (404 y {@code noDisponibles}, con las hijas), la probadora la tiene, y un interruptor
 * apagado gana (FR-021). Sin Spring.
 */
@SuppressWarnings("unchecked")
class ReglaDeDespliegueTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T03:30:00Z");

    private final UserId probadora = UserId.of(UUID.randomUUID());
    private final UserId otra = UserId.of(UUID.randomUUID());
    private final UserId soporte = UserId.of(UUID.randomUUID());
    private final Dobles.Despliegues despliegues = new Dobles.Despliegues();
    private final Dobles.Base estados = new Dobles.Base();
    private final CopiaDeDespliegues copia = new CopiaDeDespliegues(despliegues);
    private final CopiaDeEstados copiaDeEstados = new CopiaDeEstados(estados);
    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private MisFuncionalidadesService mias;
    private InterruptoresService interruptores;

    @BeforeEach
    void armar() {
        when(usuarios.findById(any())).thenAnswer(invocacion -> Optional.of(new UserSummary(invocacion.getArgument(0),
                "Alguien", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        ObjectProvider<EstadoAutomaticoDeFuncionalidades> sinAutomaticos = mock(ObjectProvider.class);
        when(sinAutomaticos.orderedStream()).thenReturn(Stream.empty());
        ObjectProvider<ReglaDeDisponibilidad> reglas = mock(ObjectProvider.class);
        when(reglas.orderedStream()).thenAnswer(invocacion -> Stream.of(new ReglaDeDespliegue(copia)));
        EstadoVigente estado = new EstadoVigente(copiaDeEstados, sinAutomaticos, reglas);
        mias = new MisFuncionalidadesService(estado, new RolDelActor(usuarios));
        interruptores = new InterruptoresService(estado, new RolDelActor(usuarios));
    }

    @Test
    @DisplayName("es la primera regla: @Order(10), antes de la de roles personalizados (@Order(20))")
    void orden() {
        assertThat(ReglaDeDespliegue.class.getAnnotation(Order.class).value()).isEqualTo(10);
    }

    @Test
    @DisplayName("al 0 % con una probadora: ella la tiene; otra persona, 404 y la clave (y sus hijas) en noDisponibles")
    void ceroConProbadora() {
        enDespliegue("chat", 0, probadora);

        assertThat(interruptores.prendidaPara("chat.grupos", probadora)).isTrue();
        assertThatThrownBy(() -> interruptores.exigirPrendidaPara("chat.grupos", otra))
                .isInstanceOf(FuncionalidadNoDisponibleException.class)
                .extracting(e -> ((FuncionalidadNoDisponibleException) e).clave()).isEqualTo("chat");
        MisFuncionalidades deOtra = mias.mias(otra);
        assertThat(deOtra.noDisponibles()).containsExactlyInAnyOrder("chat", "chat.grupos", "chat.global",
                "chat.soporte", "chat.directos", "chat.audio", "chat.fotos");
        assertThat(deOtra.apagadas()).isEmpty();
        assertThat(mias.mias(probadora).noDisponibles()).isEmpty();
        assertThat(interruptores.prendida("chat.grupos")).as("un proceso sin persona no ve el despliegue").isTrue();
    }

    @Test
    @DisplayName("al terminar (sin fila) llega a todos en cuanto la copia recarga")
    void terminar() {
        enDespliegue("chat", 0, probadora);
        despliegues.filas.clear();
        copia.recargar();

        assertThat(mias.mias(otra).noDisponibles()).isEmpty();
        assertThat(interruptores.prendidaPara("chat.grupos", otra)).isTrue();
    }

    @Test
    @DisplayName("FR-021: un interruptor apagado gana sobre el despliegue; ni la probadora la ve, y es «apagada», no «no disponible»")
    void elApagadoGana() {
        enDespliegue("chat", 0, probadora);
        estados.filas.put("chat", new EstadoDeFuncionalidad("chat", false, Set.of(), "Chat en mantenimiento", 3,
                soporte, AHORA));
        copiaDeEstados.recargar();

        MisFuncionalidades deLaProbadora = mias.mias(probadora);
        MisFuncionalidades deOtra = mias.mias(otra);

        assertThat(deLaProbadora.apagadas()).extracting(FuncionalidadApagada::clave).contains("chat", "chat.grupos");
        assertThat(deOtra.apagadas()).extracting(FuncionalidadApagada::aviso).containsOnly("Chat en mantenimiento");
        assertThat(deOtra.noDisponibles()).isEmpty();
    }

    @Test
    @DisplayName("una persona nueva cae en un grupo estable desde su primer pedido (sin asignar nada)")
    void personaNueva() {
        enDespliegue("eventos", 50, null);
        UserId nueva = UserId.of(UUID.randomUUID());

        boolean primera = interruptores.prendidaPara("eventos", nueva);
        for (int lectura = 0; lectura < 10; lectura++) {
            assertThat(interruptores.prendidaPara("eventos", nueva)).isEqualTo(primera);
        }
    }

    private void enDespliegue(String clave, int porcentaje, UserId probadora) {
        DespliegueDeFuncionalidad despliegue = DespliegueDeFuncionalidad.empezar(clave, porcentaje, 1, soporte, AHORA);
        despliegues.empezar(probadora == null ? despliegue : despliegue.conProbadora(probadora));
        copia.recargar();
    }
}
