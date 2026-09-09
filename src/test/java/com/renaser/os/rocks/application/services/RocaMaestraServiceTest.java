package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.in.rocamaestra.DefinirRocaMaestraUseCase.DefinirRocaMaestraCommand;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocamaestra.GuardarRocaMaestraPort;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.MetaCuantitativa;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RocaMaestraServiceTest {

    private static final Instant AYER = Instant.parse("2026-09-06T10:00:00Z");
    private static final Instant AHORA = Instant.parse("2026-09-07T10:00:00Z");
    private static final Clock CLOCK = FixedClock.at(AHORA);
    private static final UUID ID_GENERADO = UUID.fromString("00000000-0000-4000-8000-000000000009");

    @Mock
    private LoadRocaMaestraPort loadRocaMaestraPort;
    @Mock
    private GuardarRocaMaestraPort guardarRocaMaestraPort;
    @Mock
    private ConsultarProgresoParticipanteRocksPort progresoPort;
    @Mock
    private IdGenerator idGenerator;

    private RocaMaestraService service;

    @BeforeEach
    void setUp() {
        service = new RocaMaestraService(loadRocaMaestraPort, guardarRocaMaestraPort, progresoPort, CLOCK,
                idGenerator);
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
        lenient().when(guardarRocaMaestraPort.guardar(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static UserId actor() {
        return UserId.of(UUID.randomUUID());
    }

    private static ProgresoParticipanteRocks progreso(RolParticipante rol, boolean suspendido) {
        return new ProgresoParticipanteRocks(10, LocalDate.of(2026, 8, 1), ZoneOffset.UTC, rol, suspendido);
    }

    private UserId traineeActivo() {
        UserId id = actor();
        when(progresoPort.deParticipante(id)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        return id;
    }

    private static DefinirRocaMaestraCommand comando(UserId actor) {
        return new DefinirRocaMaestraCommand(actor, EjeObjetivo.TRABAJO,
                "Facturar 30.000 USD en contratos high-ticket", new BigDecimal("30000"), new BigDecimal("19500"),
                "USD", null);
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: rol sin permiso (no TRAINEE) -> NotAuthorizedException")
    void rolSinPermisoRechazado() {
        UserId id = actor();
        when(progresoPort.deParticipante(id)).thenReturn(Optional.of(progreso(RolParticipante.MENTOR, false)));

        assertThatThrownBy(() -> service.misRocasMaestras(id)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: actor SUSPENDIDO -> NotAuthorizedException aunque el rol sea correcto")
    void actorSuspendidoRechazado() {
        UserId id = actor();
        when(progresoPort.deParticipante(id)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service.misRocasMaestras(id)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void participanteInexistenteEs404() {
        UserId id = actor();
        when(progresoPort.deParticipante(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.misRocasMaestras(id)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void traineeActivoRecibeSusRocasMaestras() {
        UserId id = traineeActivo();
        RocaMaestra maestra = RocaMaestra.definir(RocaMaestraId.of(UUID.randomUUID()), id, EjeObjetivo.CUERPO,
                "objetivo", null, AYER);
        when(loadRocaMaestraPort.deParticipante(id)).thenReturn(List.of(maestra));

        assertThat(service.misRocasMaestras(id)).containsExactly(maestra);
    }

    // ---------------------------------------------------------------------------------------
    // definir(): crear o corregir el objetivo de 90 dias
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("CLAUDE.MD §0.3: un rol sin permiso tampoco puede definir el objetivo")
    void definirRechazaRolSinPermiso() {
        UserId id = actor();
        when(progresoPort.deParticipante(id)).thenReturn(Optional.of(progreso(RolParticipante.ADMIN, false)));

        assertThatThrownBy(() -> service.definir(comando(id))).isInstanceOf(NotAuthorizedException.class);
        verify(guardarRocaMaestraPort, never()).guardar(any());
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: un aprendiz SUSPENDIDO no puede definir el objetivo")
    void definirRechazaSuspendido() {
        UserId id = actor();
        when(progresoPort.deParticipante(id)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service.definir(comando(id))).isInstanceOf(NotAuthorizedException.class);
        verify(guardarRocaMaestraPort, never()).guardar(any());
    }

    @Test
    @DisplayName("si el eje no tenia objetivo, se crea con la identidad del generador")
    void definirCreaCuandoNoExiste() {
        UserId id = traineeActivo();
        when(loadRocaMaestraPort.deParticipanteYEje(id, EjeObjetivo.TRABAJO)).thenReturn(Optional.empty());

        RocaMaestra guardada = service.definir(comando(id));

        assertThat(guardada.id().value()).isEqualTo(ID_GENERADO);
        assertThat(guardada.participanteId()).isEqualTo(id);
        assertThat(guardada.eje()).isEqualTo(EjeObjetivo.TRABAJO);
        assertThat(guardada.creadoEn()).isEqualTo(AHORA);
        assertThat(guardada.meta().porcentaje()).isEqualTo(65);
    }

    @Test
    @DisplayName("si el eje ya tenia objetivo, se corrige el mismo: no se crea un segundo")
    void definirCorrigeElExistente() {
        UserId id = traineeActivo();
        RocaMaestraId yaExistente = RocaMaestraId.of(UUID.randomUUID());
        when(loadRocaMaestraPort.deParticipanteYEje(id, EjeObjetivo.TRABAJO)).thenReturn(Optional.of(
                RocaMaestra.definir(yaExistente, id, EjeObjetivo.TRABAJO, "El objetivo viejo",
                        MetaCuantitativa.nueva(new BigDecimal("10000"), "USD"), AYER)));

        RocaMaestra guardada = service.definir(comando(id));

        assertThat(guardada.id()).as("misma roca, corregida").isEqualTo(yaExistente);
        assertThat(guardada.creadoEn()).as("la fecha de creacion no se mueve").isEqualTo(AYER);
        assertThat(guardada.actualizadoEn()).isEqualTo(AHORA);
        assertThat(guardada.objetivo()).isEqualTo("Facturar 30.000 USD en contratos high-ticket");
        verify(idGenerator, never()).newId();
    }

    @Test
    @DisplayName("un objetivo sin numeros se guarda sin meta, no con una meta en cero")
    void definirSinMetaGuardaSinMeta() {
        UserId id = traineeActivo();
        when(loadRocaMaestraPort.deParticipanteYEje(id, EjeObjetivo.RELACIONES)).thenReturn(Optional.empty());

        service.definir(new DefinirRocaMaestraCommand(id, EjeObjetivo.RELACIONES,
                "Recuperar la confianza con mi hijo", null, null, null, null));

        ArgumentCaptor<RocaMaestra> guardada = ArgumentCaptor.forClass(RocaMaestra.class);
        verify(guardarRocaMaestraPort).guardar(guardada.capture());
        assertThat(guardada.getValue().tieneMeta()).isFalse();
    }

    @Test
    @DisplayName("media meta no vale: sin unidad el numero no se puede ni mostrar")
    void elComandoRechazaUnaMetaIncompleta() {
        UserId id = actor();

        assertThatThrownBy(() -> new DefinirRocaMaestraCommand(id, EjeObjetivo.TRABAJO, "Facturar",
                new BigDecimal("30000"), new BigDecimal("0"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
