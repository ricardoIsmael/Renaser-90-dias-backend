package com.renaser.os.community.application.services;

import com.renaser.os.community.api.FotoPropiaDelGrupoFinder;
import com.renaser.os.community.application.ports.in.celula.CambiarFotoDelGrupoUseCase;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.celula.FotoDelGrupoPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.application.ports.out.celula.PrepararFotoDelGrupoPort;
import com.renaser.os.community.domain.model.acompanamiento.ConjuntoAsignaciones;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.celula.FotoDelGrupo;
import com.renaser.os.community.domain.model.celula.FotoSubidaDelGrupo;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * La foto propia de un grupo (D-212, decisión del dueño del 2026-09-27: «Admin y el mentor de ese grupo»).
 *
 * <p><b>Quién la cambia.</b> ADMIN y ALCHEMIST, la de cualquier grupo. El mentor, solo la del grupo que
 * acompaña HOY: su asignación vigente con función MENTOR, la misma fuente que usa {@link MisCelulasService}
 * para decir quién está en un grupo (desde D-141 un mentor puede acompañar varios, y el puntero
 * {@code celulas.mentor_id} nombra uno solo). Nadie más.
 *
 * <p><b>Corregido 2026-09-27.</b> Decía «El Alquimista tampoco, aunque administre los grupos en todo lo
 * demás: el dueño nombró a "Admin"». En la página de decisiones el dueño agregó al Alquimista («sí»), el
 * mismo alcance que ya tiene en el resto de la administración de grupos ({@code UserRole.canManageRoles}).
 *
 * <p><b>Sin {@code @Transactional}.</b> Subir, leer y borrar son llamadas al almacenamiento, que no deben
 * retener una conexión de la base. Cada escritura va en su propia sentencia: {@link FotoDelGrupoPort}
 * reemplaza y devuelve la anterior en un solo UPDATE.
 *
 * <p><b>Orden.</b> Primero se sube el objeto nuevo y después se cambia la referencia: si la subida falla,
 * el grupo sigue con la foto que tenía. Borrar la anterior es lo último y no hace fallar el cambio: un
 * objeto que quedó sin referencia es un costo de centavos, y el cambio ya se hizo.
 */
@Service
public class FotoDelGrupoService implements CambiarFotoDelGrupoUseCase, FotoPropiaDelGrupoFinder {

    private static final Logger log = LoggerFactory.getLogger(FotoDelGrupoService.class);

    private final LoadCelulaPort loadCelulaPort;
    private final LoadAsignacionesPort loadAsignacionesPort;
    private final UserSummaryFinder userSummaryFinder;
    private final FotoDelGrupoPort fotoDelGrupoPort;
    private final PrepararFotoDelGrupoPort prepararFoto;
    private final AlmacenamientoPort almacenamiento;
    private final Clock clock;

    public FotoDelGrupoService(LoadCelulaPort loadCelulaPort, LoadAsignacionesPort loadAsignacionesPort,
                               UserSummaryFinder userSummaryFinder, FotoDelGrupoPort fotoDelGrupoPort,
                               PrepararFotoDelGrupoPort prepararFoto, AlmacenamientoPort almacenamiento,
                               Clock clock) {
        this.loadCelulaPort = loadCelulaPort;
        this.loadAsignacionesPort = loadAsignacionesPort;
        this.userSummaryFinder = userSummaryFinder;
        this.fotoDelGrupoPort = fotoDelGrupoPort;
        this.prepararFoto = prepararFoto;
        this.almacenamiento = almacenamiento;
        this.clock = clock;
    }

    @Override
    public FotoDelGrupo cambiar(CambiarFotoDelGrupoCommand command) {
        requirePuedeCambiarla(command.actorId(), command.celulaId());
        byte[] jpeg = prepararFoto.comoJpegCuadrado(new FotoSubidaDelGrupo(command.foto(), command.tipo()));
        FotoDelGrupo nueva = FotoDelGrupo.nueva(command.celulaId(), clock.now());
        almacenamiento.subir(nueva.ruta(), jpeg, "image/jpeg");
        fotoDelGrupoPort.reemplazar(command.celulaId(), nueva)
                // Dos cambios en el mismo milisegundo darían la misma clave: esa no se borra, es la nueva.
                .filter(anterior -> !anterior.equals(nueva.ruta()))
                .ifPresent(this::borrarLaQueSeDejoDeUsar);
        log.info("[community.foto] {} cambió la foto del grupo {} ({} bytes)", command.actorId(), command.celulaId(),
                jpeg.length);
        return nueva;
    }

    @Override
    public void volverALaDeRenaser(UserId actorId, CelulaId celulaId) {
        requirePuedeCambiarla(actorId, celulaId);
        fotoDelGrupoPort.quitar(celulaId).ifPresent(ruta -> {
            borrarLaQueSeDejoDeUsar(ruta);
            log.info("[community.foto] {} volvió el grupo {} a la foto de Renaser", actorId, celulaId);
        });
    }

    @Override
    public Optional<FotoDelGrupo> actual(UserId actorId, CelulaId celulaId) {
        requirePuedeCambiarla(actorId, celulaId);
        return fotoDelGrupoPort.deGrupo(celulaId);
    }

    /** {@link FotoPropiaDelGrupoFinder}: la lee del almacenamiento; sin el objeto, como si no tuviera. */
    @Override
    public Optional<FotoPropia> fotoDe(UUID grupoId) {
        return fotoDelGrupoPort.deGrupo(CelulaId.of(grupoId))
                .flatMap(foto -> almacenamiento.leer(foto.ruta(), FotoSubidaDelGrupo.PESO_MAXIMO).map(jpeg -> new FotoPropia(jpeg, foto.cambiadaEn())));
    }

    @Override
    public Map<UUID, Instant> cambiadasEn(Collection<UUID> grupos) {
        return fotoDelGrupoPort.deGrupos(grupos.stream().map(CelulaId::of).toList()).entrySet().stream()
                .collect(Collectors.toMap(entrada -> entrada.getKey().value(), entrada -> entrada.getValue().cambiadaEn()));
    }

    /** Cuenta activa (403), el grupo existe (404) y quien pide es ADMIN o su mentor de hoy (403). */
    private void requirePuedeCambiarla(UserId actorId, CelulaId celulaId) {
        UserSummary actor = userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        loadCelulaPort.porId(celulaId).orElseThrow(() -> new NoSuchElementException("Celula no encontrada: " + celulaId));
        if (!actor.role().canManageRoles() && !esSuMentorHoy(actorId, celulaId)) {
            throw new NotAuthorizedException("Solo Administración, el Alquimista o el mentor de este grupo cambian su foto");
        }
    }

    private boolean esSuMentorHoy(UserId actorId, CelulaId celulaId) {
        return ConjuntoAsignaciones.de(loadAsignacionesPort.porCelula(celulaId))
                .mentorVigenteEn(celulaId, clock.now())
                .filter(actorId::equals)
                .isPresent();
    }

    private void borrarLaQueSeDejoDeUsar(String ruta) {
        try {
            almacenamiento.borrar(ruta);
        } catch (RuntimeException e) {
            log.warn("[community.foto] No se pudo borrar la foto anterior {}: queda sin referencia", ruta, e);
        }
    }
}
