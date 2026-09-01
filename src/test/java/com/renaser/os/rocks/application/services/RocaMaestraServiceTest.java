package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RocaMaestraServiceTest {

    @Mock
    private LoadRocaMaestraPort loadRocaMaestraPort;
    @Mock
    private ConsultarProgresoParticipanteRocksPort progresoPort;

    private RocaMaestraService service;

    private static UserId actor() {
        return UserId.of(UUID.randomUUID());
    }

    private static ProgresoParticipanteRocks progreso(RolParticipante rol, boolean suspendido) {
        return new ProgresoParticipanteRocks(10, LocalDate.of(2026, 8, 1), ZoneOffset.UTC, rol, suspendido);
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: rol sin permiso (no TRAINEE) -> NotAuthorizedException")
    void rolSinPermisoRechazado() {
        service = new RocaMaestraService(loadRocaMaestraPort, progresoPort);
        UserId id = actor();
        when(progresoPort.deParticipante(id)).thenReturn(Optional.of(progreso(RolParticipante.MENTOR, false)));

        assertThatThrownBy(() -> service.misRocasMaestras(id)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: actor SUSPENDIDO -> NotAuthorizedException aunque el rol sea correcto")
    void actorSuspendidoRechazado() {
        service = new RocaMaestraService(loadRocaMaestraPort, progresoPort);
        UserId id = actor();
        when(progresoPort.deParticipante(id)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service.misRocasMaestras(id)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void participanteInexistenteEs404() {
        service = new RocaMaestraService(loadRocaMaestraPort, progresoPort);
        UserId id = actor();
        when(progresoPort.deParticipante(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.misRocasMaestras(id)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void traineeActivoRecibeSusRocasMaestras() {
        service = new RocaMaestraService(loadRocaMaestraPort, progresoPort);
        UserId id = actor();
        when(progresoPort.deParticipante(id)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        RocaMaestra maestra = RocaMaestra.rehydrate(RocaMaestraId.of(UUID.randomUUID()), id,
                EjeObjetivo.CUERPO, "objetivo", Instant.now());
        when(loadRocaMaestraPort.deParticipante(id)).thenReturn(List.of(maestra));

        var resultado = service.misRocasMaestras(id);

        assertThat(resultado).containsExactly(maestra);
    }
}
