package com.renaser.os.users.application.ports.out.participante;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.application.ports.in.participante.ListTraineesUseCase.ResumenTraineeAdmin;

import java.util.Set;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Query compuesta `usuarios` LEFT JOIN `participantes_programa` — implementa
 * {@link com.renaser.os.users.api.ParticipacionProgramaFinder} desde
 * `application/services`. Devuelve directamente el tipo publico
 * {@link ParticipacionPrograma}: no hay traduccion adicional que hacer entre este
 * puerto y la proyeccion que otros modulos consumen.
 */
public interface ConsultarResumenParticipacionPort {

    /** {@code Optional.empty()} SOLO si el usuario no existe en `usuarios` — ver javadoc de {@link ParticipacionPrograma}. */
    Optional<ParticipacionPrograma> resumenDe(UserId usuarioId);

    List<UserId> miembrosActivosDeCelula(UUID celulaId);

    List<UserId> miembrosDeCelula(UUID celulaId);

    List<UserId> usuariosActivosConRol(Set<UserRole> roles);

    List<ParticipacionProgramaFinder.UsuarioConDiaPrograma> usuariosActivosConDiaPrograma(Set<UserRole> roles);

    List<UserId> participantesInscritosActivos();

    int contarMiembrosDeCelula(UUID celulaId);

    /**
     * Panel admin de aprendices (gap #7 de docs/PLAN_INTEGRACION_FRONTEND.md): pagina de
     * TODOS los usuarios con rol TRAINEE (cualquier estado), con su resumen de programa
     * si tienen fila en `participantes_programa` (siempre deberian, ver D-33, pero LEFT
     * JOIN de todas formas por robustez). Sin filtros: el encargo solo pide "listar,
     * detalle, editar dia" — se agregan filtros el dia que se pidan explicitamente.
     */
    List<ResumenTraineeAdmin> listarAprendices(int offset, int limit);

    long contarAprendices();
}
