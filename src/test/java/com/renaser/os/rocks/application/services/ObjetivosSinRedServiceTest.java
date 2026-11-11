package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.in.rocamaestra.DefinirRocaMaestraUseCase;
import com.renaser.os.rocks.application.ports.in.rocamaestra.DefinirRocaMaestraUseCase.DefinirRocaMaestraCommand;
import com.renaser.os.rocks.application.ports.in.rocamensual.DefinirRocaMensualUseCase;
import com.renaser.os.rocks.application.ports.in.rocamensual.DefinirRocaMensualUseCase.DefinirRocaMensualCommand;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CerrarSemanaUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CerrarSemanaUseCase.CerrarSemanaCommand;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CrearPlanSemanalUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CrearPlanSemanalUseCase.CrearPlanSemanalCommand;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CrearPlanSemanalUseCase.ItemRocaSemanal;
import com.renaser.os.rocks.application.ports.in.rocasemanal.EditarDentroDe48hUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.EditarDentroDe48hUseCase.EditarRocaSemanalCommand;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.application.ports.out.rocamensual.LoadRocaMensualPort;
import com.renaser.os.rocks.application.ports.out.rocasemanal.LoadRocaSemanalPort;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.OrigenDelObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensual;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensualId;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanalId;
import com.renaser.os.shared.application.EntregaSinRedDePrueba;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.NoAplicadoSinRedException;
import com.renaser.os.shared.domain.OrigenDeLaHora;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.ANA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Los objetivos del Plan hechos sin red (spec 048, fase 3, FR-019): semana, revisión, objetivo de 90 días y del mes, con la regla de choques
 * D13. Lo hecho el domingo 8 de noviembre a las 21:40 de Lima (02:40 UTC del lunes 9) y enviado el lunes 9 a las 08:00 de Lima.
 */
class ObjetivosSinRedServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Instant HECHO = Instant.parse("2026-11-09T02:40:00Z");
    private static final Instant LLEGA = Instant.parse("2026-11-09T13:00:00Z");
    private static final Instant VIEJO = Instant.parse("2026-10-30T14:00:00Z");
    private static final UUID ENVIO = UUID.fromString("5f0c1f4e-2d0a-4b7e-9d77-0c9f3f6b1a10");
    private static final HechoSinRed SIN_RED = new HechoSinRed(HECHO, ENVIO, OrigenDeLaHora.RELOJ_DEL_SISTEMA);

    private final ConsultarProgresoParticipanteRocksPort progreso = mock(ConsultarProgresoParticipanteRocksPort.class);
    private final LoadRocaMaestraPort maestras = mock(LoadRocaMaestraPort.class);
    private final LoadRocaSemanalPort semanales = mock(LoadRocaSemanalPort.class);
    private final LoadRocaMensualPort mensuales = mock(LoadRocaMensualPort.class);
    private final CrearPlanSemanalUseCase crearSemana = mock(CrearPlanSemanalUseCase.class);
    private final EditarDentroDe48hUseCase editarSemanal = mock(EditarDentroDe48hUseCase.class);
    private final CerrarSemanaUseCase cerrarSemana = mock(CerrarSemanaUseCase.class);
    private final DefinirRocaMaestraUseCase definirMaestra = mock(DefinirRocaMaestraUseCase.class);
    private final DefinirRocaMensualUseCase definirMensual = mock(DefinirRocaMensualUseCase.class);

    private EntregaSinRedDePrueba entrega;
    private ObjetivosSinRedService servicio;
    private RocaMaestra cuerpo;
    private RocaMaestra trabajo;
    private RocaMaestra relaciones;

    @BeforeEach
    void preparar() {
        FixedClock reloj = FixedClock.at(LLEGA);
        entrega = EntregaSinRedDePrueba.nueva(reloj);
        servicio = new ObjetivosSinRedService(progreso, maestras, semanales, mensuales, crearSemana, editarSemanal, cerrarSemana,
                definirMaestra, definirMensual, entrega.puerto(), reloj);
        when(progreso.deParticipante(ANA)).thenReturn(Optional.of(
                new ProgresoParticipanteRocks(17, LocalDate.of(2026, 10, 24), LIMA, RolParticipante.TRAINEE, false, true)));
        cuerpo = maestra(EjeObjetivo.CUERPO, VIEJO);
        trabajo = maestra(EjeObjetivo.TRABAJO, VIEJO);
        relaciones = maestra(EjeObjetivo.RELACIONES, VIEJO);
        when(maestras.deParticipante(ANA)).thenReturn(List.of(cuerpo, trabajo, relaciones));
        when(semanales.deParticipanteYSemana(any(), anyInt())).thenReturn(List.of());
    }

    @AfterEach
    void terminar() {
        entrega.terminar();
    }

    private static RocaMaestra maestra(EjeObjetivo eje, Instant actualizadoEn) {
        return RocaMaestra.rehydrate(RocaMaestraId.of(UUID.randomUUID()), ANA, eje, "Objetivo", null, VIEJO, actualizadoEn);
    }

    private static RocaSemanal semanal(RocaMaestra maestra, Instant creadoEn, Instant actualizadoEn) {
        return RocaSemanal.rehydrate(RocaSemanalId.of(UUID.randomUUID()), maestra.id(), 2, "Titulo", "obstaculo", "contingencia", 5,
                null, null, null, creadoEn, actualizadoEn);
    }

    private static CrearPlanSemanalCommand semana(EjeObjetivo... ejes) {
        return new CrearPlanSemanalCommand(ANA, java.util.Arrays.stream(ejes)
                .map(eje -> new ItemRocaSemanal(eje, "Correr", "Pereza", "Salir igual", 5)).toList());
    }

    private static EditarRocaSemanalCommand editar(RocaSemanal roca) {
        return new EditarRocaSemanalCommand(ANA, roca.id(), "Nuevo título", null, null, null);
    }

    // ─── semana ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("semana nueva: se crea con la hora real (el domingo por la noche planifica la semana que empieza el lunes)")
    void creaLaSemanaConLaHoraReal() { // spec 048 FR-019
        servicio.crearSemana(semana(EjeObjetivo.CUERPO), SIN_RED);

        verify(crearSemana).crear(any(CrearPlanSemanalCommand.class), eq(HECHO));
        assertThat(entrega.registro(ENVIO).orElseThrow().tipo()).isEqualTo("PLAN_SEMANAL_CREAR");
    }

    @Test
    @DisplayName("la semana ya planificada en todos los ejes pedidos: YA_ESTABA_HECHO; si se creó DESPUÉS de lo que hiciste, CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR")
    void semanaYaPlanificada() { // spec 048 FR-019
        when(semanales.deParticipanteYSemana(any(), anyInt())).thenReturn(List.of(semanal(cuerpo, VIEJO, VIEJO)));
        assertThatThrownBy(() -> servicio.crearSemana(semana(EjeObjetivo.CUERPO), SIN_RED)).isInstanceOfSatisfying(
                NoAplicadoSinRedException.class, e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.YA_ESTABA_HECHO));

        when(semanales.deParticipanteYSemana(any(), anyInt())).thenReturn(List.of(semanal(cuerpo, HECHO.plusSeconds(60), HECHO.plusSeconds(60))));
        assertThatThrownBy(() -> servicio.crearSemana(semana(EjeObjetivo.CUERPO), new HechoSinRed(HECHO, UUID.randomUUID(), null)))
                .isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR));
        verify(crearSemana, never()).crear(any(), any());
    }

    @Test
    @DisplayName("si solo algunos ejes ya estaban se crean los que faltan, como con red")
    void seCreanLosQueFaltan() { // spec 048 FR-019
        when(semanales.deParticipanteYSemana(any(), anyInt())).thenReturn(List.of(semanal(cuerpo, VIEJO, VIEJO)));

        servicio.crearSemana(semana(EjeObjetivo.CUERPO, EjeObjetivo.TRABAJO), SIN_RED);

        verify(crearSemana).crear(any(CrearPlanSemanalCommand.class), eq(HECHO));
    }

    @Test
    @DisplayName("editar la semana: si cambió en el servidor DESPUÉS gana el servidor; si fue ANTES se aplica con la hora real; si ya no existe, RECURSO_YA_NO_EXISTE")
    void editarLaSemana() { // spec 048 FR-019
        RocaSemanal masNueva = semanal(cuerpo, VIEJO, HECHO.plusSeconds(30));
        when(semanales.byId(masNueva.id())).thenReturn(Optional.of(masNueva));
        assertThatThrownBy(() -> servicio.editarSemanal(editar(masNueva), SIN_RED)).isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR));

        RocaSemanal vieja = semanal(cuerpo, VIEJO, HECHO.minusSeconds(3600));
        when(semanales.byId(vieja.id())).thenReturn(Optional.of(vieja));
        servicio.editarSemanal(editar(vieja), new HechoSinRed(HECHO, UUID.randomUUID(), null));
        verify(editarSemanal).editar(any(EditarRocaSemanalCommand.class), eq(HECHO));

        RocaSemanal fantasma = semanal(cuerpo, VIEJO, VIEJO);
        when(semanales.byId(fantasma.id())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.editarSemanal(editar(fantasma), new HechoSinRed(HECHO, UUID.randomUUID(), null)))
                .isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.RECURSO_YA_NO_EXISTE));
    }

    @Test
    @DisplayName("la revisión de la semana (cerrar) sigue la misma regla de choque y se aplica con la hora real")
    void cerrarLaSemana() { // spec 048 FR-019
        RocaSemanal vieja = semanal(cuerpo, VIEJO, VIEJO);
        when(semanales.byId(vieja.id())).thenReturn(Optional.of(vieja));

        servicio.cerrarSemana(new CerrarSemanaCommand(ANA, vieja.id(), 8, "Falta de tiempo", "Madrugar"), SIN_RED);

        verify(cerrarSemana).cerrar(any(CerrarSemanaCommand.class), eq(HECHO));
        assertThat(entrega.registro(ENVIO).orElseThrow().tipo()).isEqualTo("PLAN_SEMANAL_CERRAR");
    }

    @Test
    @DisplayName("la semana de otra persona es el 403 de siempre")
    void semanaAjena() { // spec 048 FR-019
        RocaSemanal ajena = semanal(maestra(EjeObjetivo.CUERPO, VIEJO), VIEJO, VIEJO);
        when(semanales.byId(ajena.id())).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> servicio.editarSemanal(editar(ajena), SIN_RED))
                .isInstanceOf(com.renaser.os.shared.domain.NotAuthorizedException.class);
        verify(editarSemanal, never()).editar(any(), any());
    }

    // ─── objetivo de 90 días y del mes ─────────────────────────────────────

    private static DefinirRocaMaestraCommand definirCuerpo() {
        return new DefinirRocaMaestraCommand(ANA, EjeObjetivo.CUERPO, "Objetivo", null, null, null, null, false);
    }

    @Test
    @DisplayName("objetivo de 90 días: un avance registrado en el servidor DESPUÉS de lo que hiciste gana; uno anterior no choca")
    void objetivoDe90Dias() { // spec 048 FR-019
        RocaMaestra avanzadaDespues = maestra(EjeObjetivo.CUERPO, HECHO.plusSeconds(120));
        when(maestras.deParticipanteYEje(ANA, EjeObjetivo.CUERPO)).thenReturn(Optional.of(avanzadaDespues));
        assertThatThrownBy(() -> servicio.definirObjetivoDe90Dias(definirCuerpo(), SIN_RED)).isInstanceOfSatisfying(
                NoAplicadoSinRedException.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR));

        when(maestras.deParticipanteYEje(ANA, EjeObjetivo.CUERPO)).thenReturn(Optional.of(cuerpo));
        servicio.definirObjetivoDe90Dias(definirCuerpo(), new HechoSinRed(HECHO, UUID.randomUUID(), null));
        verify(definirMaestra).definir(any(DefinirRocaMaestraCommand.class), eq(HECHO));
    }

    @Test
    @DisplayName("un objetivo de 90 días POR CONFIRMAR (nacido del diagnóstico) no cuenta como cambio aunque su hora sea posterior: es de la materialización")
    void porConfirmarNoChoca() { // spec 048 FR-019
        RocaMaestra delDiagnostico = RocaMaestra.rehydrate(RocaMaestraId.of(UUID.randomUUID()), ANA, EjeObjetivo.CUERPO, "Del diagnóstico",
                null, HECHO.plusSeconds(300), HECHO.plusSeconds(300), OrigenDelObjetivo.DIAGNOSTICO, null);
        when(maestras.deParticipanteYEje(ANA, EjeObjetivo.CUERPO)).thenReturn(Optional.of(delDiagnostico));

        servicio.definirObjetivoDe90Dias(definirCuerpo(), SIN_RED);

        verify(definirMaestra).definir(any(DefinirRocaMaestraCommand.class), eq(HECHO));
    }

    private static DefinirRocaMensualCommand definirMes() {
        return new DefinirRocaMensualCommand(ANA, EjeObjetivo.CUERPO, 2, "Correr 12 km", null, null, null);
    }

    @Test
    @DisplayName("objetivo del mes: sin el objetivo de 90 días es RECURSO_YA_NO_EXISTE; uno cambiado DESPUÉS gana el servidor; si no, se aplica")
    void objetivoDelMes() { // spec 048 FR-019
        when(maestras.deParticipanteYEje(ANA, EjeObjetivo.CUERPO)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.definirObjetivoDelMes(definirMes(), SIN_RED)).isInstanceOfSatisfying(
                NoAplicadoSinRedException.class, e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.RECURSO_YA_NO_EXISTE));

        when(maestras.deParticipanteYEje(ANA, EjeObjetivo.CUERPO)).thenReturn(Optional.of(cuerpo));
        when(mensuales.deMaestraYMes(cuerpo.id(), 2)).thenReturn(Optional.of(
                RocaMensual.rehydrate(RocaMensualId.of(UUID.randomUUID()), cuerpo.id(), 2, "Otro", null, VIEJO, HECHO.plusSeconds(10))));
        assertThatThrownBy(() -> servicio.definirObjetivoDelMes(definirMes(), new HechoSinRed(HECHO, UUID.randomUUID(), null)))
                .isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR));

        when(mensuales.deMaestraYMes(cuerpo.id(), 2)).thenReturn(Optional.empty());
        servicio.definirObjetivoDelMes(definirMes(), new HechoSinRed(HECHO, UUID.randomUUID(), null));
        verify(definirMensual, times(1)).definir(any(DefinirRocaMensualCommand.class), eq(HECHO));
    }
}
