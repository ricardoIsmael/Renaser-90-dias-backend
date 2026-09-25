package com.renaser.os.points.application.services;

import com.renaser.os.points.api.DetalleDelSemaforo;
import com.renaser.os.points.api.PausaDelSemaforo;
import com.renaser.os.points.api.SemaforoFinder;
import com.renaser.os.points.api.SemanaCerrada;
import com.renaser.os.points.api.SemanaDelSemaforo;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.points.application.ports.in.semaforo.ConsultarMiSemaforoUseCase;
import com.renaser.os.points.application.ports.out.semaforo.CargarDiasDelSemaforoPort;
import com.renaser.os.points.application.ports.out.semaforo.SemanasDelSemaforoPort;
import com.renaser.os.points.application.services.LecturaDeMediciones.Lectura;
import com.renaser.os.points.domain.model.semaforo.FotoSemanal;
import com.renaser.os.points.domain.model.semaforo.MedicionDeLaPersona;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Lectura del semáforo (D-168): implementa {@link SemaforoFinder} para los otros módulos y la
 * autoconsulta de la propia persona. Solo LEE lo que el barrido horario ya guardó: nada se recalcula
 * en una lectura, y una consulta por tabla sirve a toda la colección pedida.
 */
@Service
public class ConsultaDelSemaforoService implements SemaforoFinder, ConsultarMiSemaforoUseCase {

    static final int SEMANAS_POR_DEFECTO = 8;
    private static final int SEMANAS_MAXIMAS = 13;
    private static final ZoneId ZONA_POR_DEFECTO = ZoneId.of("America/Lima");

    private final LecturaDeMediciones lectura;
    private final UserSummaryFinder userSummaryFinder;
    private final CargarDiasDelSemaforoPort diasPort;
    private final SemanasDelSemaforoPort semanasPort;

    public ConsultaDelSemaforoService(LecturaDeMediciones lectura, UserSummaryFinder userSummaryFinder,
                                      CargarDiasDelSemaforoPort diasPort, SemanasDelSemaforoPort semanasPort) {
        this.lectura = lectura;
        this.userSummaryFinder = userSummaryFinder;
        this.diasPort = diasPort;
        this.semanasPort = semanasPort;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UserId, VentanaDelSemaforo> vigenteDe(Collection<UserId> participantes) {
        Map<UserId, VentanaDelSemaforo> resultado = new LinkedHashMap<>();
        lectura.vigenteDe(participantes).forEach((id, l) -> resultado.put(id, l.medicion().vigente()));
        return resultado;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UserId, VentanaDelSemaforo> semanaDe(Collection<UserId> participantes, LocalDate semanaHasta) {
        LocalDate semanaDesde = SemanaDelSemaforo.desde(semanaHasta);
        Map<UserId, Lectura> lecturas = lectura.de(participantes, hoy -> semanaDesde, hoy -> semanaHasta);
        Map<UserId, FotoSemanal> fotos = lecturas.isEmpty() ? Map.of()
                : semanasPort.deLaSemana(lecturas.keySet(), semanaHasta);
        Map<UserId, VentanaDelSemaforo> resultado = new LinkedHashMap<>();
        lecturas.forEach((id, l) -> resultado.put(id, l.medicion().semana(semanaHasta, fotos.get(id))));
        return resultado;
    }

    @Override
    @Transactional(readOnly = true)
    public DetalleDelSemaforo detalleDe(UserId participante, int semanas) {
        boolean obligatorio = userSummaryFinder.findById(participante)
                .map(u -> u.role() == UserRole.TRAINEE).orElse(false);
        return detalle(participante, obligatorio, semanas);
    }

    @Override
    @Transactional(readOnly = true)
    public DetalleDelSemaforo consultar(UserId actorId, int semanas) {
        UserSummary actor = exigirCuentaActiva(actorId);
        return detalle(actorId, actor.role() == UserRole.TRAINEE, semanas);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResumenParaHoy> resumenParaHoy(UserId actorId) {
        return Optional.ofNullable(lectura.vigenteDe(List.of(actorId)).get(actorId))
                .map(Lectura::medicion)
                .map(medicion -> {
                    VentanaDelSemaforo vigente = medicion.vigente();
                    return new ResumenParaHoy(vigente.color(), vigente.porcentaje(), vigente.diasConDatos(),
                            pausaEnCurso(medicion) != null, vigente.dias());
                });
    }

    private DetalleDelSemaforo detalle(UserId participante, boolean obligatorio, int semanas) {
        Lectura suya = lectura.vigenteDe(List.of(participante)).get(participante);
        if (suya == null) {
            return DetalleDelSemaforo.noAplica(ZONA_POR_DEFECTO, obligatorio);
        }
        List<SemanaCerrada> historial = semanasPort.ultimasDe(participante, Math.clamp(semanas, 1, SEMANAS_MAXIMAS))
                .stream().map(FotoSemanal::aSemanaCerrada).toList();
        return new DetalleDelSemaforo(true, obligatorio, suya.zona(), pausaEnCurso(suya.medicion()),
                suya.medicion().vigente(), historial, diasPort.ultimoCalculoDe(participante).orElse(null));
    }

    /** La pausa que rige hoy, si hay una; es la que se muestra y la que se puede cambiar. */
    static PausaDelSemaforo pausaEnCurso(MedicionDeLaPersona medicion) {
        return medicion.calendario().pausas().stream()
                .filter(p -> p.vigenteEl(medicion.hoyLocal()))
                .findFirst()
                .map(p -> new PausaDelSemaforo(p.desde(), p.hasta()))
                .orElse(null);
    }

    private UserSummary exigirCuentaActiva(UserId actorId) {
        UserSummary actor = userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        return actor;
    }
}
