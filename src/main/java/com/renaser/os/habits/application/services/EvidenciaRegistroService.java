package com.renaser.os.habits.application.services;

import com.renaser.os.evidence.api.DestinoEvidencia;
import com.renaser.os.evidence.api.RegistrarEvidenciaPort;
import com.renaser.os.evidence.api.RegistrarEvidenciaPort.EvidenciaRegistrada;
import com.renaser.os.evidence.api.RegistrarEvidenciaPort.RegistrarEvidenciaComando;
import com.renaser.os.habits.application.ports.in.registro.SolicitarUrlEvidenciaRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.SubirEvidenciaRegistroUseCase;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.util.NoSuchElementException;

/**
 * Servicio separado de {@link RegistroService} a propósito (CLAUDE.MD §5.4.8, límite
 * de tamaño de clase): {@code RegistroService} ya está cerca del techo de líneas y
 * "subir evidencia" es una operación independiente de "completar" — un hábito con
 * {@code ExigenciaEvidencia.OPCIONAL} puede completarse sin pasar por acá. Cierra D-H6
 * de {@code docs/MODULO_HABITS.md}.
 *
 * <p>Tambien firma la URL de subida ({@link SolicitarUrlEvidenciaRegistroUseCase}): los dos
 * pasos del camino generico de evidencia — pedir la URL y confirmar la evidencia — comparten
 * el mismo chequeo de pertenencia, asi que separarlos en dos servicios duplicaria
 * {@link #requireSelf} sin ganar nada.
 */
@Service
public class EvidenciaRegistroService implements SubirEvidenciaRegistroUseCase,
        SolicitarUrlEvidenciaRegistroUseCase {

    /** Mismo bucket unico que `rocks` y `dia-sin-celular`; lo que separa es el prefijo de ruta. */
    static final String BUCKET_EVIDENCIA = "renaser-files";
    private static final String PREFIJO_RUTA = "evidencia-habitos";
    /** Igual que rocks/racha: la URL es una credencial de escritura, dura lo que dura la subida. */
    private static final Duration VALIDEZ_URL_SUBIDA = Duration.ofMinutes(10);

    private final LoadRegistroHabitoPort loadRegistroPort;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final RegistrarEvidenciaPort registrarEvidenciaPort;
    private final AlmacenamientoPort almacenamientoPort;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public EvidenciaRegistroService(LoadRegistroHabitoPort loadRegistroPort,
                                     ConsultarProgresoParticipanteHabitsPort progresoPort,
                                     RegistrarEvidenciaPort registrarEvidenciaPort,
                                     AlmacenamientoPort almacenamientoPort, IdGenerator idGenerator, Clock clock) {
        this.loadRegistroPort = loadRegistroPort;
        this.progresoPort = progresoPort;
        this.registrarEvidenciaPort = registrarEvidenciaPort;
        this.almacenamientoPort = almacenamientoPort;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /**
     * La clave del objeto lleva un id propio por subida y no solo {@code registroId}: un
     * registro admite mas de una evidencia (la tabla {@code evidencias} no lo limita), y con
     * una clave fija la segunda subida pisaria el archivo de la primera en S3 dejando la fila
     * vieja apuntando a bytes que ya no son suyos.
     */
    @Override
    public UrlEvidenciaRegistro solicitarUrl(SolicitarUrlEvidenciaRegistroCommand command) {
        RegistroHabito registro = requireRegistro(command.registroId());
        requireSelf(command.actorId(), registro.participanteId());

        String ruta = PREFIJO_RUTA + "/" + command.actorId() + "/" + registro.id() + "/" + idGenerator.newId();
        URI url = almacenamientoPort.firmarSubida(ruta, command.tipoContenido(), VALIDEZ_URL_SUBIDA);
        return new UrlEvidenciaRegistro(url, BUCKET_EVIDENCIA, ruta);
    }

    @Override
    @Transactional
    public EvidenciaRegistrada subir(SubirEvidenciaRegistroCommand command) {
        RegistroHabito registro = requireRegistro(command.registroId());
        requireSelf(command.actorId(), registro.participanteId());

        var comando = new RegistrarEvidenciaComando(command.actorId(),
                new DestinoEvidencia.RegistroHabito(registro.id().value()), command.tipo(), command.bucket(),
                command.rutaStorage(), command.contenidoTexto(), command.timestampExif(), command.gpsLat(),
                command.gpsLng(), false, clock.now());
        return registrarEvidenciaPort.registrar(comando);
    }

    private RegistroHabito requireRegistro(RegistroHabitoId id) {
        return loadRegistroPort.byId(id).orElseThrow(() -> new NoSuchElementException("Registro no encontrado: " + id));
    }

    /** Mismo criterio que {@code RegistroService.requireSelf}: pertenencia Y estado de cuenta. */
    private void requireSelf(UserId actorId, UserId participanteId) {
        if (!actorId.equals(participanteId)) {
            throw new NotAuthorizedException("Solo el propio participante puede subir evidencia de sus habitos");
        }
        var progreso = progresoPort.deParticipante(participanteId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + participanteId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
    }
}
