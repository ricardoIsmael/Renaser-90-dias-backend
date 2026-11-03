package com.renaser.os.users.api;

import com.renaser.os.shared.domain.DuracionDelPrograma;
import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Puerto de entrada publico para leer `participantes_programa` sin que cada modulo
 * mande su propia query nativa contra una tabla ajena (CLAUDE.MD). Reemplaza, cuando
 * cada modulo se refactorice, a las copias locales:
 * {@code points}/{@code phasecontracts}/{@code habits}/{@code rocks}/{@code calendar}/
 * {@code community}/{@code academy}: cada uno tiene hoy su propio
 * {@code ConsultarProgresoParticipante*Port} + enum local {@code RolParticipante}.
 */
public interface ParticipacionProgramaFinder {

    /**
     * {@code Optional.empty()} SOLO si {@code participanteId} no existe en `usuarios`.
     * Un usuario real sin fila de programa devuelve {@code ParticipacionPrograma} con
     * {@code inscrito=false}, nunca vacio — ver javadoc de {@link ParticipacionPrograma}.
     */
    Optional<ParticipacionPrograma> deParticipante(UserId participanteId);

    /**
     * Las participaciones de VARIAS personas en UNA consulta (auditoria de consultas, H-01): quien arma una pantalla con una fila por persona
     * (la lista de «Hábitos de todos», «Todos los aprendices») no puede llamar a {@link #deParticipante} dentro de un {@code for}. Mismo contrato:
     * una persona que no existe en `usuarios` no aparece; una que existe y no esta inscrita aparece con {@code inscrito=false}.
     *
     * <p>El cuerpo por defecto pregunta de a una (para los dobles de prueba que no necesitan la consulta en lote); la implementacion real la sobreescribe.
     */
    default Map<UserId, ParticipacionPrograma> deParticipantes(Collection<UserId> participantes) {
        Map<UserId, ParticipacionPrograma> resultado = new LinkedHashMap<>();
        for (UserId participante : participantes) {
            deParticipante(participante).ifPresent(participacion -> resultado.put(participante, participacion));
        }
        return resultado;
    }

    /** Miembros de la celula cuyo `usuarios.estado = 'ACTIVO'`. */
    List<UserId> miembrosActivosDeCelula(UUID celulaId);

    /**
     * TODOS los miembros de la celula, sin filtrar por estado del usuario. Existe
     * ademas de {@link #miembrosActivosDeCelula} porque son dos preguntas distintas y
     * los consumidores usan una u otra: `calendar` resuelve destinatarios de un evento
     * (solo activos tiene sentido), `community` lista la composicion de la celula
     * (un suspendido sigue perteneciendo a ella).
     */
    List<UserId> miembrosDeCelula(UUID celulaId);

    /**
     * Usuarios ACTIVOS con alguno de esos roles. Operacion EN LOTE a proposito: los
     * consumidores que resuelven audiencias (`calendar`) o barren padrones (`habits`)
     * necesitan una sola consulta, no una por usuario — llamar a
     * {@link #deParticipante} en un bucle seria un N+1.
     */
    List<UserId> usuariosActivosConRol(Set<UserRole> roles);

    /** Igual que {@link #usuariosActivosConRol} pero con el dia de programa de cada uno ({@code null} si no esta inscrito). */
    List<UsuarioConDiaPrograma> usuariosActivosConDiaPrograma(Set<UserRole> roles);

    /** Usuarios ACTIVOS que ademas tienen fila en `participantes_programa`, sin importar el rol. */
    List<UserId> participantesInscritosActivos();

    /** {@code duracionDias}: la duración de SU programa (90 o 365, spec 022); 90 si no está inscrito. */
    record UsuarioConDiaPrograma(UserId id, Integer diaPrograma, int duracionDias) {

        public UsuarioConDiaPrograma(UserId id, Integer diaPrograma) {
            this(id, diaPrograma, DuracionDelPrograma.POR_DEFECTO);
        }
    }

    /** Cantidad total de participantes con esa celula asignada (cualquier estado). */
    int contarMiembrosDeCelula(UUID celulaId);
}
