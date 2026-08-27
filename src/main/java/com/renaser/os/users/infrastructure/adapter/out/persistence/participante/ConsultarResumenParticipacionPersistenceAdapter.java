package com.renaser.os.users.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.participante.ListTraineesUseCase.ResumenTraineeAdmin;
import com.renaser.os.users.application.ports.out.participante.ConsultarResumenParticipacionPort;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementacion CANONICA (a diferencia de las copias locales de `points`/`phasecontracts`/
 * `habits`/`rocks`/`calendar`/`community`, ver CLAUDE.MD) de la lectura compuesta
 * `usuarios` LEFT JOIN `participantes_programa`. Nunca reemplaza esas copias por si
 * misma — eso lo hace el dueño del repo cuando refactorice cada modulo consumidor.
 *
 * <p><b>FROM `usuarios`, LEFT JOIN `participantes_programa` — nunca al reves.</b> Mismo
 * criterio (y misma razon: el programa es opcional para todo rol que no sea APRENDIZ)
 * que {@code calendar.ConsultarProgresoParticipanteCalendarPersistenceAdapter}.
 */
@Component
class ConsultarResumenParticipacionPersistenceAdapter implements ConsultarResumenParticipacionPort {

    private static final ZoneId ZONA_POR_DEFECTO = ZoneId.of("America/Lima");

    private static final String QUERY_RESUMEN = """
            SELECT (pp.usuario_id IS NOT NULL) AS inscrito,
                   COALESCE(pp.dia_programa, 0) AS dia_programa,
                   pp.fecha_inicio,
                   COALESCE(pp.timezone, 'America/Lima') AS timezone,
                   COALESCE(pp.fase::text, 'FASE_1_RENACER') AS fase,
                   pp.celula_id,
                   pp.mentor_id,
                   u.rol,
                   u.estado
            FROM renaser.usuarios u
            LEFT JOIN renaser.participantes_programa pp ON pp.usuario_id = u.id
            WHERE u.id = ?1
            """;

    private static final String QUERY_MIEMBROS_ACTIVOS = """
            SELECT pp.usuario_id
            FROM renaser.participantes_programa pp
            JOIN renaser.usuarios u ON u.id = pp.usuario_id
            WHERE pp.celula_id = ?1 AND u.estado = 'ACTIVO'
            """;

    private static final String QUERY_MIEMBROS_TODOS = """
            SELECT usuario_id FROM renaser.participantes_programa WHERE celula_id = ?1
            """;

    private static final String QUERY_ACTIVOS_POR_ROL = """
            SELECT id FROM renaser.usuarios WHERE estado = 'ACTIVO' AND rol = ANY (?1)
            """;

    private static final String QUERY_ACTIVOS_POR_ROL_CON_DIA = """
            SELECT u.id, pp.dia_programa
            FROM renaser.usuarios u
            LEFT JOIN renaser.participantes_programa pp ON pp.usuario_id = u.id
            WHERE u.estado = 'ACTIVO' AND u.rol = ANY (?1)
            """;

    private static final String QUERY_INSCRITOS_ACTIVOS = """
            SELECT pp.usuario_id
            FROM renaser.participantes_programa pp
            JOIN renaser.usuarios u ON u.id = pp.usuario_id
            WHERE u.estado = 'ACTIVO'
            """;

    private static final String QUERY_CONTAR_MIEMBROS = """
            SELECT COUNT(*) FROM renaser.participantes_programa WHERE celula_id = ?1
            """;

    /** Panel admin de aprendices (gap #7): todos los TRAINEE, con o sin fila de programa. */
    private static final String QUERY_LISTAR_APRENDICES = """
            SELECT u.id, u.nombre_completo, u.email, u.estado,
                   COALESCE(pp.dia_programa, 0) AS dia_programa,
                   COALESCE(pp.fase::text, 'FASE_1_RENACER') AS fase,
                   pp.celula_id, pp.mentor_id
            FROM renaser.usuarios u
            LEFT JOIN renaser.participantes_programa pp ON pp.usuario_id = u.id
            WHERE u.rol = 'APRENDIZ'
            ORDER BY u.nombre_completo
            LIMIT ?1 OFFSET ?2
            """;

    private static final String QUERY_CONTAR_APRENDICES = """
            SELECT COUNT(*) FROM renaser.usuarios WHERE rol = 'APRENDIZ'
            """;

    private final EntityManager entityManager;

    ConsultarResumenParticipacionPersistenceAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<ParticipacionPrograma> resumenDe(UserId usuarioId) {
        List<Object[]> filas = entityManager.createNativeQuery(QUERY_RESUMEN)
                .setParameter(1, usuarioId.value())
                .getResultList();
        if (filas.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(aResumen(usuarioId, filas.get(0)));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<UserId> miembrosActivosDeCelula(UUID celulaId) {
        List<Object> filas = entityManager.createNativeQuery(QUERY_MIEMBROS_ACTIVOS)
                .setParameter(1, celulaId)
                .getResultList();
        return filas.stream().map(fila -> UserId.of(aUuid(fila))).collect(Collectors.toList());
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<UserId> miembrosDeCelula(UUID celulaId) {
        List<Object> filas = entityManager.createNativeQuery(QUERY_MIEMBROS_TODOS)
                .setParameter(1, celulaId)
                .getResultList();
        return filas.stream().map(fila -> UserId.of(aUuid(fila))).collect(Collectors.toList());
    }

    /**
     * `= ANY (?)` con un array de texto en vez de un `IN (...)` armado a mano: evita
     * concatenar placeholders y deja el enum nativo `rol_usuario` comparandose contra
     * texto, que Postgres resuelve sin cast explicito.
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<UserId> usuariosActivosConRol(Set<UserRole> roles) {
        if (roles.isEmpty()) {
            return List.of();
        }
        List<Object> filas = entityManager.createNativeQuery(QUERY_ACTIVOS_POR_ROL)
                .setParameter(1, clavesDe(roles))
                .getResultList();
        return filas.stream().map(fila -> UserId.of(aUuid(fila))).collect(Collectors.toList());
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ParticipacionProgramaFinder.UsuarioConDiaPrograma> usuariosActivosConDiaPrograma(Set<UserRole> roles) {
        if (roles.isEmpty()) {
            return List.of();
        }
        List<Object[]> filas = entityManager.createNativeQuery(QUERY_ACTIVOS_POR_ROL_CON_DIA)
                .setParameter(1, clavesDe(roles))
                .getResultList();
        return filas.stream()
                .map(fila -> new ParticipacionProgramaFinder.UsuarioConDiaPrograma(UserId.of(aUuid(fila[0])),
                        fila[1] == null ? null : ((Number) fila[1]).intValue()))
                .collect(Collectors.toList());
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<UserId> participantesInscritosActivos() {
        List<Object> filas = entityManager.createNativeQuery(QUERY_INSCRITOS_ACTIVOS).getResultList();
        return filas.stream().map(fila -> UserId.of(aUuid(fila))).collect(Collectors.toList());
    }

    /** Traduce el enum Java a las etiquetas reales de `rol_usuario` en Postgres. */
    private static String[] clavesDe(Set<UserRole> roles) {
        return roles.stream().map(ConsultarResumenParticipacionPersistenceAdapter::aClave).toArray(String[]::new);
    }

    private static String aClave(UserRole rol) {
        return switch (rol) {
            case ALCHEMIST -> "ALQUIMISTA";
            case ADMIN -> "ADMIN";
            case MENTOR_LEAD -> "LIDER_MENTORES";
            case MENTOR -> "MENTOR";
            case TRAINEE -> "APRENDIZ";
        };
    }

    @Override
    public int contarMiembrosDeCelula(UUID celulaId) {
        Number total = (Number) entityManager.createNativeQuery(QUERY_CONTAR_MIEMBROS)
                .setParameter(1, celulaId)
                .getSingleResult();
        return total.intValue();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ResumenTraineeAdmin> listarAprendices(int offset, int limit) {
        List<Object[]> filas = entityManager.createNativeQuery(QUERY_LISTAR_APRENDICES)
                .setParameter(1, limit)
                .setParameter(2, offset)
                .getResultList();
        return filas.stream().map(this::aResumenTraineeAdmin).collect(Collectors.toList());
    }

    @Override
    public long contarAprendices() {
        Number total = (Number) entityManager.createNativeQuery(QUERY_CONTAR_APRENDICES).getSingleResult();
        return total.longValue();
    }

    private ResumenTraineeAdmin aResumenTraineeAdmin(Object[] fila) {
        UserId id = UserId.of(aUuid(fila[0]));
        String fullName = String.valueOf(fila[1]);
        String email = String.valueOf(fila[2]);
        boolean suspendido = "SUSPENDIDO".equals(String.valueOf(fila[3]));
        int diaPrograma = ((Number) fila[4]).intValue();
        FasePrograma fase = mapearFase(String.valueOf(fila[5]));
        UUID celulaId = fila[6] == null ? null : aUuid(fila[6]);
        UserId mentorId = fila[7] == null ? null : UserId.of(aUuid(fila[7]));
        return new ResumenTraineeAdmin(id, fullName, email,
                suspendido ? UserStatus.SUSPENDED : UserStatus.ACTIVE,
                diaPrograma, fase, celulaId, mentorId);
    }

    /**
     * String.valueOf(...) para `rol`/`estado`/`fase` (tipos ENUM nativos de Postgres,
     * pueden llegar como String o PGobject segun la ruta de Hibernate/pgjdbc — mismo
     * criterio ya verificado por `rocks`/`phasecontracts` contra Postgres real).
     */
    private ParticipacionPrograma aResumen(UserId usuarioId, Object[] fila) {
        boolean inscrito = Boolean.TRUE.equals(fila[0]);
        int diaPrograma = ((Number) fila[1]).intValue();
        LocalDate fechaInicio = inscrito ? aLocalDate(fila[2]) : null;
        ZoneId zona = fila[3] == null ? ZONA_POR_DEFECTO : ZoneId.of(String.valueOf(fila[3]));
        FasePrograma fase = mapearFase(String.valueOf(fila[4]));
        UUID celulaId = fila[5] == null ? null : aUuid(fila[5]);
        UserId mentorId = fila[6] == null ? null : UserId.of(aUuid(fila[6]));
        UserRole rol = mapearRol(String.valueOf(fila[7]));
        boolean suspendido = "SUSPENDIDO".equals(String.valueOf(fila[8]));
        return new ParticipacionPrograma(usuarioId, inscrito, diaPrograma, fechaInicio, zona, fase, celulaId,
                mentorId, rol, suspendido);
    }

    private static LocalDate aLocalDate(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof LocalDate localDate) {
            return localDate;
        }
        if (valor instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(valor));
    }

    private static UUID aUuid(Object valor) {
        if (valor instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(String.valueOf(valor));
    }

    /** Espejo de FaseProgramaJpa (este mismo paquete). */
    private static FasePrograma mapearFase(String faseCruda) {
        return switch (faseCruda) {
            case "FASE_1_RENACER" -> FasePrograma.PHASE_1_REBIRTH;
            case "FASE_2_DESARROLLO" -> FasePrograma.PHASE_2_DEVELOPMENT;
            case "FASE_3_GUERRERO_ALQUIMISTA" -> FasePrograma.PHASE_3_ALCHEMIST_WARRIOR;
            case "FASE_4_ASCENSION" -> FasePrograma.PHASE_4_ASCENSION;
            default -> throw new IllegalStateException("Fase de programa desconocida: " + faseCruda);
        };
    }

    /** Espejo de `users/infrastructure/adapter/out/persistence/user/RolUsuarioJpa.java`. */
    private static UserRole mapearRol(String rolCrudo) {
        return switch (rolCrudo) {
            case "ALQUIMISTA" -> UserRole.ALCHEMIST;
            case "ADMIN" -> UserRole.ADMIN;
            case "LIDER_MENTORES" -> UserRole.MENTOR_LEAD;
            case "MENTOR" -> UserRole.MENTOR;
            case "APRENDIZ" -> UserRole.TRAINEE;
            default -> throw new IllegalStateException("Rol de usuario desconocido: " + rolCrudo);
        };
    }
}
