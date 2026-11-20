package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.community.api.ZonaDelAcompananteFinder;
import com.renaser.os.mentoring.api.RojoDelDiaEvent;
import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.points.api.SemaforoDelDiaFinder;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El aviso de rojo de las 19:00 (spec 057, FR-031 a FR-033). */
class AvisoDeRojoServiceTest {

    /** 19:05 del viernes 20 de noviembre en Lima = 00:05 UTC del sábado 21: la madrugada UTC que esconde el desfase de día. */
    private static final Instant A_LAS_19_EN_LIMA = Instant.parse("2026-11-21T00:05:00Z");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final UserId mentora = UserId.of(UUID.randomUUID());
    private final UserId ana = UserId.of(UUID.randomUUID());
    private final UserId beto = UserId.of(UUID.randomUUID());
    private final UserId caro = UserId.of(UUID.randomUUID());
    private final UUID fenix = UUID.randomUUID();
    private final AcompanamientoFinder acompanamiento = mock(AcompanamientoFinder.class);
    private final ParticipacionProgramaFinder participaciones = mock(ParticipacionProgramaFinder.class);
    private final ZonaDelAcompananteFinder zonas = mock(ZonaDelAcompananteFinder.class);
    private final MedicionDeGrupos medicion = mock(MedicionDeGrupos.class);
    private final SemaforoDelDiaFinder semaforoDelDia = mock(SemaforoDelDiaFinder.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);

    @BeforeEach
    void preparar() {
        when(acompanamiento.gruposConMentorVigente(any())).thenReturn(List.of(
                new GrupoAcompanado(fenix, "Fénix", mentora, UUID.randomUUID(), "America/Lima", 3)));
        Map<UserId, UserSummary> padron = new LinkedHashMap<>();
        padron.put(ana, new UserSummary(ana, "Ana Pérez", null, UserRole.TRAINEE, UserStatus.ACTIVE));
        padron.put(beto, new UserSummary(beto, "Beto Ríos", null, UserRole.TRAINEE, UserStatus.ACTIVE));
        padron.put(caro, new UserSummary(caro, "Caro Díaz", null, UserRole.TRAINEE, UserStatus.ACTIVE));
        when(medicion.padronDe(eq(fenix), any())).thenReturn(padron);
        when(zonas.zonaDe(any(), any())).thenReturn(LIMA);
        when(participaciones.deParticipantes(any())).thenAnswer(inv -> {
            Map<UserId, ParticipacionPrograma> deTodos = new LinkedHashMap<>();
            for (UserId a : (Collection<UserId>) inv.getArgument(0)) {
                ParticipacionPrograma participacion = mock(ParticipacionPrograma.class);
                when(participacion.zona()).thenReturn(a.equals(caro) ? ZoneId.of("Asia/Tokyo") : LIMA);
                deTodos.put(a, participacion);
            }
            return deTodos;
        });
    }

    private AvisoDeRojoService servicio(Instant ahora) {
        return new AvisoDeRojoService(acompanamiento, participaciones, zonas, medicion, semaforoDelDia, eventos, mock(PlatformTransactionManager.class),
                FixedClock.at(ahora));
    }

    private static DiaDelSemaforo dia(LocalDate fecha, EstadoDiaSemaforo estado, Integer porcentaje, ColorSemaforo color) {
        return new DiaDelSemaforo(fecha, estado, porcentaje, color, 3, 1, 0, 0);
    }

    @Test
    @DisplayName("spec 057 FR-031: a las 19:00 del mentor publica un evento por cada aprendiz MEDIDO en ROJO; el verde, el amarillo y el sin datos no avisan")
    void soloLosRojosMedidos() { // spec 057 FR-031
        LocalDate hoyEnLima = LocalDate.of(2026, 11, 20);
        when(semaforoDelDia.delDia(any(), eq(hoyEnLima))).thenReturn(Map.of(
                ana, dia(hoyEnLima, EstadoDiaSemaforo.MEDIDO, 33, ColorSemaforo.ROJO),
                beto, dia(hoyEnLima, EstadoDiaSemaforo.MEDIDO, 90, ColorSemaforo.VERDE)));

        int publicados = servicio(A_LAS_19_EN_LIMA).avisar();

        assertThat(publicados).isEqualTo(1);
        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(evento.capture());
        RojoDelDiaEvent rojo = (RojoDelDiaEvent) evento.getValue();
        assertThat(rojo.mentorId()).isEqualTo(mentora.value());
        assertThat(rojo.aprendizId()).isEqualTo(ana.value());
        assertThat(rojo.grupoId()).isEqualTo(fenix);
        assertThat(rojo.nombreDelAprendiz()).isEqualTo("Ana Pérez");
        assertThat(rojo.porcentaje()).isEqualTo(33);
        assertThat(rojo.rutaApp()).isEqualTo("/mentor/groups/" + fenix + "/learners/" + ana.value());
    }

    @Test
    @DisplayName("spec 057 FR-031: un día pausado, sin datos, suspendido o fuera del programa nunca avisa, aunque traiga color")
    void soloElEstadoMedido() { // spec 057 FR-031
        LocalDate hoyEnLima = LocalDate.of(2026, 11, 20);
        when(semaforoDelDia.delDia(any(), eq(hoyEnLima))).thenReturn(Map.of(
                ana, DiaDelSemaforo.sinPorcentaje(hoyEnLima, EstadoDiaSemaforo.PAUSADO),
                beto, DiaDelSemaforo.sinPorcentaje(hoyEnLima, EstadoDiaSemaforo.SIN_DATOS)));

        assertThat(servicio(A_LAS_19_EN_LIMA).avisar()).isZero();
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("spec 057 FR-031: fuera de las 19:00 a las 19:29 de la zona del mentor no lee el semáforo ni publica nada")
    void fueraDeHora() { // spec 057 FR-031
        // 18:35 en Lima del viernes: todavía no.
        assertThat(servicio(Instant.parse("2026-11-20T23:35:00Z")).avisar()).isZero();
        // 19:35 en Lima: ya pasó la ventana.
        assertThat(servicio(Instant.parse("2026-11-21T00:35:00Z")).avisar()).isZero();

        org.mockito.Mockito.verifyNoInteractions(semaforoDelDia);
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("spec 057 FR-031: el día es el LOCAL DEL APRENDIZ: a las 19:05 de Lima ya es sábado en Tokio, y se pregunta por ese día")
    void diaLocalDelAprendiz() { // spec 057 FR-031
        LocalDate hoyEnLima = LocalDate.of(2026, 11, 20);
        LocalDate hoyEnTokio = LocalDate.of(2026, 11, 21);
        when(semaforoDelDia.delDia(any(), eq(hoyEnLima))).thenReturn(Map.of());
        when(semaforoDelDia.delDia(any(), eq(hoyEnTokio))).thenReturn(Map.of(
                caro, dia(hoyEnTokio, EstadoDiaSemaforo.MEDIDO, 20, ColorSemaforo.ROJO)));

        servicio(A_LAS_19_EN_LIMA).avisar();

        verify(semaforoDelDia).delDia(eq(List.of(caro)), eq(hoyEnTokio));
        verify(semaforoDelDia).delDia(eq(List.of(ana, beto)), eq(hoyEnLima));
        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(((RojoDelDiaEvent) evento.getValue()).aprendizId()).isEqualTo(caro.value());
    }

    @Test
    @DisplayName("spec 057 FR-032: dos corridas en la misma semana del aprendiz publican la misma clave; la semana siguiente, otra")
    void laClaveEsPorSemana() { // spec 057 FR-032
        // Solo Ana (de Lima) está en rojo: los días de Tokio de Caro no se stubean y vuelven vacíos.
        for (LocalDate dia : List.of(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 27))) {
            when(semaforoDelDia.delDia(org.mockito.ArgumentMatchers.argThat(l -> l != null && l.contains(ana)), eq(dia)))
                    .thenReturn(Map.of(ana, dia(dia, EstadoDiaSemaforo.MEDIDO, 30, ColorSemaforo.ROJO)));
        }

        servicio(Instant.parse("2026-11-21T00:05:00Z")).avisar();
        servicio(Instant.parse("2026-11-20T00:05:00Z")).avisar();
        servicio(Instant.parse("2026-11-28T00:05:00Z")).avisar();

        ArgumentCaptor<Object> eventosPublicados = ArgumentCaptor.forClass(Object.class);
        verify(eventos, org.mockito.Mockito.times(3)).publishEvent(eventosPublicados.capture());
        List<UUID> claves = eventosPublicados.getAllValues().stream().map(e -> ((RojoDelDiaEvent) e).claveDeduplicacion()).toList();
        assertThat(claves.get(0)).as("el jueves y el viernes son la misma semana").isEqualTo(claves.get(1));
        assertThat(claves.get(2)).as("el viernes siguiente es otra semana").isNotEqualTo(claves.get(0));
    }

    @Test
    @DisplayName("spec 057 FR-031: un mentor que falla no frena a los demás")
    void unMentorQueFallaNoFrenaAlResto() { // spec 057 FR-031
        UserId otraMentora = UserId.of(UUID.randomUUID());
        UUID aurora = UUID.randomUUID();
        when(acompanamiento.gruposConMentorVigente(any())).thenReturn(List.of(
                new GrupoAcompanado(aurora, "Aurora", otraMentora, UUID.randomUUID(), "America/Lima", 3),
                new GrupoAcompanado(fenix, "Fénix", mentora, UUID.randomUUID(), "America/Lima", 3)));
        when(medicion.padronDe(eq(aurora), any())).thenThrow(new IllegalStateException("falla de lectura"));
        LocalDate hoy = LocalDate.of(2026, 11, 20);
        when(semaforoDelDia.delDia(any(), eq(hoy))).thenReturn(Map.of(ana, dia(hoy, EstadoDiaSemaforo.MEDIDO, 10, ColorSemaforo.ROJO)));

        assertThat(servicio(A_LAS_19_EN_LIMA).avisar()).isEqualTo(1);
    }
}
