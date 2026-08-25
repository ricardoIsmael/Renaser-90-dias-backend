package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.in.rocasemanal.CerrarSemanaUseCase.CerrarSemanaCommand;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CrearPlanSemanalUseCase.CrearPlanSemanalCommand;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CrearPlanSemanalUseCase.ItemRocaSemanal;
import com.renaser.os.rocks.application.ports.in.rocasemanal.EditarDentroDe48hUseCase.EditarRocaSemanalCommand;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.application.ports.out.rocasemanal.LoadRocaSemanalPort;
import com.renaser.os.rocks.application.ports.out.rocasemanal.SaveRocaSemanalPort;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanalId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RocaSemanalServiceTest {

    // domingo 2026-08-23 13:00 UTC: ventana semanal EN_PLAZO
    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-23T13:00:00Z"));

    @Mock
    private LoadRocaMaestraPort loadRocaMaestraPort;
    @Mock
    private LoadRocaSemanalPort loadRocaSemanalPort;
    @Mock
    private SaveRocaSemanalPort saveRocaSemanalPort;
    @Mock
    private ConsultarProgresoParticipanteRocksPort progresoPort;

    private RocaSemanalService service;
    private UserId actorId;

    @BeforeEach
    void setUp() {
        service = new RocaSemanalService(loadRocaMaestraPort, loadRocaSemanalPort, saveRocaSemanalPort, progresoPort,
                CLOCK);
        actorId = UserId.of(UUID.randomUUID());
    }

    private static ProgresoParticipanteRocks progreso(RolParticipante rol, boolean suspendido) {
        // diaPrograma=20 debe ser consistente con fechaInicio respecto de CLOCK ("hoy" = 2026-08-23):
        // dia 1 = fechaInicio, asi que fechaInicio = hoy - 19 dias.
        return new ProgresoParticipanteRocks(20, LocalDate.of(2026, 8, 4), ZoneOffset.UTC, rol, suspendido);
    }

    private List<RocaMaestra> tresMaestras() {
        return List.of(
                RocaMaestra.rehydrate(RocaMaestraId.newId(), actorId, EjeObjetivo.CUERPO, "obj cuerpo", Instant.now()),
                RocaMaestra.rehydrate(RocaMaestraId.newId(), actorId, EjeObjetivo.TRABAJO, "obj trabajo", Instant.now()),
                RocaMaestra.rehydrate(RocaMaestraId.newId(), actorId, EjeObjetivo.RELACIONES, "obj relaciones",
                        Instant.now()));
    }

    private static ItemRocaSemanal item(EjeObjetivo eje) {
        return new ItemRocaSemanal(eje, "titulo", "a1", "a2", "a3", null, null, null);
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: rol sin permiso (no TRAINEE) -> NotAuthorizedException")
    void rolSinPermisoRechazado() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.MENTOR, false)));

        var command = new CrearPlanSemanalCommand(actorId,
                List.of(item(EjeObjetivo.CUERPO), item(EjeObjetivo.TRABAJO), item(EjeObjetivo.RELACIONES)));
        assertThatThrownBy(() -> service.crear(command)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: actor SUSPENDIDO -> NotAuthorizedException")
    void actorSuspendidoRechazado() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, true)));

        var command = new CrearPlanSemanalCommand(actorId,
                List.of(item(EjeObjetivo.CUERPO), item(EjeObjetivo.TRABAJO), item(EjeObjetivo.RELACIONES)));
        assertThatThrownBy(() -> service.crear(command)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("sin las 3 rocas maestras completas -> ROCKS_LOCKED (403)")
    void sinRocasMaestrasCompletasEsRocksLocked() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        when(loadRocaMaestraPort.deParticipante(actorId)).thenReturn(List.of(tresMaestras().get(0)));

        var command = new CrearPlanSemanalCommand(actorId,
                List.of(item(EjeObjetivo.CUERPO), item(EjeObjetivo.TRABAJO), item(EjeObjetivo.RELACIONES)));
        assertThatThrownBy(() -> service.crear(command)).isInstanceOf(NotAuthorizedException.class)
                .hasMessageContaining("ROCKS_LOCKED");
    }

    @Test
    void yaPlanificadaLaSemanaEsConflicto() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        List<RocaMaestra> maestras = tresMaestras();
        when(loadRocaMaestraPort.deParticipante(actorId)).thenReturn(maestras);
        when(loadRocaSemanalPort.deParticipanteYSemana(anyList(), anyInt()))
                .thenReturn(List.of(RocaSemanal.planificar(tresMaestras().get(0).id(), 2, "x",
                        List.of(new com.renaser.os.rocks.domain.model.rocasemanal.AccionCritica(1, "a"),
                                new com.renaser.os.rocks.domain.model.rocasemanal.AccionCritica(2, "b"),
                                new com.renaser.os.rocks.domain.model.rocasemanal.AccionCritica(3, "c")),
                        null, null, null, CLOCK)));

        var command = new CrearPlanSemanalCommand(actorId,
                List.of(item(EjeObjetivo.CUERPO), item(EjeObjetivo.TRABAJO), item(EjeObjetivo.RELACIONES)));
        assertThatThrownBy(() -> service.crear(command)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ALREADY_PLANNED");
    }

    @Test
    void creaLasTresRocasSemanalesUnaPorEje() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        when(loadRocaMaestraPort.deParticipante(actorId)).thenReturn(tresMaestras());
        when(loadRocaSemanalPort.deParticipanteYSemana(anyList(), anyInt())).thenReturn(List.of());
        when(saveRocaSemanalPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var command = new CrearPlanSemanalCommand(actorId,
                List.of(item(EjeObjetivo.CUERPO), item(EjeObjetivo.TRABAJO), item(EjeObjetivo.RELACIONES)));
        var creadas = service.crear(command);

        assertThat(creadas).hasSize(3);
    }

    @Test
    void editarFueraDeLaVentanaEsRechazado() {
        FixedClock clockCierre = FixedClock.at(Instant.parse("2026-08-25T10:00:00Z")); // martes, ventana cerrada
        service = new RocaSemanalService(loadRocaMaestraPort, loadRocaSemanalPort, saveRocaSemanalPort, progresoPort,
                clockCierre);
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        RocaMaestraId maestraId = tresMaestras().get(0).id();
        RocaSemanal existente = RocaSemanal.planificar(maestraId, 2, "T",
                List.of(new com.renaser.os.rocks.domain.model.rocasemanal.AccionCritica(1, "a"),
                        new com.renaser.os.rocks.domain.model.rocasemanal.AccionCritica(2, "b"),
                        new com.renaser.os.rocks.domain.model.rocasemanal.AccionCritica(3, "c")),
                null, null, null, FixedClock.at(Instant.parse("2026-08-18T13:00:00Z")));
        when(loadRocaSemanalPort.byId(existente.id())).thenReturn(Optional.of(existente));
        when(loadRocaMaestraPort.deParticipante(actorId)).thenReturn(
                List.of(RocaMaestra.rehydrate(maestraId, actorId, EjeObjetivo.CUERPO, "obj", Instant.now())));

        var command = new EditarRocaSemanalCommand(actorId, existente.id(), "nuevo", null, null, null, null);
        assertThatThrownBy(() -> service.editar(command)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void cerrarEsIdempotenteYSobreescribe() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        RocaMaestraId maestraId = tresMaestras().get(0).id();
        RocaSemanal existente = RocaSemanal.planificar(maestraId, 2, "T",
                List.of(new com.renaser.os.rocks.domain.model.rocasemanal.AccionCritica(1, "a"),
                        new com.renaser.os.rocks.domain.model.rocasemanal.AccionCritica(2, "b"),
                        new com.renaser.os.rocks.domain.model.rocasemanal.AccionCritica(3, "c")),
                null, null, null, CLOCK);
        when(loadRocaSemanalPort.byId(existente.id())).thenReturn(Optional.of(existente));
        when(loadRocaMaestraPort.deParticipante(actorId)).thenReturn(
                List.of(RocaMaestra.rehydrate(maestraId, actorId, EjeObjetivo.CUERPO, "obj", Instant.now())));
        when(saveRocaSemanalPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.cerrar(new CerrarSemanaCommand(actorId, existente.id(), 7, "bloqueo", "correccion"));
        var resultado = service.cerrar(new CerrarSemanaCommand(actorId, existente.id(), 9, "bloqueo2", "correccion2"));

        assertThat(resultado.autoevaluacionFin()).isEqualTo(9);
        assertThat(resultado.bloqueoPrincipal()).isEqualTo("bloqueo2");
    }
}
