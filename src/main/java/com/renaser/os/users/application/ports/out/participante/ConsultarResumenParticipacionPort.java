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
     * Panel admin de personas (gap #7 de docs/PLAN_INTEGRACION_FRONTEND.md): pagina de
     * TODOS los usuarios (cualquier rol, cualquier estado), con su resumen de programa
     * si tienen fila en `participantes_programa` (LEFT JOIN: para el staff normalmente no
     * hay, y por eso el programa de esas filas llega en cero).
     *
     * <p><b>Corregido 2026-09-16 (D-138).</b> Aca decia "TODOS los usuarios con rol TRAINEE",
     * y el SQL filtraba {@code u.rol = 'APRENDIZ'}: los 7 usuarios de staff no aparecian en la
     * pantalla de Personas. Pedido del dueno — "tambien los mentores hacen el recorrido".
     * {@code soloSinGrupo} es la unica parte que sigue mirando el rol, ver abajo.
     *
     * <p><b>Los filtros llegan con el SDD 003, pedidos explicitamente.</b> {@code busqueda} recorta
     * por nombre o correo EN LA BASE, no sobre la pagina ya descargada: buscar en el movil sobre
     * los veinte que vinieron esconde al que esta en la pagina cuatro y hace creer que no existe
     * (ARF-02, V28). {@code soloSinGrupo} es la otra cola operativa de la pantalla de resumen:
     * quien no tiene grupo vigente es justamente a quien hay que ubicar.
     *
     * @param busqueda    {@code null} o vacio = sin recorte. Se compara sin distinguir mayusculas.
     * @param soloSinGrupo {@code true} = solo los <b>aprendices</b> que no tienen celula asignada.
     *                     El rol quedo dentro de ESTE filtro cuando el listado se abrio a todos los
     *                     roles (D-138): a un miembro de staff no se le puede asignar celula, asi
     *                     que sumarlo a la cola daria un numero que no baja nunca.
     */
    List<ResumenTraineeAdmin> listarAprendices(int offset, int limit, String busqueda, boolean soloSinGrupo);

    long contarAprendices(String busqueda, boolean soloSinGrupo);
}
