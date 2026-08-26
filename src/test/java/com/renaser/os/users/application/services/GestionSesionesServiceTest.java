package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GestionSesionesServiceTest {

    @Mock
    private FindByIndexNameSessionRepository<Session> sessionRepository;

    @Test
    void cerrarTodasBorraCadaSesionEncontradaParaElUsuario() {
        UserId usuarioId = UserId.of(UUID.randomUUID());
        Session sesionMovil = new MapSession("id-movil");
        Session sesionWeb = new MapSession("id-web");
        when(sessionRepository.findByPrincipalName(usuarioId.value().toString()))
                .thenReturn(Map.of("id-movil", sesionMovil, "id-web", sesionWeb));

        new GestionSesionesService(sessionRepository).cerrarTodas(usuarioId);

        verify(sessionRepository).deleteById("id-movil");
        verify(sessionRepository).deleteById("id-web");
    }

    @Test
    void cerrarTodasSinSesionesActivasNoBorraNada() {
        UserId usuarioId = UserId.of(UUID.randomUUID());
        when(sessionRepository.findByPrincipalName(usuarioId.value().toString())).thenReturn(Map.of());

        new GestionSesionesService(sessionRepository).cerrarTodas(usuarioId);

        verify(sessionRepository, never()).deleteById(any());
    }
}
