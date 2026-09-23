package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.EnfoqueDiarioPort;
import com.renaser.os.habits.application.ports.in.espiritu.CompletarPastillaRenacerUseCase;
import com.renaser.os.habits.application.ports.in.espiritu.ConsultarEstadoEspirituUseCase;
import com.renaser.os.habits.application.ports.in.espiritu.ConsultarEstadoEspirituUseCase.DiaEspiritu;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituUseCase;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituUseCase.EntregarResumenEspirituCommand;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase.TrackDelDiaConCatalogo;
import com.renaser.os.habits.application.ports.in.santuario.IniciarRachaUseCase;
import com.renaser.os.habits.application.ports.in.santuario.IniciarRachaUseCase.IniciarRachaCommand;
import com.renaser.os.habits.application.ports.in.santuario.IniciarSesionBloqueoUseCase;
import com.renaser.os.habits.application.ports.in.santuario.IniciarSesionBloqueoUseCase.IniciarSesionBloqueoCommand;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.habits.domain.model.santuario.RachaSinCelular;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementa {@link EnfoqueDiarioPort}: fachada delgada sobre los casos de uso de Espiritu,
 * Santuario y Dia sin celular, mismo patron que {@link AgendaDelDiaFinderService}.
 *
 * <p><b>Sin reglas propias.</b> Las horas de Espiritu son las constantes de
 * {@link EspirituService}; el "dia calendario" de un audio es el de su plazo, igual que
 * {@code EspirituService.diaCalendarioDe}; los habitos de hoy y su horario resuelto salen de
 * {@link ConsultarTracksDelDiaConCatalogoUseCase}; si un registro se puede iniciar lo dice
 * {@code EstadoRegistro.puedeIniciar}; las metas de la racha son {@link RachaSinCelular#HITOS}.
 * Lo unico que se arma aca es {@link #iniciableDesde}, un pre-chequeo para no ofrecer un boton que
 * va a fallar: al confirmar decide {@code SantuarioService.iniciar}, que lo vuelve a evaluar.
 *
 * <p><b>Por que {@link #espirituDeHoy} usa el caso de uso con efectos.</b> {@code consultar} avanza
 * la maquina perezosa de Espiritu en cada lectura, a proposito (su javadoc). Una lectura "pura"
 * obligaria a copiar {@code asegurarAvance} para adivinar que veria la persona al abrir la app; el
 * avance es idempotente, es exactamente lo que la app hace al abrir Training, y la propia entrega
 * lo corre antes de escribir. Copiar la regla seria peor que ejecutarla.
 */
@Service
public class EnfoqueDiarioService implements EnfoqueDiarioPort {

    private final ConsultarEstadoEspirituUseCase consultarEspiritu;
    private final EntregarResumenEspirituUseCase entregarEspiritu;
    private final ConsultarTracksDelDiaConCatalogoUseCase tracksDelDia;
    private final IniciarSesionBloqueoUseCase iniciarSesionBloqueo;
    private final IniciarRachaUseCase iniciarRacha;
    private final EnfoqueDiarioLecturas lecturas;
    private final Clock clock;

    public EnfoqueDiarioService(ConsultarEstadoEspirituUseCase consultarEspiritu,
                                EntregarResumenEspirituUseCase entregarEspiritu,
                                ConsultarTracksDelDiaConCatalogoUseCase tracksDelDia,
                                IniciarSesionBloqueoUseCase iniciarSesionBloqueo, IniciarRachaUseCase iniciarRacha,
                                EnfoqueDiarioLecturas lecturas, Clock clock) {
        this.consultarEspiritu = consultarEspiritu;
        this.entregarEspiritu = entregarEspiritu;
        this.tracksDelDia = tracksDelDia;
        this.iniciarSesionBloqueo = iniciarSesionBloqueo;
        this.iniciarRacha = iniciarRacha;
        this.lecturas = lecturas;
        this.clock = clock;
    }

    @Override
    public EspirituDeHoy espirituDeHoy(UserId participanteId) {
        List<DiaEspiritu> dias = consultarEspiritu.consultar(participanteId).dias();
        ZoneId zona = lecturas.zonaNoSuspendida(participanteId);
        Instant ahora = clock.now();
        LocalDate hoy = ahora.atZone(zona).toLocalDate();
        Optional<DiaEspiritu> deHoy = dias.stream()
                .filter(dia -> dia.fechaLimite() != null && dia.fechaLimite().atZone(zona).toLocalDate().equals(hoy))
                .findFirst();
        EstadoEspirituDeHoy estado = deHoy.map(EnfoqueDiarioService::estadoDe)
                .orElseGet(() -> ahora.atZone(zona).toLocalTime().isBefore(EspirituService.HORA_DESBLOQUEO)
                        ? EstadoEspirituDeHoy.ANTES_DE_LA_HORA_DE_DESBLOQUEO : EstadoEspirituDeHoy.SIN_AUDIO_HOY);
        AudioDeHoy audio = deHoy.map(dia -> new AudioDeHoy(dia.dia(), dia.titulo(), dia.fechaLimite(),
                dia.entregadoEn())).orElse(null);
        Integer puntos = estado == EstadoEspirituDeHoy.PENDIENTE ? puntosDePastillaRenacer(participanteId) : null;
        return new EspirituDeHoy(zona, hoy, EspirituService.HORA_DESBLOQUEO, EspirituService.HORA_LIMITE, estado,
                audio, puntos);
    }

    @Override
    public boolean entregarResumenEspiritu(UserId actorId, int diaAudio, String resumen) {
        return entregarEspiritu.entregar(new EntregarResumenEspirituCommand(actorId, diaAudio, resumen)).aTiempo();
    }

    @Override
    public List<SantuarioDeHoy> santuariosDeHoy(UserId participanteId) {
        ZoneId zona = lecturas.zonaNoSuspendida(participanteId);
        return tracksDelDia.consultarHoyDe(participanteId).stream()
                .filter(track -> track.tipoHabito() == TipoHabito.BLOQUEO)
                .map(track -> new SantuarioDeHoy(zona, track.registro().id().value(), track.tituloHabito(),
                        track.registro().estado().puedeIniciar() && !lecturas.tieneSesion(track.registro().id()),
                        iniciableDesde(track, zona)))
                .toList();
    }

    @Override
    public void iniciarSantuario(UserId actorId, UUID registroId) {
        iniciarSesionBloqueo.iniciar(new IniciarSesionBloqueoCommand(actorId, RegistroHabitoId.of(registroId)));
    }

    @Override
    public DiaSinCelularDeHoy diaSinCelularDeHoy(UserId participanteId) {
        ZoneId zona = lecturas.zonaNoSuspendida(participanteId);
        Optional<RachaSinCelular> enCurso = lecturas.rachaActiva(participanteId);
        Optional<TrackDelDiaConCatalogo> track = lecturas.habitoPorClave(RachaService.CLAVE_SISTEMA_SIN_CELULAR)
                .flatMap(habito -> deHoyDelHabito(participanteId, habito));
        boolean iniciable = track.isPresent() && track.get().registro().estado().puedeIniciar() && enCurso.isEmpty();
        return new DiaSinCelularDeHoy(zona, track.map(t -> t.registro().id().value()).orElse(null),
                track.map(TrackDelDiaConCatalogo::tituloHabito).orElse(null), iniciable,
                enCurso.map(RachaSinCelular::iniciadaEn).orElse(null),
                enCurso.map(RachaSinCelular::horasObjetivo).orElse(null), RachaSinCelular.HITOS);
    }

    @Override
    public void iniciarDiaSinCelular(UserId actorId, UUID registroId, int horasObjetivo) {
        iniciarRacha.iniciar(new IniciarRachaCommand(actorId, RegistroHabitoId.of(registroId), horasObjetivo));
    }

    private static EstadoEspirituDeHoy estadoDe(DiaEspiritu dia) {
        return switch (dia.estado()) {
            case "SUBMITTED" -> EstadoEspirituDeHoy.ENTREGADO_A_TIEMPO;
            case "MISSED" -> EstadoEspirituDeHoy.PERDIDO;
            // Una entrega tardia deja el registro PENDIENTE con entregadoEn puesto (RegistroEspiritu.entregar).
            default -> dia.entregadoEn() != null ? EstadoEspirituDeHoy.ENTREGADO_FUERA_DE_PLAZO
                    : EstadoEspirituDeHoy.PENDIENTE;
        };
    }

    private Integer puntosDePastillaRenacer(UserId participanteId) {
        return lecturas.habitoPorClave(CompletarPastillaRenacerUseCase.CLAVE_SISTEMA_PASTILLA_RENACER)
                .flatMap(habito -> deHoyDelHabito(participanteId, habito))
                .map(TrackDelDiaConCatalogo::puntosEnJuego)
                .map(enJuego -> enJuego.siCompletaAhora())
                .orElse(null);
    }

    private Optional<TrackDelDiaConCatalogo> deHoyDelHabito(UserId participanteId, Habito habito) {
        return tracksDelDia.consultarHoyDe(participanteId).stream()
                .filter(track -> track.registro().habitoId().equals(habito.id()))
                .findFirst();
    }

    /**
     * El mismo instante de disparo que arma {@code SantuarioService.resolverVentana} con el horario
     * resuelto de la proyeccion; sin disparo o sin limite, ese servicio no pone restriccion horaria.
     */
    private static Instant iniciableDesde(TrackDelDiaConCatalogo track, ZoneId zona) {
        if (track.horaDisparo() == null || track.horaLimite() == null) {
            return null;
        }
        return track.registro().fechaEjecucion().atStartOfDay(zona).toInstant()
                .plus(Duration.ofMinutes(track.horaDisparo().getHour() * 60L + track.horaDisparo().getMinute()));
    }
}
