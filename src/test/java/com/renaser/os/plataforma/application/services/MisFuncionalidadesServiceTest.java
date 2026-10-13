package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.in.ConsultarMisFuncionalidadesUseCase.FuncionalidadApagada;
import com.renaser.os.plataforma.application.ports.in.ConsultarMisFuncionalidadesUseCase.MisFuncionalidades;
import com.renaser.os.plataforma.domain.model.estado.EstadoDeFuncionalidad;
import com.renaser.os.shared.application.EstadoAutomaticoDeFuncionalidades;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code GET /funcionalidades/mias} (contrato §6): las hijas de una madre apagada figuran todas, y lo que apaga el
 * circuito de la IA cuenta igual —también para la versión—, aunque nadie lo haya cambiado a mano. Sin Spring.
 */
@SuppressWarnings("unchecked")
class MisFuncionalidadesServiceTest {

    private final UserId aprendiz = UserId.of(UUID.randomUUID());
    private final Dobles.Base base = new Dobles.Base();
    private final CircuitoFalso circuito = new CircuitoFalso();
    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final CopiaDeEstados copia = new CopiaDeEstados(base);
    private MisFuncionalidadesService service;

    @BeforeEach
    void armar() {
        when(usuarios.findById(aprendiz)).thenReturn(Optional.of(
                new UserSummary(aprendiz, "A", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        ObjectProvider<EstadoAutomaticoDeFuncionalidades> automaticos = mock(ObjectProvider.class);
        when(automaticos.orderedStream()).thenAnswer(invocacion -> Stream.of(circuito));
        service = new MisFuncionalidadesService(new EstadoVigente(copia, automaticos), new RolDelActor(usuarios));
    }

    @Test
    @DisplayName("nada apagado: lista vacía, versión 0 y sin leer el rol")
    void nadaApagado() {
        MisFuncionalidades mias = service.mias(aprendiz);

        assertThat(mias.apagadas()).isEmpty();
        assertThat(mias.version()).isZero();
        verify(usuarios, never()).findById(any());
    }

    @Test
    @DisplayName("la madre apagada para el rol arrastra a todas sus hijas, cada una con el aviso de la madre")
    void lasHijasFiguranTodas() {
        base.filas.put("chat", new EstadoDeFuncionalidad("chat", true, Set.of(UserRole.TRAINEE), "Chat en pausa", 4,
                UserId.of(UUID.randomUUID()), Instant.parse("2026-10-13T22:00:00Z")));
        copia.recargar();

        MisFuncionalidades mias = service.mias(aprendiz);

        assertThat(mias.version()).isEqualTo(4);
        assertThat(mias.apagadas()).extracting(FuncionalidadApagada::clave).containsExactlyInAnyOrder("chat",
                "chat.grupos", "chat.global", "chat.soporte", "chat.directos", "chat.audio", "chat.fotos");
        assertThat(mias.apagadas()).extracting(FuncionalidadApagada::aviso).containsOnly("Chat en pausa");
    }

    @Test
    @DisplayName("el circuito abierto de ia.chat figura con su aviso, y la versión cambia al abrir y al cerrar")
    void elCircuitoCuentaParaLaListaYLaVersion() {
        long cerrado = service.mias(aprendiz).version();

        circuito.abrir("ia.chat");
        MisFuncionalidades abierto = service.mias(aprendiz);
        circuito.cerrar();
        MisFuncionalidades otraVezCerrado = service.mias(aprendiz);

        assertThat(abierto.apagadas()).containsExactly(new FuncionalidadApagada("ia.chat", "SER está en pausa"));
        assertThat(abierto.version()).isGreaterThan(cerrado);
        assertThat(otraVezCerrado.apagadas()).isEmpty();
        assertThat(otraVezCerrado.version()).isGreaterThan(abierto.version());
    }

    /** Un circuito de prueba: cada apertura y cada cierre suman uno a su versión. */
    static final class CircuitoFalso implements EstadoAutomaticoDeFuncionalidades {

        private String abierta;
        private long version;

        void abrir(String clave) {
            abierta = clave;
            version++;
        }

        void cerrar() {
            abierta = null;
            version++;
        }

        @Override
        public Optional<String> apagadaAutomaticamente(String clave) {
            return clave.equals(abierta) ? Optional.of("SER está en pausa") : Optional.empty();
        }

        @Override
        public long version() {
            return version;
        }

        @Override
        public List<CircuitoAutomatico> circuitos() {
            return List.of();
        }
    }
}
