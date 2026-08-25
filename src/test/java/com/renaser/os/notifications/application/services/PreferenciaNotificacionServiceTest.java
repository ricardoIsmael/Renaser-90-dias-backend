package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.in.preferencia.GestionarPreferenciasUseCase.ActualizarPreferenciasCommand;
import com.renaser.os.notifications.application.ports.in.preferencia.GestionarPreferenciasUseCase.ItemPreferencia;
import com.renaser.os.notifications.application.ports.out.preferencia.LoadPreferenciasPort;
import com.renaser.os.notifications.application.ports.out.preferencia.SavePreferenciaPort;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.notifications.domain.model.preferencia.PreferenciaNotificacion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreferenciaNotificacionServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Mock
    private LoadPreferenciasPort loadPreferenciasPort;
    @Mock
    private SavePreferenciaPort savePreferenciaPort;

    private PreferenciaNotificacionService service;

    private static UserId usuario() {
        return UserId.of(UUID.randomUUID());
    }

    @BeforeEach
    void setUp() {
        service = new PreferenciaNotificacionService(loadPreferenciasPort, savePreferenciaPort, CLOCK);
    }

    @Test
    @DisplayName("consultar() completa con default HABILITADA los tipos sin fila propia")
    void consultarCompletaConDefault() {
        UserId actor = usuario();
        when(loadPreferenciasPort.porUsuario(actor)).thenReturn(
                List.of(PreferenciaNotificacion.de(actor, TipoNotificacion.MENSAJE_MENTOR, false, CLOCK)));

        List<PreferenciaNotificacion> resultado = service.consultar(actor);

        assertThat(resultado).hasSize(TipoNotificacion.values().length);
        assertThat(resultado.stream().filter(p -> p.tipo() == TipoNotificacion.MENSAJE_MENTOR).findFirst()
                .orElseThrow().habilitada()).isFalse();
        assertThat(resultado.stream().filter(p -> p.tipo() == TipoNotificacion.ANUNCIO_SISTEMA).findFirst()
                .orElseThrow().habilitada()).isTrue(); // sin fila -> default
    }

    @Test
    void actualizarPersisteCadaItemYDevuelveElEstadoCompleto() {
        UserId actor = usuario();
        lenient().when(loadPreferenciasPort.porUsuario(actor)).thenReturn(List.of());

        service.actualizar(new ActualizarPreferenciasCommand(actor,
                List.of(new ItemPreferencia(TipoNotificacion.MENSAJE_CHAT, false),
                        new ItemPreferencia(TipoNotificacion.RESUMEN_SEMANAL, false))));

        ArgumentCaptor<PreferenciaNotificacion> captor = ArgumentCaptor.forClass(PreferenciaNotificacion.class);
        verify(savePreferenciaPort, times(2)).upsert(captor.capture());
        assertThat(captor.getAllValues()).extracting(PreferenciaNotificacion::tipo)
                .containsExactlyInAnyOrder(TipoNotificacion.MENSAJE_CHAT, TipoNotificacion.RESUMEN_SEMANAL);
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: autoservicio por diseno de firma — dos actores nunca comparten resultado")
    void esAutoservicioPorConstruccion() {
        UserId actorA = usuario();
        UserId actorB = usuario();
        when(loadPreferenciasPort.porUsuario(actorA)).thenReturn(
                List.of(PreferenciaNotificacion.de(actorA, TipoNotificacion.MENSAJE_MENTOR, false, CLOCK)));
        when(loadPreferenciasPort.porUsuario(actorB)).thenReturn(List.of());

        List<PreferenciaNotificacion> deA = service.consultar(actorA);
        List<PreferenciaNotificacion> deB = service.consultar(actorB);

        boolean mentorApagadoParaA = deA.stream()
                .anyMatch(p -> p.tipo() == TipoNotificacion.MENSAJE_MENTOR && !p.habilitada());
        boolean mentorApagadoParaB = deB.stream()
                .anyMatch(p -> p.tipo() == TipoNotificacion.MENSAJE_MENTOR && !p.habilitada());
        assertThat(mentorApagadoParaA).isTrue();
        assertThat(mentorApagadoParaB).isFalse(); // B nunca ve el ajuste de A
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: el comando de actualizacion no tiene forma de apuntar a otro usuario "
            + "(sin campo aparte de actorId — el blindaje es de firma, no de runtime)")
    void elComandoNoTieneCampoDeUsuarioObjetivoAparteDelActor() {
        RecordComponent[] componentes = ActualizarPreferenciasCommand.class.getRecordComponents();
        assertThat(componentes).extracting(RecordComponent::getName).containsExactly("actorId", "preferencias");
    }
}
