package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.HabitoEnJuegoResumen;
import com.renaser.os.habits.api.HabitoEnJuegoResumen.TramoPuntos;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase.TrackDelDiaConCatalogo;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.medicion.ContarMedicionesAutomaticasPort;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.registro.PuntosEnJuego;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.habits.domain.model.registro.VentanaEntrega;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * La escala en tramos que {@code habits} le expone al agente (2026-09-23). El caso que importa es
 * {@link #losTramosDicenLoMismoQuePuntosEnJuego}: los tramos se derivan de
 * {@code ResultadoOtorgamiento} sin la ventana entera, y este test los compara contra
 * {@code PuntosEnJuego.de} con una ventana REAL. Si D-97 cambia de forma, falla aca.
 */
class AgendaDelDiaFinderServiceTest {

    /** La zona real del padron: nada de UTC en un test de horarios (E-91). */
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate FECHA = LocalDate.of(2026, 9, 23);
    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());

    private final ConsultarTracksDelDiaConCatalogoUseCase tracks = mock(ConsultarTracksDelDiaConCatalogoUseCase.class);
    private final ConsultarProgresoParticipanteHabitsPort progreso =
            mock(ConsultarProgresoParticipanteHabitsPort.class);
    private final LoadHabitoPort habitos = mock(LoadHabitoPort.class);
    private final ContarMedicionesAutomaticasPort automaticas = mock(ContarMedicionesAutomaticasPort.class);
    private final AgendaDelDiaFinderService service = new AgendaDelDiaFinderService(tracks,
            mock(CompletarRegistroUseCase.class), progreso, habitos, automaticas);

    /** 20:00 -> 22:00 en Lima, extension por defecto: la gracia va de 23:50 a 00:00 del dia siguiente. */
    private static VentanaEntrega ventanaNocturna() {
        return VentanaEntrega.calcular(FECHA, LocalTime.of(20, 0), LocalTime.of(22, 0), LIMA, null);
    }

    @Test
    @DisplayName("la escala: 10 hasta 2 min dentro de la gracia, despues 9, 8, 7 y 6 hasta el plazo")
    void escalaEnTramos() {
        VentanaEntrega ventana = ventanaNocturna();
        Instant plazo = ventana.plazoEvidencia();

        List<TramoPuntos> tramos = AgendaDelDiaFinderService.tramosDe(PuntosEnJuego.de(ventana,
                plazo.minus(Duration.ofHours(3))));

        assertThat(tramos).containsExactly(
                new TramoPuntos(plazo.minus(Duration.ofMinutes(8)), 10),
                new TramoPuntos(plazo.minus(Duration.ofMinutes(6)), 9),
                new TramoPuntos(plazo.minus(Duration.ofMinutes(4)), 8),
                new TramoPuntos(plazo.minus(Duration.ofMinutes(2)), 7),
                new TramoPuntos(plazo, 6));
    }

    @Test
    @DisplayName("cada 30 s, desde antes del ancla hasta el plazo, el tramo paga lo mismo que PuntosEnJuego")
    void losTramosDicenLoMismoQuePuntosEnJuego() {
        VentanaEntrega ventana = ventanaNocturna();
        List<TramoPuntos> tramos = AgendaDelDiaFinderService.tramosDe(PuntosEnJuego.de(ventana,
                ventana.instanteAncla()));

        for (Instant entrega = ventana.instanteAncla().minus(Duration.ofHours(1));
             entrega.isBefore(ventana.plazoEvidencia()); entrega = entrega.plus(Duration.ofSeconds(30))) {
            Instant enQueInstante = entrega;
            int segunTramos = tramos.stream().filter(tramo -> enQueInstante.isBefore(tramo.hasta())).findFirst()
                    .orElseThrow().puntos();
            assertThat(segunTramos).as("entrega en %s", entrega)
                    .isEqualTo(PuntosEnJuego.de(ventana, entrega).siCompletaAhora());
        }
    }

    @Test
    @DisplayName("sin puntos en juego (estado terminal) o sin plazo (no vence) no hay escala")
    void sinPlazoNoHayTramos() {
        assertThat(AgendaDelDiaFinderService.tramosDe(null)).isEmpty();
        assertThat(AgendaDelDiaFinderService.tramosDe(PuntosEnJuego.de(null, Instant.parse("2026-09-23T15:00:00Z"))))
                .isEmpty();
    }

    @Test
    @DisplayName("reloj a las 02:00 UTC (21:00 en Lima, dia anterior): el habito de la vispera trae su escala")
    void conRelojDeMadrugadaUtcTraeLaEscalaDeLaVispera() {
        VentanaEntrega ventana = ventanaNocturna();
        Instant madrugadaUtc = Instant.parse("2026-09-24T02:00:00Z");
        RegistroHabito registro = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), APRENDIZ,
                HabitoId.of(UUID.randomUUID()), FECHA, 5, TipoDia.DISCIPLINA, false, madrugadaUtc);
        when(tracks.consultarHoyDe(APRENDIZ)).thenReturn(List.of(new TrackDelDiaConCatalogo(registro, "Leer",
                TipoHabito.CHECKBOX, null, LocalTime.of(20, 0), LocalTime.of(22, 0),
                PuntosEnJuego.de(ventana, madrugadaUtc), false, false)));

        HabitoEnJuegoResumen resumen = service.deHoyDe(APRENDIZ).get(0);

        assertThat(resumen.puntosEnJuego()).isEqualTo(10);
        assertThat(resumen.plazo()).isEqualTo(Instant.parse("2026-09-24T05:00:00Z"));
        assertThat(resumen.tramos()).hasSize(5).first()
                .isEqualTo(new TramoPuntos(Instant.parse("2026-09-24T04:52:00Z"), 10));
    }

    @Test
    @DisplayName("la zona es la del progreso del participante, y UTC si no tiene (mismo respaldo que la proyeccion)")
    void zonaDelParticipante() {
        when(progreso.deParticipante(APRENDIZ)).thenReturn(Optional.of(new ProgresoParticipanteHabits(5,
                "America/Lima", RolParticipante.TRAINEE, false, true)));
        UserId sinProgreso = UserId.of(UUID.randomUUID());
        when(progreso.deParticipante(sinProgreso)).thenReturn(Optional.empty());

        assertThat(service.zonaDe(APRENDIZ)).isEqualTo(LIMA);
        assertThat(service.zonaDe(sinProgreso)).isEqualTo(ZoneId.of("UTC"));
    }

    @Test
    @DisplayName("E-290: un habito renombrado sale con el titulo propio y, aparte, el del programa")
    void renombradoLlevaElTituloDelPrograma() {
        RegistroHabito registro = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), APRENDIZ,
                HabitoId.of(UUID.randomUUID()), FECHA, 5, TipoDia.DISCIPLINA, false, Instant.parse("2026-09-23T15:00:00Z"));
        when(tracks.consultarHoyDe(APRENDIZ)).thenReturn(List.of(new TrackDelDiaConCatalogo(registro,
                "Batido de papaya", TipoHabito.CHECKBOX, null, null, null, null, false, true, null, "JUGO VERDE")));

        HabitoEnJuegoResumen resumen = service.deHoyDe(APRENDIZ).get(0);

        assertThat(resumen.titulo()).isEqualTo("Batido de papaya");
        assertThat(resumen.tituloDelPrograma()).isEqualTo("JUGO VERDE");
    }

    /**
     * E-455: "que me falta hoy" se contesta por dimension y por el horario que la persona registro.
     * Sin la hora (la resuelta, con su preferencia) ni la dimension, el acompanante no podia hacer
     * ninguna de las dos cosas.
     */
    @Test
    @DisplayName("E-455: cada habito de hoy sale con su hora resuelta y su dimension, en una sola consulta")
    void traeHoraYDimension() {
        HabitoId caminarId = HabitoId.of(UUID.randomUUID());
        RegistroHabito registro = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), APRENDIZ,
                caminarId, FECHA, 5, TipoDia.DISCIPLINA, false, Instant.parse("2026-09-23T15:00:00Z"));
        when(tracks.consultarHoyDe(APRENDIZ)).thenReturn(List.of(new TrackDelDiaConCatalogo(registro, "Caminar",
                TipoHabito.CHECKBOX, null, LocalTime.of(19, 0), LocalTime.of(21, 0), null, false, false)));
        when(habitos.porIds(java.util.Set.of(caminarId))).thenReturn(List.of(Habito.crearDeSistema(caminarId,
                "Caminar", TipoHabito.CHECKBOX, "CUERPO", ExigenciaEvidencia.OPCIONAL, Instant.EPOCH)));

        HabitoEnJuegoResumen resumen = service.deHoyDe(APRENDIZ).get(0);

        assertThat(resumen.horaInicio()).isEqualTo(LocalTime.of(19, 0));
        assertThat(resumen.categoriaClave()).isEqualTo("CUERPO");
    }

    // spec 037 FR-012 (la agenda de hoy trae medicion.meta) y spec 038 T021: SER lee la meta, lo que lleva y si ya mide con el telefono
    @Test
    @DisplayName("spec 038 T021: un habito de km sale con su meta, lo registrado hoy y la pista de que ya mide con el telefono")
    void kilometrosTraenMetaYLaPistaDeLosPasos() {
        RegistroHabito registro = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), APRENDIZ,
                HabitoId.of(UUID.randomUUID()), FECHA, 5, TipoDia.DISCIPLINA, false, Instant.parse("2026-09-23T15:00:00Z"));
        var medicion = new ConsultarTracksDelDiaConCatalogoUseCase.MedicionDelTrack(
                com.renaser.os.habits.domain.model.medicion.UnidadMedicion.KILOMETROS, null, new java.math.BigDecimal("12.50"),
                new java.math.BigDecimal("5.00"), null, null);
        when(tracks.consultarHoyDe(APRENDIZ)).thenReturn(List.of(new TrackDelDiaConCatalogo(registro, "KILOMETROS DIARIOS",
                TipoHabito.CHECKBOX, null, null, null, null, false, true, "DAILY_KM", null, medicion)));
        when(automaticas.conAlgunaMedicionAutomatica(List.of(APRENDIZ), "DAILY_KM", FECHA)).thenReturn(java.util.Set.of(APRENDIZ));

        var resumen = service.deHoyDe(APRENDIZ).get(0).medicion();

        assertThat(resumen.unidad()).isEqualTo("KILOMETROS");
        assertThat(resumen.meta()).isEqualByComparingTo("5");
        assertThat(resumen.valorDelDia()).isNull();
        assertThat(resumen.conPasosConectados()).isTrue();
    }

    @Test
    @DisplayName("spec 038 T021: sin ningun dia medido por el telefono no hay pasos conectados, y un habito que no mide no lleva medicion ni gasta la consulta")
    void sinPasosConectadosNiMedicion() {
        RegistroHabito km = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), APRENDIZ,
                HabitoId.of(UUID.randomUUID()), FECHA, 5, TipoDia.DISCIPLINA, false, Instant.parse("2026-09-23T15:00:00Z"));
        RegistroHabito lectura = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), APRENDIZ,
                HabitoId.of(UUID.randomUUID()), FECHA, 5, TipoDia.DISCIPLINA, false, Instant.parse("2026-09-23T15:00:00Z"));
        var medicion = new ConsultarTracksDelDiaConCatalogoUseCase.MedicionDelTrack(
                com.renaser.os.habits.domain.model.medicion.UnidadMedicion.KILOMETROS, null, java.math.BigDecimal.ZERO);
        when(tracks.consultarHoyDe(APRENDIZ)).thenReturn(List.of(
                new TrackDelDiaConCatalogo(km, "KILOMETROS DIARIOS", TipoHabito.CHECKBOX, null, null, null, null, false, true, "DAILY_KM", null, medicion),
                new TrackDelDiaConCatalogo(lectura, "Leer", TipoHabito.CHECKBOX, null, null, null, null, false, false)));
        when(automaticas.conAlgunaMedicionAutomatica(List.of(APRENDIZ), "DAILY_KM", FECHA)).thenReturn(java.util.Set.of());

        var resumenes = service.deHoyDe(APRENDIZ);

        assertThat(resumenes.get(0).medicion().conPasosConectados()).isFalse();
        assertThat(resumenes.get(1).medicion()).isNull();
        org.mockito.Mockito.verify(automaticas, org.mockito.Mockito.times(1)).conAlgunaMedicionAutomatica(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("spec 038 T021: si la consulta de las lecturas automaticas falla, la medicion sale sin la pista y la agenda no se cae")
    void siFallaLaPistaLaAgendaSigue() {
        RegistroHabito km = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), APRENDIZ,
                HabitoId.of(UUID.randomUUID()), FECHA, 5, TipoDia.DISCIPLINA, false, Instant.parse("2026-09-23T15:00:00Z"));
        var medicion = new ConsultarTracksDelDiaConCatalogoUseCase.MedicionDelTrack(
                com.renaser.os.habits.domain.model.medicion.UnidadMedicion.KILOMETROS, null, java.math.BigDecimal.ZERO);
        when(tracks.consultarHoyDe(APRENDIZ)).thenReturn(List.of(new TrackDelDiaConCatalogo(km, "KILOMETROS DIARIOS",
                TipoHabito.CHECKBOX, null, null, null, null, false, true, "DAILY_KM", null, medicion)));
        when(automaticas.conAlgunaMedicionAutomatica(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalStateException("base caida"));

        assertThat(service.deHoyDe(APRENDIZ).get(0).medicion().conPasosConectados()).isFalse();
    }
}
