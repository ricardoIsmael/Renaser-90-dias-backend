package com.renaser.os.rocks.application.services;

import com.renaser.os.shared.GuardDeRol;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CerrarSemanaUseCase.CerrarSemanaCommand;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CrearPlanSemanalUseCase.CrearPlanSemanalCommand;
import jakarta.validation.ConstraintViolationException;
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
import com.renaser.os.shared.domain.IdGenerator;
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
import static org.assertj.core.api.Assertions.assertThatCode;
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
    /** Id fijo que devuelve el IdGenerator mockeado, mismo espiritu que el FixedClock de arriba. */
    private static final UUID ID_GENERADO = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private LoadRocaMaestraPort loadRocaMaestraPort;
    @Mock
    private LoadRocaSemanalPort loadRocaSemanalPort;
    @Mock
    private SaveRocaSemanalPort saveRocaSemanalPort;
    @Mock
    private ConsultarProgresoParticipanteRocksPort progresoPort;
    @Mock
    private IdGenerator idGenerator;

    private RocaSemanalService service;
    private UserId actorId;

    @BeforeEach
    void setUp() {
        service = new RocaSemanalService(loadRocaMaestraPort, loadRocaSemanalPort, saveRocaSemanalPort, progresoPort,
                CLOCK, idGenerator);
        actorId = UserId.of(UUID.randomUUID());
        // lenient: no todos los casos llegan a generar un id (varios cortan antes, en autorizacion).
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
    }

    private static ProgresoParticipanteRocks progreso(RolParticipante rol, boolean suspendido) {
        // diaPrograma=20 debe ser consistente con fechaInicio respecto de CLOCK ("hoy" = 2026-08-23):
        // dia 1 = fechaInicio, asi que fechaInicio = hoy - 19 dias.
        return new ProgresoParticipanteRocks(20, LocalDate.of(2026, 8, 4), ZoneOffset.UTC, rol, suspendido, false);
    }

    /** Igual que {@link #progreso} pero con el programa ANDANDO: el caso de E-169. */
    private static ProgresoParticipanteRocks progresoActivado(RolParticipante rol, boolean suspendido) {
        // diaPrograma=20 debe ser consistente con fechaInicio respecto de CLOCK ("hoy" = 2026-08-23):
        // dia 1 = fechaInicio, asi que fechaInicio = hoy - 19 dias.
        return new ProgresoParticipanteRocks(20, LocalDate.of(2026, 8, 4), ZoneOffset.UTC, rol, suspendido, true);
    }

    private List<RocaMaestra> tresMaestras() {
        // meta = null: la roca semanal solo necesita la identidad de su maestra, no su parte
        // medible (V35). Dejarlas cualitativas mantiene el fixture en lo minimo que hace falta.
        Instant ahora = Instant.now();
        return List.of(
                RocaMaestra.rehydrate(RocaMaestraId.of(UUID.randomUUID()), actorId, EjeObjetivo.CUERPO,
                        "obj cuerpo", null, ahora, ahora),
                RocaMaestra.rehydrate(RocaMaestraId.of(UUID.randomUUID()), actorId, EjeObjetivo.TRABAJO,
                        "obj trabajo", null, ahora, ahora),
                RocaMaestra.rehydrate(RocaMaestraId.of(UUID.randomUUID()), actorId, EjeObjetivo.RELACIONES,
                        "obj relaciones", null, ahora, ahora));
    }

    private static ItemRocaSemanal item(EjeObjetivo eje) {
        return new ItemRocaSemanal(eje, "titulo", null, null, null);
    }

    @Test
    @DisplayName("alcanza con UN eje: la semana se abre con el principal y los otros dos se suman despues")
    void conUnSoloEjeSeAcepta() {
        assertThatCode(() -> new CrearPlanSemanalCommand(actorId, List.of(item(EjeObjetivo.CUERPO))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("...pero la lista vacia no: una semana sin ningun objetivo no es una semana armada")
    void sinNingunEjeSeRechaza() {
        assertThatThrownBy(() -> new CrearPlanSemanalCommand(actorId, List.of()))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("el maximo sigue en tres: no existe un cuarto eje")
    void masDeTresSeRechaza() {
        assertThatThrownBy(() -> new CrearPlanSemanalCommand(actorId,
                List.of(item(EjeObjetivo.CUERPO), item(EjeObjetivo.TRABAJO), item(EjeObjetivo.RELACIONES),
                        item(EjeObjetivo.CUERPO))))
                .isInstanceOf(ConstraintViolationException.class);
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

    /**
     * > <b>Corregido el 2026-09-23.</b> Este test se llamaba {@code yaPlanificadaLaSemanaEsConflicto}
     * > y daba por buena la regla vieja: con UN objetivo en la semana, pedir los tres era conflicto.
     * > Esa regla es la que dejaba al dueno mirando Negocio y viendo el objetivo de Cuerpo, sin
     * > poder planificar el suyo hasta el domingo siguiente.
     * >
     * > Ademas su fixture mentia: llamaba a {@code tresMaestras()} DOS veces y cada llamada sortea
     * > UUIDs nuevos, asi que la roca "existente" colgaba de una maestra que no estaba en la lista.
     * > Con el codigo viejo daba igual —solo miraba {@code isEmpty()}— y por eso paso inadvertido.
     * > Es el caso exacto de `.claude/rules/03-pruebas.md`: el bug se escondia en el fixture.
     */
    @Test
    @DisplayName("pedir SOLO un eje que ya tiene objetivo esta semana sigue siendo conflicto")
    void pedirSoloUnEjeYaPlanificadoEsConflicto() {
        List<RocaMaestra> maestras = tresMaestras();
        conSemanaQueYaTiene(maestras, EjeObjetivo.CUERPO);

        var command = new CrearPlanSemanalCommand(actorId, List.of(item(EjeObjetivo.CUERPO)));
        assertThatThrownBy(() -> service.crear(command)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ALREADY_PLANNED");
    }

    @Test
    @DisplayName("suma los ejes que faltan y no pisa el que ya estaba")
    void sumaLosEjesQueFaltan() {
        List<RocaMaestra> maestras = tresMaestras();
        conSemanaQueYaTiene(maestras, EjeObjetivo.CUERPO);
        when(saveRocaSemanalPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var creadas = service.crear(new CrearPlanSemanalCommand(actorId,
                List.of(item(EjeObjetivo.CUERPO), item(EjeObjetivo.TRABAJO), item(EjeObjetivo.RELACIONES))));

        // Solo las dos que faltaban: Cuerpo ya tenia la suya y no se toca desde aca.
        assertThat(creadas).hasSize(2);
        RocaMaestraId trabajo = maestras.get(1).id();
        RocaMaestraId relaciones = maestras.get(2).id();
        assertThat(creadas).extracting(RocaSemanal::rocaMaestraId)
                .containsExactlyInAnyOrder(trabajo, relaciones);
    }

    /** Siembra una semana que ya tiene objetivo en {@code ejeExistente}, con maestras coherentes. */
    private void conSemanaQueYaTiene(List<RocaMaestra> maestras, EjeObjetivo ejeExistente) {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        when(loadRocaMaestraPort.deParticipante(actorId)).thenReturn(maestras);
        RocaMaestraId maestraDelEje = maestras.stream().filter(m -> m.eje() == ejeExistente).findFirst()
                .orElseThrow().id();
        when(loadRocaSemanalPort.deParticipanteYSemana(anyList(), anyInt()))
                .thenReturn(List.of(RocaSemanal.planificar(RocaSemanalId.of(UUID.randomUUID()),
                        maestraDelEje, 2, "ya estaba", null, null, null, CLOCK)));
    }

    @Test
    @DisplayName("E-205: el servicio CREA la semana con un solo eje, no solo la valida el comando")
    void creaLaSemanaConUnSoloEje() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        when(loadRocaMaestraPort.deParticipante(actorId)).thenReturn(tresMaestras());
        when(loadRocaSemanalPort.deParticipanteYSemana(anyList(), anyInt())).thenReturn(List.of());
        when(saveRocaSemanalPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var creadas = service.crear(new CrearPlanSemanalCommand(actorId, List.of(item(EjeObjetivo.CUERPO))));

        assertThat(creadas).hasSize(1);
        assertThat(creadas.get(0).titulo()).isEqualTo("titulo");
    }

    @Test
    @DisplayName("dos objetivos del mismo eje en la misma semana no: uno se perderia")
    void dosDelMismoEjeSeRechaza() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        when(loadRocaMaestraPort.deParticipante(actorId)).thenReturn(tresMaestras());

        var command = new CrearPlanSemanalCommand(actorId,
                List.of(item(EjeObjetivo.CUERPO), item(EjeObjetivo.CUERPO)));
        assertThatThrownBy(() -> service.crear(command)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mismo eje");
    }

    @Test
    @DisplayName("alcanza con el objetivo: la semana se guarda con titulo y nada mas")
    void soloConElObjetivoSeGuarda() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        when(loadRocaMaestraPort.deParticipante(actorId)).thenReturn(tresMaestras());
        when(loadRocaSemanalPort.deParticipanteYSemana(anyList(), anyInt())).thenReturn(List.of());
        when(saveRocaSemanalPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var soloObjetivo = new ItemRocaSemanal(EjeObjetivo.CUERPO, "Bajar a 81,6 kg", null, null, null);
        var creadas = service.crear(new CrearPlanSemanalCommand(actorId, List.of(soloObjetivo)));

        assertThat(creadas).hasSize(1);
        assertThat(creadas.get(0).titulo()).isEqualTo("Bajar a 81,6 kg");
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
                clockCierre, idGenerator);
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        RocaMaestraId maestraId = tresMaestras().get(0).id();
        RocaSemanal existente = RocaSemanal.planificar(RocaSemanalId.of(UUID.randomUUID()), maestraId, 2, "T",
                null, null, null, FixedClock.at(Instant.parse("2026-08-18T13:00:00Z")));
        when(loadRocaSemanalPort.byId(existente.id())).thenReturn(Optional.of(existente));
        when(loadRocaMaestraPort.deParticipante(actorId)).thenReturn(
                List.of(RocaMaestra.rehydrate(maestraId, actorId, EjeObjetivo.CUERPO, "obj", null,
                        Instant.now(), Instant.now())));

        var command = new EditarRocaSemanalCommand(actorId, existente.id(), "nuevo", null, null, null);
        assertThatThrownBy(() -> service.editar(command)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void cerrarEsIdempotenteYSobreescribe() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, false)));
        RocaMaestraId maestraId = tresMaestras().get(0).id();
        RocaSemanal existente = RocaSemanal.planificar(RocaSemanalId.of(UUID.randomUUID()), maestraId, 2, "T", null, null, null, CLOCK);
        when(loadRocaSemanalPort.byId(existente.id())).thenReturn(Optional.of(existente));
        when(loadRocaMaestraPort.deParticipante(actorId)).thenReturn(
                List.of(RocaMaestra.rehydrate(maestraId, actorId, EjeObjetivo.CUERPO, "obj", null,
                        Instant.now(), Instant.now())));
        when(saveRocaSemanalPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.cerrar(new CerrarSemanaCommand(actorId, existente.id(), 7, "bloqueo", "correccion"));
        var resultado = service.cerrar(new CerrarSemanaCommand(actorId, existente.id(), 9, "bloqueo2", "correccion2"));

        assertThat(resultado.autoevaluacionFin()).isEqualTo(9);
        assertThat(resultado.bloqueoPrincipal()).isEqualTo("bloqueo2");
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
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progresoActivado(RolParticipante.MENTOR, false)));

        var command = new CrearPlanSemanalCommand(actorId,
                List.of(item(EjeObjetivo.CUERPO), item(EjeObjetivo.TRABAJO), item(EjeObjetivo.RELACIONES)));
        GuardDeRol.noRechaza(() -> service.crear(command), "Solo un aprendiz opera sus propias rocas");
    }
}
