package com.renaser.os.points.application.services;

import com.renaser.os.points.api.DetalleDelSemaforo;
import com.renaser.os.points.application.ports.in.semaforo.ConsultarMiSemaforoUseCase;
import com.renaser.os.points.application.ports.in.semaforo.PausarSemaforoUseCase;
import com.renaser.os.points.application.ports.out.semaforo.PausasDelSemaforoPort;
import com.renaser.os.points.domain.model.semaforo.PausaDeMedicion;
import com.renaser.os.points.domain.model.semaforo.PausaId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ProgramasActivadosFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder.ProgramaActivado;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Pausa con fecha de regreso del semáforo del staff con programa propio (D-168). No borra nada: a
 * diferencia de {@code DELETE /mentor/activate-tracking}, que borra la participación entera, esto
 * solo deja de medir esos días.
 *
 * <p>El permiso declarado ({@code TRACK_PROGRAM_AS_STAFF}) ya deja afuera al aprendiz en el
 * interceptor, pero el interceptor no revisa a MENTOR/ADMIN/ALCHEMIST (hueco A-1): la regla de rol y
 * la de cuenta activa se imponen acá.
 */
@Service
public class PausaDelSemaforoService implements PausarSemaforoUseCase {

    private final UserSummaryFinder userSummaryFinder;
    private final ProgramasActivadosFinder programasFinder;
    private final PausasDelSemaforoPort pausasPort;
    private final ConsultarMiSemaforoUseCase consulta;
    private final Clock clock;

    public PausaDelSemaforoService(UserSummaryFinder userSummaryFinder, ProgramasActivadosFinder programasFinder,
                                   PausasDelSemaforoPort pausasPort, ConsultarMiSemaforoUseCase consulta, Clock clock) {
        this.userSummaryFinder = userSummaryFinder;
        this.programasFinder = programasFinder;
        this.pausasPort = pausasPort;
        this.consulta = consulta;
        this.clock = clock;
    }

    @Override
    @Transactional
    public DetalleDelSemaforo pausar(PausarSemaforoCommand command) {
        UserId actor = command.actorId();
        LocalDate hoy = hoyDe(exigirStaffConPrograma(actor));
        Optional<PausaDeMedicion> enCurso = pausaEnCurso(actor, hoy);
        PausaDeMedicion pausa;
        if (enCurso.isPresent()) {
            pausa = enCurso.get();
            pausa.cambiarHasta(command.hasta(), hoy);
        } else {
            pausa = PausaDeMedicion.iniciar(PausaId.of(UUID.randomUUID()), actor, hoy, command.hasta(), clock.now());
        }
        pausasPort.guardar(pausa);
        return consulta.consultar(actor, ConsultaDelSemaforoService.SEMANAS_POR_DEFECTO);
    }

    @Override
    @Transactional
    public DetalleDelSemaforo reanudar(UserId actorId) {
        LocalDate hoy = hoyDe(exigirStaffConPrograma(actorId));
        pausaEnCurso(actorId, hoy).ifPresent(pausa -> {
            pausa.reanudar(hoy, clock.now());
            pausasPort.guardar(pausa);
        });
        return consulta.consultar(actorId, ConsultaDelSemaforoService.SEMANAS_POR_DEFECTO);
    }

    private ProgramaActivado exigirStaffConPrograma(UserId actorId) {
        UserSummary actor = userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        if (actor.role() == UserRole.TRAINEE) {
            throw new NotAuthorizedException("Para el aprendiz el semaforo es obligatorio: no se puede pausar");
        }
        return programasFinder.de(actorId)
                .filter(p -> p.primeraFecha() != null)
                .orElseThrow(() -> new NoSuchElementException("No hay un programa propio activado"));
    }

    private Optional<PausaDeMedicion> pausaEnCurso(UserId actorId, LocalDate hoy) {
        return pausasPort.de(List.of(actorId)).getOrDefault(actorId, List.of()).stream()
                .filter(p -> p.vigenteEl(hoy))
                .findFirst();
    }

    private LocalDate hoyDe(ProgramaActivado programa) {
        return clock.now().atZone(programa.zona()).toLocalDate();
    }
}
