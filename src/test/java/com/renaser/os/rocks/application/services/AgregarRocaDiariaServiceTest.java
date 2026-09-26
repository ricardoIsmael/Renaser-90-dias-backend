package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.in.rocadiaria.AgregarRocaDiariaUseCase.AgregarRocaDiariaCommand;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.rocks.application.ports.out.rocadiaria.SaveRocaDiariaPort;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.application.ports.out.rocasemanal.LoadRocaSemanalPort;
import com.renaser.os.rocks.domain.model.rocadiaria.ColorPareto;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiariaId;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanalId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D-177: agregar UNA accion a un dia que viene, sin tocar las demas.
 *
 * <p>Fixture coherente: programa iniciado el martes 2026-09-01; el miercoles 23 es el dia 23, semana
 * 4 (lunes 21 a domingo 27). El reloj por defecto esta a las 03:00 UTC del jueves 24, que en Lima es
 * todavia el MIERCOLES 23 a las 22:00 (regla 02: el caso que esconde un reloj a las 10:00 UTC).
 */
class AgregarRocaDiariaServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate INICIO = LocalDate.of(2026, 9, 1);
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 9, 23);
    private static final LocalDate JUEVES = LocalDate.of(2026, 9, 24);
    private static final FixedClock NOCHE_DEL_MIERCOLES_EN_LIMA = FixedClock.at(Instant.parse("2026-09-24T03:00:00Z"));

    private final UserId aprendiz = UserId.of(UUID.randomUUID());
    private final ConsultarProgresoParticipanteRocksPort progresoPort = mock(ConsultarProgresoParticipanteRocksPort.class);
    private final LoadRocaMaestraPort maestras = mock(LoadRocaMaestraPort.class);
    private final LoadRocaSemanalPort semanales = mock(LoadRocaSemanalPort.class);
    private final LoadRocaDiariaPort diarias = mock(LoadRocaDiariaPort.class);
    private final SaveRocaDiariaPort guardar = mock(SaveRocaDiariaPort.class);
    private final IdGenerator ids = mock(IdGenerator.class);
    private final List<RocaMaestra> tresMaestras = Arrays.stream(EjeObjetivo.values()).map(this::maestra).toList();
    private final RocaSemanal semanalCuerpo = RocaSemanal.rehydrate(new RocaSemanalId(UUID.randomUUID()),
            tresMaestras.get(0).id(), 4, "Correr 3 veces", null, null, null, null, null, null,
            NOCHE_DEL_MIERCOLES_EN_LIMA.now(), NOCHE_DEL_MIERCOLES_EN_LIMA.now());

    private AgregarRocaDiariaService servicio(FixedClock reloj) {
        when(progresoPort.deParticipante(aprendiz)).thenReturn(Optional.of(new ProgresoParticipanteRocks(23, INICIO,
                LIMA, RolParticipante.TRAINEE, false, true)));
        when(maestras.deParticipante(aprendiz)).thenReturn(tresMaestras);
        when(semanales.deMaestraYSemana(tresMaestras.get(0).id(), 4)).thenReturn(Optional.of(semanalCuerpo));
        when(ids.newId()).thenReturn(UUID.randomUUID());
        when(guardar.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return new AgregarRocaDiariaService(progresoPort, maestras, semanales, diarias, guardar, reloj, ids);
    }

    @Test
    @DisplayName("va detras de las del eje, con su color, y no reescribe las que ya estaban")
    void agregaDetrasSinTocarLasDemas() {
        AgregarRocaDiariaService servicio = servicio(NOCHE_DEL_MIERCOLES_EN_LIMA);
        when(diarias.deParticipanteYFecha(aprendiz, JUEVES)).thenReturn(List.of(existente(EjeObjetivo.CUERPO, 1),
                existente(EjeObjetivo.TRABAJO, 1)));

        RocaDiaria agregada = servicio.agregar(comando(JUEVES, EjeObjetivo.CUERPO));

        assertThat(agregada.posicion()).isEqualTo(2);
        assertThat(agregada.color()).isEqualTo(ColorPareto.AMARILLA);
        assertThat(agregada.fecha()).isEqualTo(JUEVES);
        assertThat(agregada.rocaSemanalId()).isEqualTo(semanalCuerpo.id());
        assertThat(agregada.horaInicio()).isEqualTo(LocalTime.of(7, 0));
        verify(guardar).save(agregada);
        verify(guardar, never()).saveAll(any());
        verify(guardar, never()).borrarDeParticipanteYFecha(any(), any());
    }

    @Test
    @DisplayName("hoy nunca: a las 03:00 UTC el miercoles 23 sigue siendo hoy en Lima (CURRENT_DAY)")
    void hoyEnLaZonaDelAprendizSeRechaza() {
        AgregarRocaDiariaService servicio = servicio(NOCHE_DEL_MIERCOLES_EN_LIMA);

        assertThatThrownBy(() -> servicio.agregar(comando(MIERCOLES, EjeObjetivo.CUERPO)))
                .isInstanceOf(IllegalStateException.class).hasMessageStartingWith("CURRENT_DAY");
        verify(guardar, never()).save(any());
    }

    @Test
    @DisplayName("hoy nunca, tampoco antes de las 18:00, cuando el plan del dia si lo aceptaria")
    void hoyDeMananaTambienSeRechaza() {
        AgregarRocaDiariaService servicio = servicio(FixedClock.at(Instant.parse("2026-09-23T15:00:00Z")));

        assertThatThrownBy(() -> servicio.agregar(comando(MIERCOLES, EjeObjetivo.CUERPO)))
                .hasMessageStartingWith("CURRENT_DAY");
    }

    @Test
    @DisplayName("despues del domingo de la semana, o un dia que paso: INVALID_DATE")
    void fueraDeLaVentanaDeFechas() {
        AgregarRocaDiariaService servicio = servicio(NOCHE_DEL_MIERCOLES_EN_LIMA);

        assertThatThrownBy(() -> servicio.agregar(comando(LocalDate.of(2026, 9, 28), EjeObjetivo.CUERPO)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("INVALID_DATE");
        assertThatThrownBy(() -> servicio.agregar(comando(LocalDate.of(2026, 9, 22), EjeObjetivo.CUERPO)))
                .hasMessageStartingWith("INVALID_DATE");
    }

    @Test
    @DisplayName("sin objetivo semanal del eje en esa semana: NO_WEEKLY_ROCK")
    void sinObjetivoSemanal() {
        AgregarRocaDiariaService servicio = servicio(NOCHE_DEL_MIERCOLES_EN_LIMA);
        when(semanales.deMaestraYSemana(tresMaestras.get(1).id(), 4)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.agregar(comando(JUEVES, EjeObjetivo.TRABAJO)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("NO_WEEKLY_ROCK");
    }

    @Test
    @DisplayName("con el eje lleno ese dia: AXIS_FULL, sin guardar")
    void ejeLleno() {
        AgregarRocaDiariaService servicio = servicio(NOCHE_DEL_MIERCOLES_EN_LIMA);
        when(diarias.deParticipanteYFecha(aprendiz, JUEVES)).thenReturn(List.of(existente(EjeObjetivo.CUERPO, 1),
                existente(EjeObjetivo.CUERPO, 2), existente(EjeObjetivo.CUERPO, 3)));

        assertThatThrownBy(() -> servicio.agregar(comando(JUEVES, EjeObjetivo.CUERPO)))
                .hasMessageStartingWith("AXIS_FULL");
        verify(guardar, never()).save(any());
    }

    @Test
    @DisplayName("sin las tres Rocas Maestras: ROCKS_LOCKED; suspendido: 403")
    void guardas() {
        AgregarRocaDiariaService servicio = servicio(NOCHE_DEL_MIERCOLES_EN_LIMA);
        when(maestras.deParticipante(aprendiz)).thenReturn(tresMaestras.subList(0, 2));

        assertThatThrownBy(() -> servicio.agregar(comando(JUEVES, EjeObjetivo.CUERPO)))
                .isInstanceOf(NotAuthorizedException.class).hasMessageStartingWith("ROCKS_LOCKED");

        when(progresoPort.deParticipante(aprendiz)).thenReturn(Optional.of(new ProgresoParticipanteRocks(23, INICIO,
                LIMA, RolParticipante.TRAINEE, true, true)));
        assertThatThrownBy(() -> servicio.agregar(comando(JUEVES, EjeObjetivo.CUERPO)))
                .isInstanceOf(NotAuthorizedException.class).hasMessage("Cuenta suspendida");
    }

    private AgregarRocaDiariaCommand comando(LocalDate fecha, EjeObjetivo eje) {
        return new AgregarRocaDiariaCommand(aprendiz, fecha, eje, "Estirar 10 minutos", 5, false, LocalTime.of(7, 0),
                LocalTime.of(7, 10));
    }

    private RocaDiaria existente(EjeObjetivo eje, int posicion) {
        return RocaDiaria.planificar(RocaDiariaId.of(UUID.randomUUID()), aprendiz, JUEVES, posicion, "Ya estaba",
                "descripcion que el chat no ve", 8, true, eje, semanalCuerpo.id(), null, null, List.of(),
                NOCHE_DEL_MIERCOLES_EN_LIMA);
    }

    private RocaMaestra maestra(EjeObjetivo eje) {
        return new RocaMaestra(new RocaMaestraId(UUID.randomUUID()), aprendiz, eje, "Objetivo de " + eje, null,
                NOCHE_DEL_MIERCOLES_EN_LIMA.now(), NOCHE_DEL_MIERCOLES_EN_LIMA.now());
    }
}
