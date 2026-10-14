package com.renaser.os.users.api;

import com.renaser.os.shared.domain.Permission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La matriz rol -> permiso de A-1 (CLAUDE.MD §5.3.2, docs/ENDPOINTS_FALTANTES.md fila A-1).
 * Domain puro: sin Spring, prueba unitaria sin contexto (CLAUDE.MD §0.2).
 */
class UserRoleTest {

    /**
     * Los 8 permisos que hoy tiene evidencia en el codigo (guards existentes) de que
     * corresponden a TRAINEE — ver el javadoc de {@code UserRole.PERMISOS_TRAINEE}.
     */
    private static final Set<Permission> PERMISOS_TRAINEE_ESPERADOS = EnumSet.of(
            Permission.USE_APP,
            Permission.FOLLOW_OWN_PROGRAM,
            Permission.PUBLISH_ON_WALL,
            Permission.OPEN_SUPPORT_TICKET,
            Permission.USE_MENTOR_TICKETS,
            Permission.OPEN_MENTOR_TICKET,
            Permission.VIEW_OWN_PHASE_CONTRACTS,
            Permission.SIGN_PHASE_CONTRACT);

    @ParameterizedTest
    @EnumSource(value = Permission.class, names = {
            "USE_APP", "FOLLOW_OWN_PROGRAM", "PUBLISH_ON_WALL", "OPEN_SUPPORT_TICKET",
            "USE_MENTOR_TICKETS", "OPEN_MENTOR_TICKET", "VIEW_OWN_PHASE_CONTRACTS", "SIGN_PHASE_CONTRACT"})
    @DisplayName("TRAINEE tiene exactamente los 8 permisos con evidencia en el codigo")
    void traineeTieneLosOchoPermisosConEvidencia(Permission permiso) {
        assertThat(UserRole.TRAINEE.can(permiso)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = Permission.class, mode = EnumSource.Mode.EXCLUDE, names = {
            "USE_APP", "FOLLOW_OWN_PROGRAM", "PUBLISH_ON_WALL", "OPEN_SUPPORT_TICKET",
            "USE_MENTOR_TICKETS", "OPEN_MENTOR_TICKET", "VIEW_OWN_PHASE_CONTRACTS", "SIGN_PHASE_CONTRACT"})
    @DisplayName("TRAINEE NO tiene ninguno de los demas permisos (MANAGE_*, MODERATE_WALL, los del SDD 002, etc)")
    void traineeNoTieneLosOtrosPermisos(Permission permiso) {
        assertThat(UserRole.TRAINEE.can(permiso)).isFalse();
    }

    @Test
    @DisplayName("la matriz de TRAINEE tiene exactamente 8 permisos, ni uno mas ni uno menos")
    void laMatrizDeTraineeTieneOchoPermisos() {
        long otorgados = EnumSet.allOf(Permission.class).stream()
                .filter(UserRole.TRAINEE::can)
                .count();
        assertThat(otorgados).isEqualTo(PERMISOS_TRAINEE_ESPERADOS.size());
    }

    /**
     * Los 10 permisos de MENTOR_LEAD (SDD 002, DL-08). Cada uno tiene su justificacion en el
     * javadoc de {@code UserRole.PERMISOS_MENTOR_LEAD}: o el rol ya lo ejercia, o la matriz de
     * roles de la especificacion del cliente §2.3 se lo concede.
     */
    private static final Set<Permission> PERMISOS_MENTOR_LEAD_ESPERADOS = EnumSet.of(
            Permission.USE_APP,
            Permission.TRACK_PROGRAM_AS_STAFF,
            Permission.FOLLOW_OWN_PROGRAM,
            Permission.PUBLISH_ON_WALL,
            Permission.OPEN_SUPPORT_TICKET,
            Permission.VIEW_ALL_MENTOR_TICKETS,
            Permission.VIEW_MENTOR_CORPS,
            Permission.VIEW_MENTOR_REPORT,
            Permission.FOLLOW_UP_MENTOR,
            Permission.SET_MENTOR_OPERATIONAL_STATUS);

    @ParameterizedTest
    @EnumSource(value = Permission.class, names = {
            "USE_APP", "TRACK_PROGRAM_AS_STAFF", "FOLLOW_OWN_PROGRAM", "PUBLISH_ON_WALL",
            "OPEN_SUPPORT_TICKET", "VIEW_ALL_MENTOR_TICKETS", "VIEW_MENTOR_CORPS",
            "VIEW_MENTOR_REPORT", "FOLLOW_UP_MENTOR", "SET_MENTOR_OPERATIONAL_STATUS"})
    @DisplayName("MENTOR_LEAD tiene exactamente los 10 permisos de su alcance")
    void mentorLeadTieneSusDiezPermisos(Permission permiso) {
        assertThat(UserRole.MENTOR_LEAD.can(permiso)).isTrue();
    }

    /**
     * La mitad que importa de verdad: gestionar el cuerpo de mentores <b>no</b> es administrar
     * la plataforma (SDD 002, CL-04). Si alguien agrega un {@code MANAGE_*} a la matriz para
     * destrabar un 403, este test se pone rojo.
     */
    @ParameterizedTest
    @EnumSource(value = Permission.class, names = {
            "MANAGE_STAFF", "MANAGE_ROLES", "MANAGE_CELLS", "MANAGE_COHORTS", "MANAGE_TRAINEES",
            "MANAGE_MENTOR_PROFILE", "MANAGE_CALENDAR", "MANAGE_HABIT_CATALOG", "MANAGE_EVIDENCE",
            "MANAGE_KNOWLEDGE_BASE", "MANAGE_SUPPORT_TICKETS", "MANAGE_WALL_CATEGORIES",
            "MODERATE_WALL", "PROMOTE_TESTIMONIAL", "APPROVE_ACCOUNT_REQUEST", "ASSIGN_MENTOR",
            "ADJUST_POINTS", "RENAME_GLOBAL_CHAT", "VIEW_ONBOARDING_DASHBOARD", "MANAGE_WELCOME",
            "MANAGE_PHASE_ANIMALS"})
    @DisplayName("MENTOR_LEAD NO administra la plataforma: ningun MANAGE_*, ni altas, ni puntos, ni moderacion")
    void mentorLeadNoEsAdministrador(Permission permiso) {
        assertThat(UserRole.MENTOR_LEAD.can(permiso)).isFalse();
    }

    @Test
    @DisplayName("MENTOR_LEAD no promueve de nivel a un mentor, pero si mueve su semaforo operativo (DL-09)")
    void mentorLeadMueveElSemaforoPeroNoElNivel() {
        assertThat(UserRole.MENTOR_LEAD.can(Permission.SET_MENTOR_OPERATIONAL_STATUS)).isTrue();
        assertThat(UserRole.MENTOR_LEAD.can(Permission.MANAGE_MENTOR_PROFILE)).isFalse();
    }

    @Test
    @DisplayName("la matriz de MENTOR_LEAD tiene exactamente 10 permisos, ni uno mas ni uno menos")
    void laMatrizDeMentorLeadTieneDiezPermisos() {
        long otorgados = EnumSet.allOf(Permission.class).stream()
                .filter(UserRole.MENTOR_LEAD::can)
                .count();
        assertThat(otorgados).isEqualTo(PERMISOS_MENTOR_LEAD_ESPERADOS.size());
    }

    /**
     * HUECO DE SEGURIDAD DELIBERADO Y TEMPORAL (A-1): para MENTOR, ADMIN y ALCHEMIST, can()
     * falla-abierto (devuelve true) para CUALQUIER permiso, porque el dueño del proyecto
     * todavia no definio su matriz. Este test documenta el hueco a proposito — el dia que se
     * defina una de esas matrices, este test tiene que ponerse rojo y avisar, no quedar en
     * verde silenciosamente.
     *
     * <blockquote><b>Corregido 2026-09-09.</b> Este test incluia tambien a MENTOR_LEAD. Salio
     * de la lista al definirse su matriz real (SDD 002, DL-08); su cobertura esta en los cuatro
     * tests de arriba.</blockquote>
     */
    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"MENTOR", "TRAINEE_GUIDE", "ADMIN", "ALCHEMIST"})
    @DisplayName("TEMPORAL: los roles sin matriz definida pasan cualquier permiso (fail-open, no verificado todavia); "
            + "el guía de aprendiz, igual que el mentor (D-265)")
    void rolesSinMatrizDefinidaFallanAbiertoParaCualquierPermiso(UserRole rol) {
        for (Permission permiso : Permission.values()) {
            if (permiso.exclusivoDeSoporteTi()) {
                continue; // D-267: lo exclusivo de Soporte TI no pasa por el falla-abierto (prueba aparte, abajo)
            }
            assertThat(rol.can(permiso))
                    .as("%s deberia pasar %s porque su matriz todavia no esta definida (hueco temporal A-1)",
                            rol, permiso)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("canManageRoles() no cambio: sigue siendo ADMIN/ALCHEMIST")
    void canManageRolesSigueIgual() {
        assertThat(UserRole.ADMIN.canManageRoles()).isTrue();
        assertThat(UserRole.ALCHEMIST.canManageRoles()).isTrue();
        assertThat(UserRole.TRAINEE.canManageRoles()).isFalse();
        assertThat(UserRole.MENTOR.canManageRoles()).isFalse();
        assertThat(UserRole.MENTOR_LEAD.canManageRoles()).isFalse();
        assertThat(UserRole.TRAINEE_GUIDE.canManageRoles()).isFalse();
    }

    @Test
    @DisplayName("D-265: solo MENTOR y TRAINEE_GUIDE actúan como mentor; el líder, el staff y el aprendiz no")
    void actuanComoMentorSoloElMentorYElGuia() {
        assertThat(EnumSet.allOf(UserRole.class).stream().filter(UserRole::actuaComoMentor))
                .containsExactlyInAnyOrder(UserRole.MENTOR, UserRole.TRAINEE_GUIDE);
        assertThat(UserRole.ROLES_QUE_ACTUAN_COMO_MENTOR).containsExactlyInAnyOrder(UserRole.MENTOR, UserRole.TRAINEE_GUIDE);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = "IT_SUPPORT", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("D-267: OPERATE_PLATFORM es solo de Soporte TI — ni ADMIN ni ALCHEMIST, aunque su matriz falle abierta")
    void operarLaPlataformaEsExclusivoDeSoporteTi(UserRole rol) {
        assertThat(rol.can(Permission.OPERATE_PLATFORM)).isFalse();
    }

    @Test
    @DisplayName("D-267: Soporte TI tiene todos los permisos, incluida la consola; no acompaña grupos ni maneja roles")
    void soporteTiTieneTodoMenosAcompanarYManejarRoles() {
        for (Permission permiso : Permission.values()) {
            assertThat(UserRole.IT_SUPPORT.can(permiso)).as("IT_SUPPORT debería pasar %s", permiso).isTrue();
        }
        assertThat(UserRole.IT_SUPPORT.actuaComoMentor()).isFalse();
        assertThat(UserRole.IT_SUPPORT.canManageRoles())
                .as("canManageRoles es también «está en los chats de soporte»: Soporte TI no lee chats privados")
                .isFalse();
        assertThat(UserRole.IT_SUPPORT.nombreVisible()).isEqualTo("Soporte TI");
        assertThat(Permission.OPERATE_PLATFORM.exclusivoDeSoporteTi()).isTrue();
        assertThat(java.util.Arrays.stream(Permission.values()).filter(Permission::exclusivoDeSoporteTi))
                .containsExactly(Permission.OPERATE_PLATFORM);
    }

    @Test
    @DisplayName("D-265: el guía de aprendiz se muestra como «Guía de aprendiz»")
    void nombreVisibleDelGuia() {
        assertThat(UserRole.TRAINEE_GUIDE.nombreVisible()).isEqualTo("Guía de aprendiz");
    }
}
