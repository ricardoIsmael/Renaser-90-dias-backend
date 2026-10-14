package com.renaser.os.chat.infrastructure.adapter.in.websocket;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.web.security.SesionDeSoloLectura;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * «Ver como» (D-270): la sesión de solo lectura vence a los 30 minutos aunque Redis la conserve (su inactividad cuenta
 * desde el último pedido). Para el socket del chat, vencida es muerta: deja de recibir.
 */
@ExtendWith(MockitoExtension.class)
class SesionVivaDeSoloLecturaTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");

    @Mock
    private SessionRepository<Session> sesiones;

    private MapSession deSoloLectura(Instant venceEn) {
        MapSession sesion = new MapSession("ver-como");
        sesion.setAttribute(SesionDeSoloLectura.ABIERTA_POR, UUID.randomUUID().toString());
        sesion.setAttribute(SesionDeSoloLectura.VENCE_EN, venceEn.toEpochMilli());
        return sesion;
    }

    @Test
    @DisplayName("vencida: el socket ya no está vivo")
    void vencida() {
        when(sesiones.findById("ver-como")).thenAnswer(i -> deSoloLectura(AHORA.minusSeconds(1)));
        SesionViva sesionViva = new SesionViva(sesiones, FixedClock.at(AHORA));

        assertThat(sesionViva.sigueViva("socket-1", "ver-como")).isFalse();
    }

    @Test
    @DisplayName("dentro de su media hora, sigue viva")
    void vigente() {
        when(sesiones.findById("ver-como")).thenAnswer(i -> deSoloLectura(AHORA.plusSeconds(600)));
        SesionViva sesionViva = new SesionViva(sesiones, FixedClock.at(AHORA));

        assertThat(sesionViva.sigueViva("socket-1", "ver-como")).isTrue();
    }
}
