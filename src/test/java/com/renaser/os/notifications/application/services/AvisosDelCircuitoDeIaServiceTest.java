package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.application.ports.in.plataforma.AvisarCircuitoDeIaUseCase.CambioDeCircuito;
import com.renaser.os.notifications.application.ports.out.limite.LimiteDeAvisosPorHoraPort;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El push a Soporte TI por el circuito de SER (spec 002, FR-016): a quién, qué dice, y como máximo 4 por hora. */
class AvisosDelCircuitoDeIaServiceTest {

    private static final Instant INICIO = Instant.parse("2026-10-13T03:00:00Z");

    private final UserId soporte = UserId.of(UUID.randomUUID());
    private final EmitirNotificacionUseCase emitir = mock(EmitirNotificacionUseCase.class);
    private final ParticipacionProgramaFinder participaciones = mock(ParticipacionProgramaFinder.class);
    private final LimiteEnMemoria limite = new LimiteEnMemoria();
    private final AvisosDelCircuitoDeIaService servicio = new AvisosDelCircuitoDeIaService(emitir, participaciones,
            limite, 4);

    @BeforeEach
    void armar() {
        when(participaciones.usuariosActivosConRol(Set.of(UserRole.IT_SUPPORT))).thenReturn(List.of(soporte));
    }

    private List<EmitirNotificacionCommand> avisos() {
        ArgumentCaptor<EmitirNotificacionCommand> captor = ArgumentCaptor.forClass(EmitirNotificacionCommand.class);
        verify(emitir, atLeastOnce()).emitir(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("abierto y cerrado: a Soporte TI, OPERACION_PLATAFORMA, con la causa y la ruta de la consola")
    void queDice() {
        servicio.avisar(new CambioDeCircuito("ia.chat", "ABIERTO",
                "5 fallas del proveedor en 2 min; se prueba de nuevo en 2 min", INICIO));
        servicio.avisar(new CambioDeCircuito("ia.voz", "CERRADO", "Salió bien el pedido de prueba",
                INICIO.plusSeconds(60)));

        List<EmitirNotificacionCommand> avisos = avisos();
        assertThat(avisos).extracting(EmitirNotificacionCommand::usuarioId).containsOnly(soporte);
        assertThat(avisos).extracting(EmitirNotificacionCommand::tipo).containsOnly(TipoNotificacion.OPERACION_PLATAFORMA);
        assertThat(avisos).extracting(EmitirNotificacionCommand::rutaApp).containsOnly("/soporte-ti");
        assertThat(avisos).extracting(EmitirNotificacionCommand::titulo).containsExactly("SER se pausó sola",
                "SER volvió");
        assertThat(avisos).extracting(EmitirNotificacionCommand::cuerpo).containsExactly(
                "Chat de SER: 5 fallas del proveedor en 2 min; se prueba de nuevo en 2 min.",
                "Voz de SER: Salió bien el pedido de prueba.");
    }

    @Test
    @DisplayName("como máximo 4 por hora por parte: el quinto y el sexto se omiten; la otra parte tiene su propio tope")
    void cuatroPorHora() {
        for (int i = 0; i < 6; i++) {
            servicio.avisar(new CambioDeCircuito("ia.chat", i % 2 == 0 ? "ABIERTO" : "CERRADO", "x",
                    INICIO.plus(Duration.ofMinutes(5L * i))));
        }
        servicio.avisar(new CambioDeCircuito("ia.voz", "ABIERTO", "x", INICIO.plus(Duration.ofMinutes(30))));
        servicio.avisar(new CambioDeCircuito("ia.chat", "ABIERTO", "x", INICIO.plus(Duration.ofMinutes(61))));

        assertThat(avisos()).hasSize(4 + 1 + 1);
    }

    @Test
    @DisplayName("idempotente: la reentrega del mismo cambio tiene el mismo origen y no gasta otro lugar del tope")
    void idempotente() {
        CambioDeCircuito cambio = new CambioDeCircuito("ia.chat", "ABIERTO", "x", INICIO);
        servicio.avisar(cambio);
        servicio.avisar(cambio);
        for (int i = 1; i <= 3; i++) {
            servicio.avisar(new CambioDeCircuito("ia.chat", "CERRADO", "x", INICIO.plusSeconds(i)));
        }

        List<EmitirNotificacionCommand> avisos = avisos();
        assertThat(avisos).hasSize(5);
        assertThat(avisos.get(0).origenEventoId()).isEqualTo(avisos.get(1).origenEventoId());
        assertThat(avisos.get(2).origenEventoId()).isNotEqualTo(avisos.get(0).origenEventoId());
    }

    /** El tope de Redis en memoria, con la misma regla: los que salieron en la hora que termina en el aviso. */
    private static final class LimiteEnMemoria implements LimiteDeAvisosPorHoraPort {
        private final Map<String, Map<UUID, Instant>> salidos = new HashMap<>();

        @Override
        public boolean admite(String grupo, UUID id, Instant en, int maximo) {
            Map<UUID, Instant> avisos = salidos.computeIfAbsent(grupo, g -> new HashMap<>());
            if (avisos.containsKey(id)) {
                return true;
            }
            List<Instant> enLaHora = new ArrayList<>(avisos.values().stream()
                    .filter(instante -> instante.isAfter(en.minus(Duration.ofHours(1))) && !instante.isAfter(en))
                    .toList());
            if (enLaHora.size() >= maximo) {
                return false;
            }
            avisos.put(id, en);
            return true;
        }
    }
}
