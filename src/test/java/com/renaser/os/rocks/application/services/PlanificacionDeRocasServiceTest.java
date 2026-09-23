package com.renaser.os.rocks.application.services;

import com.renaser.os.community.api.PublicarEnMuroPort;
import com.renaser.os.evidence.api.RegistrarEvidenciaPort;
import com.renaser.os.points.api.AjustarPuntosPort;
import com.renaser.os.rocks.api.PlanificacionDeRocasPort;
import com.renaser.os.rocks.api.PlanificacionDeRocasPort.AccionDelDia;
import com.renaser.os.rocks.api.PlanificacionDeRocasPort.MotivoRechazo;
import com.renaser.os.rocks.api.PlanificacionDeRocasPort.ObjetivoDeLaSemana;
import com.renaser.os.rocks.api.PlanificacionDeRocasPort.ResultadoPlanificacion;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CrearPlanDiarioUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CrearPlanDiarioUseCase.CrearPlanDiarioCommand;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CrearPlanDiarioUseCase.ItemRocaDiaria;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CrearPlanSemanalUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CrearPlanSemanalUseCase.CrearPlanSemanalCommand;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CrearPlanSemanalUseCase.ItemRocaSemanal;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.rocks.application.ports.out.rocadiaria.SaveRocaDiariaPort;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.application.ports.out.rocasemanal.LoadRocaSemanalPort;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanalId;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PlanificacionDeRocasService}: delega en los casos de uso de la app y traduce su rechazo a
 * {@link MotivoRechazo}, sin agregar ninguna regla.
 */
class PlanificacionDeRocasServiceTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final LocalDate FECHA = LocalDate.of(2026, 8, 26);
    private static final AccionDelDia CAMINAR = new AccionDelDia("CUERPO", 1, "Caminar", 5, false,
            LocalTime.of(6, 0), LocalTime.of(6, 30));

    private final CrearPlanDiarioUseCase crearPlanDiario = mock(CrearPlanDiarioUseCase.class);
    private final CrearPlanSemanalUseCase crearPlanSemanal = mock(CrearPlanSemanalUseCase.class);
    private final PlanificacionDeRocasService service = new PlanificacionDeRocasService(crearPlanDiario,
            crearPlanSemanal);

    @Test
    @DisplayName("los ejes de rocks.api son exactamente los de EjeObjetivo, en su orden")
    void ejesCoincidenConElEnum() {
        assertThat(PlanificacionDeRocasPort.EJES)
                .isEqualTo(Arrays.stream(EjeObjetivo.values()).map(Enum::name).toList());
    }

    @Test
    @DisplayName("el plan del dia llega al caso de uso con los mismos datos, sin acciones internas ni descripcion")
    void delegaElPlanDelDia() {
        when(crearPlanDiario.crear(any())).thenReturn(List.of());

        service.crearPlanDelDia(APRENDIZ, FECHA, List.of(CAMINAR));

        verify(crearPlanDiario).crear(new CrearPlanDiarioCommand(APRENDIZ, FECHA, List.of(new ItemRocaDiaria(
                EjeObjetivo.CUERPO, 1, "Caminar", null, 5, false, LocalTime.of(6, 0), LocalTime.of(6, 30), List.of()))));
    }

    @Test
    @DisplayName("la semana llega al caso de uso con los mismos datos y devuelve cuantos se guardaron")
    void delegaLaSemana() {
        when(crearPlanSemanal.crear(any())).thenReturn(List.of());

        ResultadoPlanificacion resultado = service.crearPlanDeLaSemana(APRENDIZ,
                List.of(new ObjetivoDeLaSemana("TRABAJO", "Vender", "Poco tiempo", null)));

        assertThat(resultado).isEqualTo(new ResultadoPlanificacion.Creado(0));
        verify(crearPlanSemanal).crear(new CrearPlanSemanalCommand(APRENDIZ,
                List.of(new ItemRocaSemanal(EjeObjetivo.TRABAJO, "Vender", "Poco tiempo", null, null))));
    }

    @Test
    @DisplayName("cada codigo del caso de uso se traduce a su motivo, sin que otro modulo lea el mensaje")
    void traduceLosRechazos() {
        assertThat(rechazoAnte(new IllegalArgumentException("INVALID_DATE: fuera de rango")))
                .isEqualTo(MotivoRechazo.FECHA_NO_PLANIFICABLE);
        assertThat(rechazoAnte(new IllegalArgumentException("NO_WEEKLY_ROCK: no hay plan semanal")))
                .isEqualTo(MotivoRechazo.SIN_OBJETIVO_SEMANAL);
        assertThat(rechazoAnte(new IllegalArgumentException("cada eje debe tener entre 1 y 3 rocas: CUERPO")))
                .isEqualTo(MotivoRechazo.DATOS_INVALIDOS);
        assertThat(rechazoAnte(new IllegalStateException("ALREADY_PLANNED: ya existen rocas")))
                .isEqualTo(MotivoRechazo.YA_PLANIFICADO);
        assertThat(rechazoAnte(new NotAuthorizedException("ROCKS_LOCKED: completa tu onboarding")))
                .isEqualTo(MotivoRechazo.ROCAS_BLOQUEADAS);
        assertThat(rechazoAnte(new NotAuthorizedException("Cuenta suspendida"))).isEqualTo(MotivoRechazo.SIN_ACCESO);
        assertThat(rechazoAnte(new NoSuchElementException("Participante no encontrado")))
                .isEqualTo(MotivoRechazo.SIN_PROGRAMA);
    }

    private MotivoRechazo rechazoAnte(RuntimeException rechazo) {
        doThrow(rechazo).when(crearPlanDiario).crear(any());
        ResultadoPlanificacion resultado = service.crearPlanDelDia(APRENDIZ, FECHA, List.of(CAMINAR));
        return ((ResultadoPlanificacion.Rechazado) resultado).motivo();
    }

    @Test
    @DisplayName("un eje inventado o un plan vacio son datos invalidos, y no llegan al caso de uso")
    void datosInvalidosAntesDelCasoDeUso() {
        ResultadoPlanificacion ejeInventado = service.crearPlanDelDia(APRENDIZ, FECHA,
                List.of(new AccionDelDia("SALUD", 1, "Caminar", 5, false, null, null)));
        ResultadoPlanificacion vacio = service.crearPlanDelDia(APRENDIZ, FECHA, List.of());

        assertThat(ejeInventado).isEqualTo(new ResultadoPlanificacion.Rechazado(MotivoRechazo.DATOS_INVALIDOS));
        assertThat(vacio).isEqualTo(new ResultadoPlanificacion.Rechazado(MotivoRechazo.DATOS_INVALIDOS));
        verify(crearPlanDiario, never()).crear(any());
    }

    @Test
    @DisplayName("un conflicto que no es ALREADY_PLANNED no es un rechazo del negocio: sube")
    void otroConflictoSube() {
        when(crearPlanDiario.crear(any())).thenThrow(new IllegalStateException("otra cosa"));

        assertThatThrownBy(() -> service.crearPlanDelDia(APRENDIZ, FECHA, List.of(CAMINAR)))
                .isInstanceOf(IllegalStateException.class);
    }

    /* ------------------------------------------------------------------------------------------
     * CON EL CASO DE USO REAL Y EL RELOJ EN LA FRANJA PELIGROSA (regla 02)
     *
     * 2026-08-26T03:00Z es MIERCOLES 26 en UTC pero MARTES 25 a las 22:00 en Lima: la ventana
     * nocturna esta abierta y "manana" para la persona es el 26. Si en algun punto de la cadena se
     * usara la fecha del servidor, el 26 seria HOY y no se reemplazaria: iria al camino del dia
     * en curso.
     * ---------------------------------------------------------------------------------------- */

    private static final FixedClock MADRUGADA_UTC = FixedClock.at(Instant.parse("2026-08-26T03:00:00Z"));
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** Lunes; el 2026-08-25 en Lima es el dia 16 del programa, el 26 cae en la semana 3. */
    private static final LocalDate INICIO = LocalDate.of(2026, 8, 10);

    private final LoadRocaMaestraPort loadRocaMaestraPort = mock(LoadRocaMaestraPort.class);
    private final LoadRocaSemanalPort loadRocaSemanalPort = mock(LoadRocaSemanalPort.class);
    private final SaveRocaDiariaPort saveRocaDiariaPort = mock(SaveRocaDiariaPort.class);
    private final ConsultarProgresoParticipanteRocksPort progresoPort = mock(ConsultarProgresoParticipanteRocksPort.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);

    private PlanificacionDeRocasService conCasoDeUsoReal() {
        RocaDiariaService rocaDiariaService = new RocaDiariaService(loadRocaMaestraPort, loadRocaSemanalPort,
                mock(LoadRocaDiariaPort.class), saveRocaDiariaPort, mock(RegistrarEvidenciaPort.class), progresoPort,
                mock(AlmacenamientoPort.class), mock(AjustarPuntosPort.class), mock(PublicarEnMuroPort.class),
                mock(ApplicationEventPublisher.class), MADRUGADA_UTC, idGenerator);
        when(progresoPort.deParticipante(APRENDIZ)).thenReturn(Optional.of(
                new ProgresoParticipanteRocks(16, INICIO, LIMA, RolParticipante.TRAINEE, false, true)));
        when(loadRocaMaestraPort.deParticipante(APRENDIZ)).thenReturn(Arrays.stream(EjeObjetivo.values())
                .map(eje -> RocaMaestra.rehydrate(RocaMaestraId.of(UUID.randomUUID()), APRENDIZ, eje, "objetivo",
                        null, MADRUGADA_UTC.now(), MADRUGADA_UTC.now()))
                .toList());
        when(loadRocaSemanalPort.deMaestraYSemana(any(), anyInt())).thenAnswer(inv -> Optional.of(
                RocaSemanal.planificar(RocaSemanalId.of(UUID.randomUUID()), inv.getArgument(0), 3, "objetivo",
                        null, null, null, MADRUGADA_UTC)));
        when(saveRocaDiariaPort.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(idGenerator.newId()).thenAnswer(inv -> UUID.randomUUID());
        return new PlanificacionDeRocasService(rocaDiariaService, crearPlanSemanal);
    }

    @Test
    @DisplayName("regla 02: a las 03:00 UTC el 26 es MANANA en Lima, y un dia que no llego se reemplaza")
    void mananaEnLimaAunqueEnUtcSeaHoy() {
        ResultadoPlanificacion resultado = conCasoDeUsoReal().crearPlanDelDia(APRENDIZ, FECHA, List.of(CAMINAR));

        assertThat(resultado).isEqualTo(new ResultadoPlanificacion.Creado(1));
        verify(saveRocaDiariaPort).borrarDeParticipanteYFecha(APRENDIZ, FECHA);
    }

    @Test
    @DisplayName("regla 02: el 25 es HOY en Lima con la ventana abierta, y ya no se planifica")
    void hoyEnLimaConLaVentanaAbierta() {
        ResultadoPlanificacion resultado = conCasoDeUsoReal().crearPlanDelDia(APRENDIZ, FECHA.minusDays(1),
                List.of(CAMINAR));

        assertThat(resultado).isEqualTo(new ResultadoPlanificacion.Rechazado(MotivoRechazo.FECHA_NO_PLANIFICABLE));
        verify(saveRocaDiariaPort, never()).saveAll(any());
    }
}
