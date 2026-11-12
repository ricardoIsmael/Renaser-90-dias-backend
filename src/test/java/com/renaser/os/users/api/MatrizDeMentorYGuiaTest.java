package com.renaser.os.users.api;

import com.renaser.os.shared.domain.Permission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La matriz estricta de MENTOR y TRAINEE_GUIDE (spec 050, tramo H): una lista de permitidos, negada por defecto, y la guarda que
 * impide que algún rol vuelva a fallar abierto sin que nadie lo decida.
 *
 * <p><b>Las dos listas de abajo se escriben a mano, no se derivan de {@code UserRole}.</b> Si salieran del código, la prueba
 * afirmaría lo que el código ya hace. {@link #PERMITIDOS} es la tabla que aprobó el dueño el 2026-11-12 (D18 de la spec);
 * {@link #DENEGADOS} es todo lo demás, nombrado uno por uno. Un permiso nuevo del vocabulario no está en ninguna de las dos y la
 * prueba de clasificación falla hasta que alguien lo ponga en una: no hay un valor por omisión que lo conceda en silencio.
 */
class MatrizDeMentorYGuiaTest {

    /** D18: iguales los dos roles (D-265). Los tres últimos siguen filtrados por el servicio a lo propio. */
    private static final Set<Permission> PERMITIDOS = EnumSet.of(
            Permission.USE_APP,
            Permission.FOLLOW_OWN_PROGRAM,
            Permission.PUBLISH_ON_WALL,
            Permission.TRACK_PROGRAM_AS_STAFF,
            Permission.OPEN_SUPPORT_TICKET,
            Permission.USE_MENTOR_TICKETS,
            Permission.ANSWER_MENTOR_TICKET,
            Permission.VIEW_OWN_PHASE_CONTRACTS,
            Permission.SIGN_PHASE_CONTRACT,
            Permission.MANAGE_CALENDAR,
            Permission.MANAGE_CELLS,
            Permission.MANAGE_COHORTS);

    /** D18: «negar el resto». Cada uno, con el motivo en la tabla aprobada. */
    private static final Set<Permission> DENEGADOS = EnumSet.of(
            Permission.MODERATE_WALL,
            Permission.MANAGE_WALL_CATEGORIES,
            Permission.PROMOTE_TESTIMONIAL,
            Permission.RENAME_GLOBAL_CHAT,
            Permission.MANAGE_WELCOME,
            Permission.MANAGE_PHASE_ANIMALS,
            Permission.MANAGE_RADAR_VISIBILITY,
            Permission.VIEW_ALL_SUPPORT_CHATS,
            Permission.MANAGE_RENASER_BOX,
            Permission.MANAGE_PAYMENTS,
            Permission.MANAGE_HABIT_CATALOG,
            Permission.MANAGE_EVIDENCE,
            Permission.VIEW_ONBOARDING_DASHBOARD,
            Permission.ADJUST_POINTS,
            Permission.EXPORT_RANKING,
            Permission.MANAGE_KNOWLEDGE_BASE,
            Permission.MANAGE_SUPPORT_TICKETS,
            Permission.OPEN_MENTOR_TICKET,
            Permission.VIEW_ALL_MENTOR_TICKETS,
            Permission.MANAGE_STAFF,
            Permission.MANAGE_TRAINEES,
            Permission.APPROVE_ACCOUNT_REQUEST,
            Permission.MANAGE_MENTOR_PROFILE,
            Permission.ASSIGN_MENTOR,
            Permission.MANAGE_ROLES,
            Permission.VIEW_MENTOR_CORPS,
            Permission.FOLLOW_UP_MENTOR,
            Permission.VIEW_MENTOR_REPORT,
            Permission.SET_MENTOR_OPERATIONAL_STATUS,
            Permission.OPERATE_PLATFORM);

    // spec 050 FR-040
    @Test
    @DisplayName("todo permiso del vocabulario está clasificado: o lo aprobó el dueño para Mentor y Guía, o se niega (no hay un valor por omisión)")
    void todoPermisoEstaClasificado() {
        Set<Permission> sinClasificar = EnumSet.allOf(Permission.class);
        sinClasificar.removeAll(PERMITIDOS);
        sinClasificar.removeAll(DENEGADOS);

        assertThat(sinClasificar)
                .as("""
                        Hay permisos nuevos sin clasificar para MENTOR y TRAINEE_GUIDE. La matriz de los dos es una lista de \
                        permitidos (spec 050 FR-040): un permiso nuevo se NIEGA hasta que el dueño apruebe darselo. Ponlo en \
                        DENEGADOS de esta prueba y, si el dueño lo aprueba, en UserRole.PERMISOS_MENTOR_Y_GUIA y en PERMITIDOS.""")
                .isEmpty();
        assertThat(PERMITIDOS).as("un permiso no puede estar permitido y denegado a la vez")
                .doesNotContainAnyElementsOf(DENEGADOS);
        assertThat(PERMITIDOS).as("D18: son exactamente 12").hasSize(12);
    }

    // spec 050 FR-040
    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"MENTOR", "TRAINEE_GUIDE"})
    @DisplayName("Mentor y Guía tienen exactamente los 12 permisos de D18, ni uno más")
    void tienenExactamenteLosDeLaTablaAprobada(UserRole rol) {
        Set<Permission> otorgados = Arrays.stream(Permission.values()).filter(rol::can)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Permission.class)));

        assertThat(otorgados).as("lo que %s puede hoy", rol).containsExactlyInAnyOrderElementsOf(PERMITIDOS);
    }

    // spec 050 FR-040
    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"MENTOR", "TRAINEE_GUIDE"})
    @DisplayName("Mentor y Guía no tienen ninguno de los permisos denegados (los de administración, moderación y jefatura)")
    void ningunoDeLosDenegados(UserRole rol) {
        for (Permission permiso : DENEGADOS) {
            assertThat(rol.can(permiso)).as("%s no debe poder %s", rol, permiso).isFalse();
        }
    }

    // spec 050 FR-040
    @Test
    @DisplayName("el Mentor y la Guía tienen el mismo conjunto (D-265: actuaComoMentor)")
    void losDosSonLoMismo() {
        for (Permission permiso : Permission.values()) {
            assertThat(UserRole.TRAINEE_GUIDE.can(permiso)).as("%s", permiso).isEqualTo(UserRole.MENTOR.can(permiso));
        }
    }

    /**
     * <b>La guarda.</b> Un rol «falla abierto» si {@code can()} le concede todo lo que no es exclusivo de Soporte TI. Hoy lo hacen
     * ADMIN y ALCHEMIST (A-1, sin matriz) e IT_SUPPORT (tiene todo, D-267). Si MENTOR, TRAINEE_GUIDE, MENTOR_LEAD o TRAINEE vuelven a
     * entrar en esa lista —por un {@code default -> true}, por reemplazar la lista por «todo menos…»—, o si un rol nuevo del enum
     * nace así sin que nadie lo decida, esta prueba se pone en rojo.
     */
    // spec 050 FR-040
    @Test
    @DisplayName("ningún rol con matriz explícita vuelve a fallar abierto; solo ADMIN, ALCHEMIST e IT_SUPPORT (los sin matriz o con todo)")
    void ningunRolConMatrizFallaAbierto() {
        Set<UserRole> fallanAbierto = new TreeSet<>();
        for (UserRole rol : UserRole.values()) {
            boolean concedeTodo = Arrays.stream(Permission.values())
                    .filter(permiso -> !permiso.exclusivoDeSoporteTi())
                    .allMatch(rol::can);
            if (concedeTodo) {
                fallanAbierto.add(rol);
            }
        }

        assertThat(fallanAbierto)
                .as("""
                        Estos roles conceden TODOS los permisos. Solo ADMIN y ALCHEMIST (deuda A-1: el dueño no definió su matriz) \
                        e IT_SUPPORT (tiene todo a propósito, D-267) pueden. Si es un rol con matriz explícita, alguien la volvió \
                        falla-abierta (spec 050 FR-040); si es un rol nuevo, su matriz tiene que ser una lista de permitidos \
                        aprobada por el dueño (regla 00: los permisos por rol no se inventan).""")
                .containsExactlyInAnyOrder(UserRole.ADMIN, UserRole.ALCHEMIST, UserRole.IT_SUPPORT);
    }

    // spec 050 FR-040
    @Test
    @DisplayName("un permiso exclusivo de Soporte TI sigue negado a Mentor y Guía, y un permiso de la jefatura de mentores también")
    void lasExcepcionesDeAntesSiguenNegadas() {
        for (UserRole rol : UserRole.ROLES_QUE_ACTUAN_COMO_MENTOR) {
            assertThat(rol.can(Permission.OPERATE_PLATFORM)).isFalse();
            assertThat(rol.can(Permission.EXPORT_RANKING)).as("D-278").isFalse();
            assertThat(rol.can(Permission.VIEW_ALL_MENTOR_TICKETS)).isFalse();
        }
    }
}
