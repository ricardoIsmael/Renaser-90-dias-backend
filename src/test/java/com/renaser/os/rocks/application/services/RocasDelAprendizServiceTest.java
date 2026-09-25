package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.api.RocasDelAprendizFinder.ObjetivoDelMesDelEje;
import com.renaser.os.rocks.api.RocasDelAprendizFinder.RocaDelDia;
import com.renaser.os.rocks.api.RocasDelAprendizFinder.RocasDeLaSemana;
import com.renaser.os.rocks.api.RocasDelAprendizFinder.RocasDelDia;
import com.renaser.os.rocks.application.ports.in.dashboard.ConsultarDashboardRocasUseCase;
import com.renaser.os.rocks.application.ports.in.dashboard.ConsultarDashboardRocasUseCase.DashboardRocas;
import com.renaser.os.rocks.application.ports.in.dashboard.ConsultarDashboardRocasUseCase.RocaSemanalVista;
import com.renaser.os.rocks.application.ports.in.rocadiaria.ConsultarRocasDeHoyUseCase.RocaDiariaVista;
import com.renaser.os.rocks.application.ports.in.rocadiaria.ConsultarRocasDeMananaUseCase;
import com.renaser.os.rocks.application.ports.in.rocamensual.ConsultarObjetivoDelMesUseCase;
import com.renaser.os.rocks.application.ports.in.rocamensual.ConsultarObjetivoDelMesUseCase.MesDelPlan;
import com.renaser.os.rocks.application.ports.in.rocamensual.ConsultarObjetivoDelMesUseCase.PlanMensualDelEje;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.domain.model.dashboard.EstadoRitmoRocas;
import com.renaser.os.rocks.domain.model.rocadiaria.ColorPareto;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiariaId;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.MetaCuantitativa;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocamensual.Magnitud;
import com.renaser.os.rocks.domain.model.rocamensual.ObjetivoDelMes;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensual;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensualId;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanalId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link RocasDelAprendizService} solo traduce lo que ya responden los casos de uso de la app. Lo
 * que se prueba aca es que la fecha y la ventana salgan de la zona del aprendiz, no del servidor.
 *
 * <p>Fixture coherente: programa iniciado el martes 2026-09-01, asi que el miercoles 2026-09-23 es
 * el dia 23, semana 4 (lunes 21 a domingo 27; la semana 1 es corta, del 1 al 6) y mes 1.
 */
class RocasDelAprendizServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate INICIO = LocalDate.of(2026, 9, 1);
    private static final LocalDate HOY_EN_LIMA = LocalDate.of(2026, 9, 23);
    /** 03:00 UTC del 24 = 22:00 del 23 en Lima: el servidor ya esta en "manana". */
    private static final FixedClock NOCHE_EN_LIMA = FixedClock.at(Instant.parse("2026-09-24T03:00:00Z"));
    /** 15:00 UTC = 10:00 en Lima, con la ventana nocturna todavia cerrada. */
    private static final FixedClock MANANA_EN_LIMA = FixedClock.at(Instant.parse("2026-09-23T15:00:00Z"));

    private final UserId aprendiz = UserId.of(UUID.randomUUID());
    private final RocaMaestra cuerpo = maestra(EjeObjetivo.CUERPO);
    private final RocaMaestra trabajo = maestra(EjeObjetivo.TRABAJO);
    private final RocaMaestra relaciones = maestra(EjeObjetivo.RELACIONES);
    private final RocaSemanal semanalCuerpo = RocaSemanal.rehydrate(new RocaSemanalId(UUID.randomUUID()),
            cuerpo.id(), 4, "Correr 3 veces", "La lluvia", "Correr en el gimnasio", 5, null, null, null,
            NOCHE_EN_LIMA.now(), NOCHE_EN_LIMA.now());

    private final ConsultarDashboardRocasUseCase dashboard = mock(ConsultarDashboardRocasUseCase.class);
    private final ConsultarRocasDeMananaUseCase manana = mock(ConsultarRocasDeMananaUseCase.class);
    private final ConsultarObjetivoDelMesUseCase objetivoDelMes = mock(ConsultarObjetivoDelMesUseCase.class);
    private final ConsultarProgresoParticipanteRocksPort progresoPort = mock(ConsultarProgresoParticipanteRocksPort.class);

    private RocasDelAprendizService servicio(FixedClock reloj) {
        when(progresoPort.deParticipante(aprendiz)).thenReturn(Optional.of(new ProgresoParticipanteRocks(23, INICIO,
                LIMA, RolParticipante.TRAINEE, false, true)));
        return new RocasDelAprendizService(dashboard, manana, objetivoDelMes, progresoPort, reloj);
    }

    @Test
    @DisplayName("hoy a las 03:00 UTC es el dia anterior en Lima, y la ventana nocturna ya esta abierta")
    void hoyEnLaZonaDelAprendiz() {
        RocaDiaria verde = roca(HOY_EN_LIMA, 1, EjeObjetivo.CUERPO, true);
        RocaDiaria amarilla = roca(HOY_EN_LIMA, 2, EjeObjetivo.CUERPO, false);
        when(dashboard.dashboard(aprendiz)).thenReturn(tablero(
                List.of(new RocaDiariaVista(amarilla, false), new RocaDiariaVista(verde, false)), true));
        when(manana.manana(aprendiz)).thenReturn(List.of(roca(HOY_EN_LIMA.plusDays(1), 1, EjeObjetivo.CUERPO, false)));

        RocasDelDia hoy = servicio(NOCHE_EN_LIMA).deHoy(aprendiz);

        assertThat(hoy.fecha()).isEqualTo(HOY_EN_LIMA);
        assertThat(hoy.rocas()).extracting(RocaDelDia::posicion).containsExactly(1, 2);
        RocaDelDia primera = hoy.rocas().get(0);
        assertThat(primera.color()).isEqualTo("VERDE");
        assertThat(primera.eje()).isEqualTo("CUERPO");
        assertThat(primera.horaInicio()).isEqualTo(LocalTime.of(7, 0));
        assertThat(primera.completada()).isTrue();
        assertThat(hoy.planificacion().planCreado()).isTrue();
        assertThat(hoy.planificacion().rocasPlanificadas()).isEqualTo(1);
        assertThat(hoy.planificacion().ventanaAbierta()).isTrue();
        assertThat(hoy.planificacion().ventanaAbreA()).isEqualTo(LocalTime.of(18, 0));
        assertThat(hoy.planificacion().puedeCrearPlan()).isTrue();
    }

    @Test
    @DisplayName("el bloqueo Pareto viaja tal cual lo resolvio el caso de uso")
    void bloqueoPareto() {
        RocaDiaria amarilla = roca(HOY_EN_LIMA, 2, EjeObjetivo.TRABAJO, false);
        when(dashboard.dashboard(aprendiz)).thenReturn(tablero(List.of(new RocaDiariaVista(amarilla, true)), false));
        when(manana.manana(aprendiz)).thenReturn(List.of());

        RocasDelDia hoy = servicio(MANANA_EN_LIMA).deHoy(aprendiz);

        assertThat(hoy.rocas().get(0).bloqueadaPorPareto()).isTrue();
        assertThat(hoy.planificacion().planCreado()).isFalse();
        assertThat(hoy.planificacion().ventanaAbierta()).isFalse();
    }

    @Test
    @DisplayName("manana es el dia local siguiente, no el siguiente del servidor")
    void mananaEnLaZonaDelAprendiz() {
        when(dashboard.dashboard(aprendiz)).thenReturn(tablero(List.of(), true));
        when(manana.manana(aprendiz)).thenReturn(List.of(roca(HOY_EN_LIMA.plusDays(1), 1, EjeObjetivo.TRABAJO, false)));

        RocasDelDia deManana = servicio(NOCHE_EN_LIMA).deManana(aprendiz);

        assertThat(deManana.fecha()).isEqualTo(LocalDate.of(2026, 9, 24));
        assertThat(deManana.rocas()).singleElement().satisfies(r -> {
            assertThat(r.eje()).isEqualTo("TRABAJO");
            assertThat(r.bloqueadaPorPareto()).isFalse();
        });
    }

    @Test
    @DisplayName("la semana trae el eje de cada objetivo semanal, su obstaculo y su contingencia")
    void semana() {
        when(dashboard.dashboard(aprendiz)).thenReturn(tablero(List.of(), true));

        RocasDeLaSemana semana = servicio(NOCHE_EN_LIMA).deLaSemana(aprendiz);

        assertThat(semana.numeroSemana()).isEqualTo(4);
        assertThat(semana.inicio()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(semana.fin()).isEqualTo(LocalDate.of(2026, 9, 27));
        assertThat(semana.rocas()).singleElement().satisfies(r -> {
            assertThat(r.eje()).isEqualTo("CUERPO");
            assertThat(r.obstaculo()).isEqualTo("La lluvia");
            assertThat(r.contingencia()).isEqualTo("Correr en el gimnasio");
            assertThat(r.editable()).isTrue();
            assertThat(r.revisada()).isFalse();
        });
    }

    @Test
    @DisplayName("el mes en curso: lo escrito por la persona manda; sin cifra, viaja el motivo")
    void mes() {
        RocaMensual editada = new RocaMensual(new RocaMensualId(UUID.randomUUID()), cuerpo.id(), 1, "Bajar 2 kg",
                MetaCuantitativa.nueva(new BigDecimal("2"), "kg"), NOCHE_EN_LIMA.now(), NOCHE_EN_LIMA.now());
        PlanMensualDelEje planCuerpo = new PlanMensualDelEje(EjeObjetivo.CUERPO, 1, "kg", false, List.of(
                new MesDelPlan(1, 30, true, new ObjetivoDelMes.ConCifra(1, Magnitud.NIVEL, new BigDecimal("78"),
                        new BigDecimal("2"), new BigDecimal("6"), false), editada),
                new MesDelPlan(2, 60, false, new ObjetivoDelMes.SinCifra(2,
                        ObjetivoDelMes.MotivoSinCifra.SIN_DATOS), null)), null);
        PlanMensualDelEje planTrabajo = new PlanMensualDelEje(EjeObjetivo.TRABAJO, 1, "S/", true, List.of(
                new MesDelPlan(1, 30, true, new ObjetivoDelMes.SinCifra(1,
                        ObjetivoDelMes.MotivoSinCifra.SIN_TIPO), null)), null);
        when(objetivoDelMes.misObjetivosMensuales(aprendiz)).thenReturn(List.of(planCuerpo, planTrabajo));

        List<ObjetivoDelMesDelEje> mes = servicio(NOCHE_EN_LIMA).delMes(aprendiz);

        assertThat(mes).hasSize(2);
        assertThat(mes.get(0).tituloPropio()).isEqualTo("Bajar 2 kg");
        assertThat(mes.get(0).cifra()).isEqualByComparingTo("2");
        assertThat(mes.get(0).diaDeCierre()).isEqualTo(30);
        assertThat(mes.get(1).cifra()).isNull();
        assertThat(mes.get(1).motivoSinCifra()).isEqualTo("SIN_TIPO");
    }

    @Test
    @DisplayName("una cuenta suspendida recibe lo mismo que en la app: el caso de uso la rechaza")
    void suspendido() {
        when(dashboard.dashboard(aprendiz)).thenThrow(new NotAuthorizedException("Cuenta suspendida"));

        assertThatThrownBy(() -> servicio(NOCHE_EN_LIMA).deHoy(aprendiz)).isInstanceOf(NotAuthorizedException.class);
    }

    private DashboardRocas tablero(List<RocaDiariaVista> deHoy, boolean puedeCrearPlanDiario) {
        return new DashboardRocas(23, 4, LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 27),
                List.of(cuerpo, trabajo, relaciones), true, true, List.of(new RocaSemanalVista(semanalCuerpo, true)), List.of(),
                EstadoRitmoRocas.OK, 0, 0, false, puedeCrearPlanDiario, true, false, deHoy, INICIO);
    }

    private RocaMaestra maestra(EjeObjetivo eje) {
        return new RocaMaestra(new RocaMaestraId(UUID.randomUUID()), aprendiz, eje, "Objetivo de " + eje, null,
                NOCHE_EN_LIMA.now(), NOCHE_EN_LIMA.now());
    }

    private RocaDiaria roca(LocalDate fecha, int posicion, EjeObjetivo eje, boolean completada) {
        return RocaDiaria.rehydrate(new RocaDiariaId(UUID.randomUUID()), aprendiz, fecha, posicion,
                "Roca " + posicion, null, ColorPareto.paraPosicion(posicion), 5, false, eje,
                semanalCuerpo.id(), LocalTime.of(7, 0), LocalTime.of(8, 0), List.of(), completada,
                completada ? NOCHE_EN_LIMA.now() : null, 0, NOCHE_EN_LIMA.now(), NOCHE_EN_LIMA.now());
    }
}
