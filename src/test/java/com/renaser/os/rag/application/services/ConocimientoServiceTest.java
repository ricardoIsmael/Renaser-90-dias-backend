package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.conocimiento.IndexarConocimientoUseCase.IndexarConocimientoCommand;
import com.renaser.os.rag.application.ports.out.conocimiento.SaveChunkConocimientoPort;
import com.renaser.os.rag.application.ports.out.ia.EmbeddingPort;
import com.renaser.os.rag.domain.model.conocimiento.ChunkConocimiento;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConocimientoServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-25T10:00:00Z"));

    @Mock
    private SaveChunkConocimientoPort saveChunkConocimientoPort;
    @Mock
    private EmbeddingPort embeddingPort;
    @Mock
    private UserSummaryFinder userSummaryFinder;

    private ConocimientoService service;
    private UserId adminId;
    private UserId traineeId;

    @BeforeEach
    void setUp() {
        service = new ConocimientoService(saveChunkConocimientoPort, embeddingPort, userSummaryFinder, CLOCK);
        adminId = UserId.of(UUID.randomUUID());
        traineeId = UserId.of(UUID.randomUUID());
    }

    private static List<Float> vector() {
        return Collections.nCopies(ChunkConocimiento.DIMENSION_EMBEDDING, 0.2f);
    }

    @Test
    void unAdminIndexaUnChunk() {
        when(userSummaryFinder.findById(adminId)).thenReturn(Optional.of(
                new UserSummary(adminId, "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));
        when(embeddingPort.generar("contenido")).thenReturn(vector());
        when(saveChunkConocimientoPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var comando = new IndexarConocimientoCommand(adminId, "LECCION", "texto", "doc-1", "leccion-1", "contenido",
                Map.of());
        var resultado = service.indexar(comando);

        assertThat(resultado.id()).isNotNull();
        verify(saveChunkConocimientoPort).save(any());
    }

    @Test
    void unAlchemistTambienPuedeIndexar() {
        when(userSummaryFinder.findById(adminId)).thenReturn(Optional.of(
                new UserSummary(adminId, "Alquimista", null, UserRole.ALCHEMIST, UserStatus.ACTIVE)));
        when(embeddingPort.generar("contenido")).thenReturn(vector());
        when(saveChunkConocimientoPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var comando = new IndexarConocimientoCommand(adminId, "LECCION", null, null, null, "contenido", Map.of());

        assertThat(service.indexar(comando).id()).isNotNull();
    }

    @Test
    void unTraineeNoPuedeIndexar() {
        when(userSummaryFinder.findById(traineeId)).thenReturn(Optional.of(
                new UserSummary(traineeId, "Aprendiz", null, UserRole.TRAINEE, UserStatus.ACTIVE)));

        var comando = new IndexarConocimientoCommand(traineeId, "LECCION", null, null, null, "contenido", Map.of());

        assertThatThrownBy(() -> service.indexar(comando)).isInstanceOf(NotAuthorizedException.class);
        verify(saveChunkConocimientoPort, never()).save(any());
    }

    @Test
    void unAdminSuspendidoNoPuedeIndexar() {
        when(userSummaryFinder.findById(adminId)).thenReturn(Optional.of(
                new UserSummary(adminId, "Admin", null, UserRole.ADMIN, UserStatus.SUSPENDED)));

        var comando = new IndexarConocimientoCommand(adminId, "LECCION", null, null, null, "contenido", Map.of());

        assertThatThrownBy(() -> service.indexar(comando)).isInstanceOf(NotAuthorizedException.class);
        verify(saveChunkConocimientoPort, never()).save(any());
    }

    @Test
    void actorInexistenteLanzaExcepcion() {
        when(userSummaryFinder.findById(adminId)).thenReturn(Optional.empty());

        var comando = new IndexarConocimientoCommand(adminId, "LECCION", null, null, null, "contenido", Map.of());

        assertThatThrownBy(() -> service.indexar(comando)).isInstanceOf(NoSuchElementException.class);
    }
}
