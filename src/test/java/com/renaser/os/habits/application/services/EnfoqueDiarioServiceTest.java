package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.EnfoqueDiarioPort.DiaSinCelularDeHoy;
import com.renaser.os.habits.api.EnfoqueDiarioPort.EspirituDeHoy;
import com.renaser.os.habits.api.EnfoqueDiarioPort.EstadoEspirituDeHoy;
import com.renaser.os.habits.api.EnfoqueDiarioPort.SantuarioDeHoy;
import com.renaser.os.habits.application.ports.in.espiritu.CompletarPastillaRenacerUseCase;
import com.renaser.os.habits.application.ports.in.espiritu.ConsultarEstadoEspirituUseCase;
import com.renaser.os.habits.application.ports.in.espiritu.ConsultarEstadoEspirituUseCase.DiaEspiritu;
import com.renaser.os.habits.application.ports.in.espiritu.ConsultarEstadoEspirituUseCase.EstadoEspiritu;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituUseCase;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituUseCase.EntregarResumenEspirituCommand;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituUseCase.ResultadoEntrega;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase.TrackDelDiaConCatalogo;
import com.renaser.os.habits.application.ports.in.santuario.IniciarRachaUseCase;
import com.renaser.os.habits.application.ports.in.santuario.IniciarSesionBloqueoUseCase;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.registro.PuntosEnJuego;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.habits.domain.model.santuario.RachaSinCelular;
import com.renaser.os.habits.domain.model.santuario.RachaSinCelularId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EnfoqueDiarioService}: la fachada de {@code habits.api.EnfoqueDiarioPort} decide "hoy" en la
 * zona de la persona y delega todo lo demas. Regla 02: los relojes de estos tests caen a proposito
 * entre 00:00 y 05:00 UTC, donde en Lima todavia es el dia anterior.
 */
class EnfoqueDiarioServiceTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** 22:00 del miercoles 23/09 en Lima; en UTC ya es jueves 24/09. */
    private static final Instant NOCHE_EN_LIMA = Instant.parse("2026-09-24T03:00:00Z");
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 9, 23);
    /** Las 12:00 del miercoles en Lima: el plazo del audio de ese dia. */
    private static final Instant MEDIODIA_MIERCOLES = Instant.parse("2026-09-23T17:00:00Z");
    private static final Instant MEDIODIA_JUEVES = Instant.parse("2026-09-24T17:00:00Z");

    private final ConsultarEstadoEspirituUseCase consultarEspiritu = mock(ConsultarEstadoEspirituUseCase.class);
    private final EntregarResumenEspirituUseCase entregarEspiritu = mock(EntregarResumenEspirituUseCase.class);
    private final ConsultarTracksDelDiaConCatalogoUseCase tracks = mock(ConsultarTracksDelDiaConCatalogoUseCase.class);
    private final IniciarSesionBloqueoUseCase iniciarSesion = mock(IniciarSesionBloqueoUseCase.class);
    private final IniciarRachaUseCase iniciarRacha = mock(IniciarRachaUseCase.class);
    private final EnfoqueDiarioLecturas lecturas = mock(EnfoqueDiarioLecturas.class);

    private EnfoqueDiarioService servicioA(Instant ahora) {
        when(lecturas.zonaNoSuspendida(APRENDIZ)).thenReturn(LIMA);
        return new EnfoqueDiarioService(consultarEspiritu, entregarEspiritu, tracks, iniciarSesion, iniciarRacha,
                lecturas, FixedClock.at(ahora));
    }

    private void conAudios(DiaEspiritu... dias) {
        when(consultarEspiritu.consultar(APRENDIZ)).thenReturn(new EstadoEspiritu(List.of(dias), null));
    }

    private static DiaEspiritu audio(int dia, String estado, Instant fechaLimite, Instant entregadoEn) {
        return new DiaEspiritu(dia, "Audio " + dia, estado, fechaLimite.minusSeconds(5 * 3600), fechaLimite,
                entregadoEn, null, null, null, null);
    }

    private static RegistroHabito registro(HabitoId habito) {
        return RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), APRENDIZ, habito, MIERCOLES, 20,
                TipoDia.TODOS, false, NOCHE_EN_LIMA);
    }

    private static TrackDelDiaConCatalogo track(RegistroHabito registro, String titulo, TipoHabito tipo,
                                                LocalTime disparo, LocalTime limite, PuntosEnJuego enJuego) {
        return new TrackDelDiaConCatalogo(registro, titulo, tipo, null, disparo, limite, enJuego, false, false);
    }

    private static Habito habito(HabitoId id, String titulo) {
        return Habito.crearDeSistema(id, titulo, TipoHabito.CHECKBOX, "MENTE", ExigenciaEvidencia.OPCIONAL,
                NOCHE_EN_LIMA);
    }

    @Test
    @DisplayName("a las 03:00 UTC el audio de hoy es el del dia de Lima, no el del servidor, y dice los puntos")
    void espirituDeHoyEnSuZona() {
        Instant mediodiaMartes = MEDIODIA_MIERCOLES.minusSeconds(86_400);
        conAudios(audio(4, "SUBMITTED", mediodiaMartes, mediodiaMartes.minusSeconds(3600)),
                audio(5, "CURRENT", MEDIODIA_MIERCOLES, null));
        HabitoId pastilla = HabitoId.of(UUID.randomUUID());
        when(lecturas.habitoPorClave(CompletarPastillaRenacerUseCase.CLAVE_SISTEMA_PASTILLA_RENACER))
                .thenReturn(Optional.of(habito(pastilla, "Pastilla Renacer")));
        when(tracks.consultarHoyDe(APRENDIZ)).thenReturn(List.of(track(registro(pastilla), "Pastilla Renacer",
                TipoHabito.JOURNALING, null, null, new PuntosEnJuego(6, 10, MEDIODIA_JUEVES))));

        EspirituDeHoy espiritu = servicioA(NOCHE_EN_LIMA).espirituDeHoy(APRENDIZ);

        assertThat(espiritu.hoy()).isEqualTo(MIERCOLES);
        assertThat(espiritu.estado()).isEqualTo(EstadoEspirituDeHoy.PENDIENTE);
        assertThat(espiritu.audio().dia()).isEqualTo(5);
        assertThat(espiritu.horaDesbloqueo()).isEqualTo(LocalTime.of(7, 0));
        assertThat(espiritu.horaLimite()).isEqualTo(LocalTime.of(12, 0));
        assertThat(espiritu.puntosPastillaRenacer()).isEqualTo(6);
    }

    @Test
    @DisplayName("antes de las 07:00 de Lima, sin audio de hoy, dice que todavia no se desbloquea")
    void antesDeLaHoraDeDesbloqueo() {
        conAudios(audio(5, "SUBMITTED", MEDIODIA_MIERCOLES, MEDIODIA_MIERCOLES.minusSeconds(3600)));

        EspirituDeHoy espiritu = servicioA(Instant.parse("2026-09-24T10:00:00Z")).espirituDeHoy(APRENDIZ);

        assertThat(espiritu.hoy()).isEqualTo(MIERCOLES.plusDays(1));
        assertThat(espiritu.estado()).isEqualTo(EstadoEspirituDeHoy.ANTES_DE_LA_HORA_DE_DESBLOQUEO);
        assertThat(espiritu.audio()).isNull();
        assertThat(espiritu.puntosPastillaRenacer()).isNull();
    }

    @Test
    @DisplayName("una entrega tardia (CURRENT con entregadoEn) se informa como fuera de plazo")
    void entregaTardia() {
        conAudios(audio(5, "CURRENT", MEDIODIA_MIERCOLES, MEDIODIA_MIERCOLES.plusSeconds(3600)));

        EspirituDeHoy espiritu = servicioA(NOCHE_EN_LIMA).espirituDeHoy(APRENDIZ);

        assertThat(espiritu.estado()).isEqualTo(EstadoEspirituDeHoy.ENTREGADO_FUERA_DE_PLAZO);
    }

    @Test
    @DisplayName("entregar delega en el caso de uso de la app y devuelve si fue a tiempo")
    void entregarDelega() {
        when(entregarEspiritu.entregar(any())).thenReturn(new ResultadoEntrega(true));

        boolean aTiempo = servicioA(NOCHE_EN_LIMA).entregarResumenEspiritu(APRENDIZ, 5, "Aprendi a respirar");

        assertThat(aTiempo).isTrue();
        verify(entregarEspiritu).entregar(new EntregarResumenEspirituCommand(APRENDIZ, 5, "Aprendi a respirar"));
    }

    @Test
    @DisplayName("el Santuario se puede iniciar desde su disparo en la hora de Lima, y no si ya tiene sesion")
    void santuariosDeHoy() {
        RegistroHabito libre = registro(HabitoId.of(UUID.randomUUID()));
        RegistroHabito conSesion = registro(HabitoId.of(UUID.randomUUID()));
        when(tracks.consultarHoyDe(APRENDIZ)).thenReturn(List.of(
                track(libre, "Santuario", TipoHabito.BLOQUEO, LocalTime.of(20, 0), LocalTime.of(21, 0), null),
                track(conSesion, "Otro", TipoHabito.BLOQUEO, null, null, null),
                track(registro(HabitoId.of(UUID.randomUUID())), "Leer", TipoHabito.CHECKBOX, null, null, null)));
        when(lecturas.tieneSesion(conSesion.id())).thenReturn(true);

        List<SantuarioDeHoy> santuarios = servicioA(NOCHE_EN_LIMA).santuariosDeHoy(APRENDIZ);

        assertThat(santuarios).hasSize(2);
        assertThat(santuarios.get(0).iniciable()).isTrue();
        assertThat(santuarios.get(0).iniciableDesde()).isEqualTo(Instant.parse("2026-09-24T01:00:00Z"));
        assertThat(santuarios.get(1).iniciable()).isFalse();
        assertThat(santuarios.get(1).iniciableDesde()).isNull();
    }

    @Test
    @DisplayName("con una racha en curso, el Dia sin celular de hoy no se puede iniciar")
    void diaSinCelularConRachaEnCurso() {
        HabitoId sinCelular = HabitoId.of(UUID.randomUUID());
        RegistroHabito deHoy = registro(sinCelular);
        when(lecturas.habitoPorClave(RachaService.CLAVE_SISTEMA_SIN_CELULAR))
                .thenReturn(Optional.of(habito(sinCelular, "Dia sin celular")));
        when(tracks.consultarHoyDe(APRENDIZ)).thenReturn(List.of(
                track(deHoy, "Dia sin celular", TipoHabito.CHECKBOX, null, null, null)));
        Instant inicio = NOCHE_EN_LIMA.minusSeconds(7200);
        when(lecturas.rachaActiva(APRENDIZ)).thenReturn(Optional.of(RachaSinCelular.iniciar(
                RachaSinCelularId.of(UUID.randomUUID()), APRENDIZ, deHoy.id(), 6, inicio)));

        DiaSinCelularDeHoy dia = servicioA(NOCHE_EN_LIMA).diaSinCelularDeHoy(APRENDIZ);

        assertThat(dia.registroId()).isEqualTo(deHoy.id().value());
        assertThat(dia.iniciable()).isFalse();
        assertThat(dia.rachaEnCursoDesde()).isEqualTo(inicio);
        assertThat(dia.rachaEnCursoHoras()).isEqualTo(6);
        assertThat(dia.metasValidas()).isEqualTo(RachaSinCelular.HITOS);
    }

    @Test
    @DisplayName("sin racha en curso y con el registro pendiente, el Dia sin celular se puede iniciar")
    void diaSinCelularIniciable() {
        HabitoId sinCelular = HabitoId.of(UUID.randomUUID());
        when(lecturas.habitoPorClave(RachaService.CLAVE_SISTEMA_SIN_CELULAR))
                .thenReturn(Optional.of(habito(sinCelular, "Dia sin celular")));
        when(tracks.consultarHoyDe(APRENDIZ)).thenReturn(List.of(
                track(registro(sinCelular), "Dia sin celular", TipoHabito.CHECKBOX, null, null, null)));
        when(lecturas.rachaActiva(APRENDIZ)).thenReturn(Optional.empty());

        assertThat(servicioA(NOCHE_EN_LIMA).diaSinCelularDeHoy(APRENDIZ).iniciable()).isTrue();
    }
}
