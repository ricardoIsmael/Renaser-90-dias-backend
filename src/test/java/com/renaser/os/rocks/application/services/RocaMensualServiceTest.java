package com.renaser.os.rocks.application.services;

import com.renaser.os.shared.GuardDeRol;
import com.renaser.os.rocks.application.ports.in.rocamensual.DefinirRocaMensualUseCase.DefinirRocaMensualCommand;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.application.ports.out.rocamensual.GuardarRocaMensualPort;
import com.renaser.os.rocks.application.ports.out.rocamensual.LoadRocaMensualPort;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.MetaCuantitativa;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensual;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensualId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.ConstraintViolationException;
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
class RocaMensualServiceTest {

    private static final Instant AYER = Instant.parse("2026-09-06T10:00:00Z");
    private static final Instant AHORA = Instant.parse("2026-09-07T10:00:00Z");
    private static final Clock CLOCK = FixedClock.at(AHORA);
    private static final UUID ID_GENERADO = UUID.fromString("00000000-0000-4000-8000-00000000000c");
    private static final int MES = 2;

    @Mock
    private LoadRocaMensualPort loadRocaMensualPort;
    @Mock
    private GuardarRocaMensualPort guardarRocaMensualPort;
    @Mock
    private LoadRocaMaestraPort loadRocaMaestraPort;
    @Mock
    private ConsultarProgresoParticipanteRocksPort progresoPort;
    @Mock
    private IdGenerator idGenerator;

    private RocaMensualService service;

    @BeforeEach
    void setUp() {
        service = new RocaMensualService(loadRocaMensualPort, guardarRocaMensualPort, loadRocaMaestraPort,
                progresoPort, CLOCK, idGenerator);
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
        lenient().when(guardarRocaMensualPort.guardar(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static UserId actor() {
        return UserId.of(UUID.randomUUID());
    }

    private static ProgresoParticipanteRocks progreso(RolParticipante rol, boolean suspendido) {
        return new ProgresoParticipanteRocks(35, LocalDate.of(2026, 8, 1), ZoneOffset.UTC, rol, suspendido, false);
    }

    /** Igual que {@link #progreso} pero con el programa ANDANDO: el caso de E-169. */
    private static ProgresoParticipanteRocks progresoActivado(RolParticipante rol, boolean suspendido) {
        return new ProgresoParticipanteRocks(35, LocalDate.of(2026, 8, 1), ZoneOffset.UTC, rol, suspendido, true);
    }

    private UserId traineeActivo() {
        UserId id = actor();
        when(progresoPort.deParticipante(id)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        return id;
    }

    /** El objetivo de 90 dias del que cuelga el tramo mensual. Sin esto no hay nada que definir. */
    private RocaMaestraId maestraDe(UserId actorId) {
        RocaMaestraId maestraId = RocaMaestraId.of(UUID.randomUUID());
        when(loadRocaMaestraPort.deParticipanteYEje(actorId, EjeObjetivo.TRABAJO)).thenReturn(Optional.of(
                RocaMaestra.definir(maestraId, actorId, EjeObjetivo.TRABAJO, "Facturar 30.000 USD",
                        MetaCuantitativa.nueva(new BigDecimal("30000"), "USD"), AYER)));
        return maestraId;
    }

    private static DefinirRocaMensualCommand comando(UserId actorId) {
        return new DefinirRocaMensualCommand(actorId, EjeObjetivo.TRABAJO, MES, "Facturar 10.000 USD este mes",
                new BigDecimal("10000"), new BigDecimal("6500"), "USD");
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: rol sin permiso (no TRAINEE) -> NotAuthorizedException")
    void rolSinPermisoRechazado() {
        UserId id = actor();
        when(progresoPort.deParticipante(id)).thenReturn(Optional.of(progreso(RolParticipante.MENTOR, false)));

        assertThatThrownBy(() -> service.misRocasMensuales(id)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: actor SUSPENDIDO -> NotAuthorizedException aunque el rol sea correcto")
    void actorSuspendidoRechazado() {
        UserId id = actor();
        when(progresoPort.deParticipante(id)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service.misRocasMensuales(id)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void participanteInexistenteEs404() {
        UserId id = actor();
        when(progresoPort.deParticipante(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.misRocasMensuales(id)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void traineeActivoRecibeSusRocasMensuales() {
        UserId id = traineeActivo();
        RocaMensual mensual = RocaMensual.definir(RocaMensualId.of(UUID.randomUUID()),
                RocaMaestraId.of(UUID.randomUUID()), 1, "Entrenar tres veces por semana", null, AYER);
        when(loadRocaMensualPort.deParticipante(id)).thenReturn(List.of(mensual));

        assertThat(service.misRocasMensuales(id)).containsExactly(mensual);
    }

    // ---------------------------------------------------------------------------------------
    // definir(): crear o corregir el tramo del mes
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("CLAUDE.MD §0.3: un rol sin permiso tampoco puede definir el tramo mensual")
    void definirRechazaRolSinPermiso() {
        UserId id = actor();
        when(progresoPort.deParticipante(id)).thenReturn(Optional.of(progreso(RolParticipante.ADMIN, false)));

        assertThatThrownBy(() -> service.definir(comando(id))).isInstanceOf(NotAuthorizedException.class);
        verify(guardarRocaMensualPort, never()).guardar(any());
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: un aprendiz SUSPENDIDO no puede definir el tramo mensual")
    void definirRechazaSuspendido() {
        UserId id = actor();
        when(progresoPort.deParticipante(id)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service.definir(comando(id))).isInstanceOf(NotAuthorizedException.class);
        verify(guardarRocaMensualPort, never()).guardar(any());
    }

    /**
     * Un tramo mensual sin objetivo mayor no significa nada: "facturar 10.000 este mes" se entiende
     * porque la meta de los 90 dias son 30.000. Por eso falta la maestra es un 404 explicito y no
     * un alta silenciosa de una maestra vacia.
     */
    @Test
    @DisplayName("sin objetivo de 90 dias en ese eje no hay tramo mensual que definir -> 404")
    void definirExigeLaRocaMaestraDelEje() {
        UserId id = traineeActivo();
        when(loadRocaMaestraPort.deParticipanteYEje(id, EjeObjetivo.TRABAJO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.definir(comando(id)))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("objetivo de 90 dias");
        verify(guardarRocaMensualPort, never()).guardar(any());
    }

    @Test
    @DisplayName("si el mes no tenia tramo, se crea colgado de la maestra y con la identidad del generador")
    void definirCreaCuandoNoExiste() {
        UserId id = traineeActivo();
        RocaMaestraId maestraId = maestraDe(id);
        when(loadRocaMensualPort.deMaestraYMes(maestraId, MES)).thenReturn(Optional.empty());

        RocaMensual guardada = service.definir(comando(id));

        assertThat(guardada.id().value()).isEqualTo(ID_GENERADO);
        assertThat(guardada.rocaMaestraId()).isEqualTo(maestraId);
        assertThat(guardada.numeroMes()).isEqualTo(MES);
        assertThat(guardada.creadoEn()).isEqualTo(AHORA);
        assertThat(guardada.diaDeCierre()).isEqualTo(60);
        assertThat(guardada.meta().porcentaje()).isEqualTo(65);
    }

    @Test
    @DisplayName("si el mes ya tenia tramo, se corrige el mismo: no se crea un segundo")
    void definirCorrigeElExistente() {
        UserId id = traineeActivo();
        RocaMaestraId maestraId = maestraDe(id);
        RocaMensualId yaExistente = RocaMensualId.of(UUID.randomUUID());
        when(loadRocaMensualPort.deMaestraYMes(maestraId, MES)).thenReturn(Optional.of(
                RocaMensual.definir(yaExistente, maestraId, MES, "El tramo viejo",
                        MetaCuantitativa.nueva(new BigDecimal("5000"), "USD"), AYER)));

        RocaMensual guardada = service.definir(comando(id));

        assertThat(guardada.id()).as("mismo tramo, corregido").isEqualTo(yaExistente);
        assertThat(guardada.creadoEn()).as("la fecha de creacion no se mueve").isEqualTo(AYER);
        assertThat(guardada.actualizadoEn()).isEqualTo(AHORA);
        assertThat(guardada.titulo()).isEqualTo("Facturar 10.000 USD este mes");
        verify(idGenerator, never()).newId();
    }

    @Test
    @DisplayName("un tramo sin numeros se guarda sin meta, no con una meta en cero")
    void definirSinMetaGuardaSinMeta() {
        UserId id = traineeActivo();
        RocaMaestraId maestraId = maestraDe(id);
        when(loadRocaMensualPort.deMaestraYMes(maestraId, 1)).thenReturn(Optional.empty());

        service.definir(new DefinirRocaMensualCommand(id, EjeObjetivo.TRABAJO, 1,
                "Cerrar la propuesta que vengo pateando", null, null, null));

        ArgumentCaptor<RocaMensual> guardada = ArgumentCaptor.forClass(RocaMensual.class);
        verify(guardarRocaMensualPort).guardar(guardada.capture());
        assertThat(guardada.getValue().tieneMeta()).isFalse();
    }

    @Test
    @DisplayName("media meta no vale: sin unidad el numero no se puede ni mostrar")
    void elComandoRechazaUnaMetaIncompleta() {
        UserId id = actor();

        assertThatThrownBy(() -> new DefinirRocaMensualCommand(id, EjeObjetivo.TRABAJO, MES, "Facturar",
                new BigDecimal("10000"), new BigDecimal("0"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("el comando no acepta un mes fuera de los tres del programa")
    void elComandoRechazaUnMesFueraDeRango() {
        UserId id = actor();

        assertThatThrownBy(() -> new DefinirRocaMensualCommand(id, EjeObjetivo.TRABAJO, 4, "Facturar",
                null, null, null))
                .isInstanceOf(ConstraintViolationException.class);
    }

    /**
     * E-169: un MENTOR que activo su seguimiento personal opera su programa como cualquiera.
     *
     * <p>Es el reverso exacto del caso de rechazo, y se construye sobre su mismo fixture para que
     * la unica diferencia sea el dato que importa. Antes de esto,
     * {@code POST /api/v1/mentor/activate-tracking} inscribia al staff y despues el guard lo
     * echaba: la inscripcion estaba construida y el uso prohibido.
     */
    @Test
    @DisplayName("E-169: un MENTOR con su programa ACTIVADO ya no lo rechaza el guard de rol")
    void staffConProgramaActivadoOperaSuPrograma() {
        UserId id = actor();
        when(progresoPort.deParticipante(id)).thenReturn(Optional.of(progresoActivado(RolParticipante.MENTOR, false)));

        GuardDeRol.noRechaza(() -> service.misRocasMensuales(id), "Solo un aprendiz opera sus propias rocas");
    }
}
