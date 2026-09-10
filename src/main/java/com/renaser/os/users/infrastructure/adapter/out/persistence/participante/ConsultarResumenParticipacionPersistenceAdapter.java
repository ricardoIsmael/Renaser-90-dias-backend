package com.renaser.os.users.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.shared.domain.Clock;
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
                   pp.celula_id,
                   pp.mentor_id,
                   u.rol,
                   u.estado,
                   pp.programa_activado_en,
                   COALESCE(pp.dias_ajuste_programa, 0) AS dias_ajuste_programa
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
            SELECT id FROM renaser.usuarios
            WHERE estado = 'ACTIVO' AND rol = ANY (CAST(?1 AS renaser.rol_usuario[]))
            """;

    private static final String QUERY_ACTIVOS_POR_ROL_CON_DIA = """
            SELECT u.id, pp.dia_programa, pp.fecha_inicio,
                   COALESCE(pp.timezone, 'America/Lima') AS timezone,
                   pp.programa_activado_en,
                   COALESCE(pp.dias_ajuste_programa, 0) AS dias_ajuste_programa
            FROM renaser.usuarios u
            LEFT JOIN renaser.participantes_programa pp ON pp.usuario_id = u.id
            WHERE u.estado = 'ACTIVO' AND u.rol = ANY (CAST(?1 AS renaser.rol_usuario[]))
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
    /**
     * El WHERE compartido por el listado y el conteo. Se escribe UNA vez a proposito: dos copias
     * se desincronizan y la pantalla termina diciendo "1 de 340" con una sola fila en la lista.
     *
     * <p>{@code ?1} llega null cuando no hay busqueda y el {@code IS NULL} deja pasar todo: un solo
     * SQL en vez de concatenar el WHERE segun los filtros, que es como se cuela una inyeccion o un
     * plan distinto por combinacion. {@code ?2} hace lo mismo con "solo sin grupo".
     *
     * <blockquote><b>Los filtros van PRIMERO y la paginacion despues, y no al reves.</b> Con la
     * numeracion invertida —filtros en {@code ?3}/{@code ?4}— el listado funcionaba y el conteo
     * reventaba con {@code ParameterLabelException: Ordinal parameter labels start from '?3'}: al
     * pegar este fragmento detras de un {@code SELECT COUNT(*)} sin LIMIT ni OFFSET, la consulta se
     * quedaba sin {@code ?1} ni {@code ?2}, y Hibernate exige que la numeracion arranque en 1 y sea
     * contigua. Salio en la pantalla de Personas, no en las pruebas: el doble del EntityManager no
     * valida etiquetas.</blockquote>
     */
    private static final String FILTRO_APRENDICES = """
            FROM renaser.usuarios u
            LEFT JOIN renaser.participantes_programa pp ON pp.usuario_id = u.id
            WHERE u.rol = 'APRENDIZ'
              AND (CAST(?1 AS text) IS NULL
                   OR u.nombre_completo ILIKE '%' || CAST(?1 AS text) || '%'
                   OR u.email ILIKE '%' || CAST(?1 AS text) || '%')
              AND (?2 = FALSE OR pp.celula_id IS NULL)
            """;

    private static final String QUERY_LISTAR_APRENDICES = """
            SELECT u.id, u.nombre_completo, u.email, u.estado,
                   COALESCE(pp.dia_programa, 0) AS dia_programa,
                   pp.celula_id, pp.mentor_id, pp.fecha_inicio,
                   COALESCE(pp.timezone, 'America/Lima') AS timezone,
                   pp.programa_activado_en,
                   COALESCE(pp.dias_ajuste_programa, 0) AS dias_ajuste_programa
            """ + FILTRO_APRENDICES + """
            ORDER BY u.nombre_completo, u.id
            LIMIT ?3 OFFSET ?4
            """;

    private static final String QUERY_CONTAR_APRENDICES = "SELECT COUNT(*) " + FILTRO_APRENDICES;

    private final EntityManager entityManager;
    private final Clock clock;

    ConsultarResumenParticipacionPersistenceAdapter(EntityManager entityManager, Clock clock) {
        this.entityManager = entityManager;
        this.clock = clock;
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
     * `= ANY (?)` con un array en vez de un `IN (...)` armado a mano: evita concatenar
     * placeholders.
     *
     * <p><b>El CAST no es opcional</b> (E-47, 2026-08-27). La version anterior pasaba el array
     * sin castear, confiando en que Postgres compararia el enum nativo {@code rol_usuario}
     * contra texto por su cuenta; no lo hace, y la consulta reventaba en runtime con
     * <em>"operator does not exist: renaser.rol_usuario = character varying"</em>. Es distinto
     * del {@code estado = 'ACTIVO'} de al lado, que si funciona: un literal en el SQL llega
     * SIN tipo y Postgres lo coacciona al enum, mientras que un parametro ligado llega tipado
     * como {@code varchar} y ya no hay coercion posible.
     *
     * <p>Se castea el ARRAY al enum y no la columna a texto ({@code rol::text = ANY (?)}):
     * castear la columna descartaria cualquier indice sobre {@code rol}. Es seguro porque
     * {@link #aClave} mapea de forma exhaustiva a las cinco etiquetas reales del enum.
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
                        fila[1] == null ? null
                                : diaVigente((Number) fila[1], aLocalDate(fila[2]), aZona(fila[3]), fila[4],
                                        (Number) fila[5])))
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
    public List<ResumenTraineeAdmin> listarAprendices(int offset, int limit, String busqueda,
                                                       boolean soloSinGrupo) {
        List<Object[]> filas = entityManager.createNativeQuery(QUERY_LISTAR_APRENDICES)
                .setParameter(1, normalizar(busqueda))
                .setParameter(2, soloSinGrupo)
                .setParameter(3, limit)
                .setParameter(4, offset)
                .getResultList();
        return filas.stream().map(this::aResumenTraineeAdmin).collect(Collectors.toList());
    }

    @Override
    public long contarAprendices(String busqueda, boolean soloSinGrupo) {
        Number total = (Number) entityManager.createNativeQuery(QUERY_CONTAR_APRENDICES)
                .setParameter(1, normalizar(busqueda))
                .setParameter(2, soloSinGrupo)
                .getSingleResult();
        return total.longValue();
    }

    /** Vacio y solo-espacios se tratan como "sin busqueda": si no, el ILIKE '%%' pasa igual pero
     * el total dejaria de coincidir con lo que ve quien borro el texto del buscador. */
    private static String normalizar(String busqueda) {
        return busqueda == null || busqueda.isBlank() ? null : busqueda.trim();
    }

    private ResumenTraineeAdmin aResumenTraineeAdmin(Object[] fila) {
        UserId id = UserId.of(aUuid(fila[0]));
        String fullName = String.valueOf(fila[1]);
        String email = String.valueOf(fila[2]);
        boolean suspendido = "SUSPENDIDO".equals(String.valueOf(fila[3]));
        UUID celulaId = fila[5] == null ? null : aUuid(fila[5]);
        UserId mentorId = fila[6] == null ? null : UserId.of(aUuid(fila[6]));
        int diaPrograma = diaVigente((Number) fila[4], aLocalDate(fila[7]), aZona(fila[8]), fila[9],
                (Number) fila[10]);
        return new ResumenTraineeAdmin(id, fullName, email,
                suspendido ? UserStatus.SUSPENDED : UserStatus.ACTIVE,
                diaPrograma, FasePrograma.paraDiaPrograma(diaPrograma), celulaId, mentorId);
    }

    /**
     * String.valueOf(...) para `rol`/`estado`/`fase` (tipos ENUM nativos de Postgres,
     * pueden llegar como String o PGobject segun la ruta de Hibernate/pgjdbc — mismo
     * criterio ya verificado por `rocks`/`phasecontracts` contra Postgres real).
     */
    private ParticipacionPrograma aResumen(UserId usuarioId, Object[] fila) {
        boolean inscrito = Boolean.TRUE.equals(fila[0]);
        LocalDate fechaInicio = inscrito ? aLocalDate(fila[2]) : null;
        ZoneId zona = aZona(fila[3]);
        UUID celulaId = fila[4] == null ? null : aUuid(fila[4]);
        UserId mentorId = fila[5] == null ? null : UserId.of(aUuid(fila[5]));
        UserRole rol = mapearRol(String.valueOf(fila[6]));
        boolean suspendido = "SUSPENDIDO".equals(String.valueOf(fila[7]));
        int diaPrograma = diaVigente((Number) fila[1], fechaInicio, zona, fila[8], (Number) fila[9]);
        // fila[8] es programa_activado_en: NULL = aprobado pero sin Terminos firmados.
        boolean activado = inscrito && fila[8] != null;
        return new ParticipacionPrograma(usuarioId, inscrito, diaPrograma, fechaInicio, zona,
                FasePrograma.paraDiaPrograma(diaPrograma), celulaId, mentorId, rol, suspendido, activado);
    }

    /**
     * <b>El dia de programa que se DEVUELVE, derivado de las fechas</b> (A-1, 2026-09-08).
     *
     * <p>Antes esta proyeccion devolvia {@code participantes_programa.dia_programa} crudo, y esa
     * columna la escribe UNICAMENTE {@code AvanzarDiaProgramaScheduler}. V20 hizo el dia derivado
     * "en el dominio", pero el camino de lectura —{@code GET /api/v1/home}, el panel admin y los
     * 7 modulos que consumen {@code ParticipacionProgramaFinder}— seguia leyendo lo ultimo que
     * alguien hubiera guardado. Efecto real (E-91 otra vez, por otra puerta): si el backend no
     * estuvo arriba en el minuto :05 de la hora que cruza la medianoche del participante, la app
     * muestra <b>dia 0</b> todo el dia aunque el dominio sepa que va por el 2. Derivar aca lo
     * vuelve independiente de que el barrido haya corrido.
     *
     * <p><b>Cuando NO se deriva</b>: mientras el reloj no arranco (sin {@code programa_activado_en},
     * o con {@code fecha_inicio} en el futuro) manda la columna, no un 0. Es la misma distincion
     * que hace {@code ParticipacionPrograma.sincronizarDiaDelPrograma}: un participante
     * pre-activacion conserva el dia que un ADMIN le haya fijado a mano.
     *
     * <p>La cuenta NO se copia aca: la hace {@code ParticipacionPrograma.diaProgramaDerivado},
     * el mismo metodo que usa el barrido.
     */
    private int diaVigente(Number diaAlmacenado, LocalDate fechaInicio, ZoneId zona, Object programaActivadoEn,
                           Number diasAjuste) {
        int almacenado = diaAlmacenado == null ? 0 : diaAlmacenado.intValue();
        if (programaActivadoEn == null || fechaInicio == null) {
            return almacenado;
        }
        LocalDate hoyEnSuZona = clock.now().atZone(zona).toLocalDate();
        if (fechaInicio.isAfter(hoyEnSuZona)) {
            return almacenado;
        }
        return com.renaser.os.users.domain.model.participante.ParticipacionPrograma.diaProgramaDerivado(
                fechaInicio, hoyEnSuZona, diasAjuste == null ? 0 : diasAjuste.intValue(), true);
    }

    /** `timezone` es NULL para quien no tiene fila de programa: mismo default que la columna. */
    private static ZoneId aZona(Object valor) {
        return valor == null ? ZONA_POR_DEFECTO : ZoneId.of(String.valueOf(valor));
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
