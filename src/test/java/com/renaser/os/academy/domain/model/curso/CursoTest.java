package com.renaser.os.academy.domain.model.curso;

import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Espejo de `src/features/cursos/__tests__/puedeVerCurso.test.ts` (RenaserBack).
 * Cada caso cita el test original que reemplaza.
 */
class CursoTest {

    private static final Instant AHORA = Instant.parse("2026-08-24T12:00:00Z");

    private static Curso curso(boolean publicado, AccesoCurso acceso, Integer diaDesbloqueo, Set<UserRole> roles) {
        return new Curso(CursoId.of("curso-1"), "curso-1", "Curso de prueba", null, null, 0, publicado, acceso,
                "skool", diaDesbloqueo, roles, AHORA, AHORA);
    }

    @Nested
    @DisplayName("sin gates (compatibilidad con lo existente)")
    class SinGates {

        @Test
        @DisplayName("publicado + abierto, sin restriccion -> visible")
        void publicadoAbiertoVisible() {
            Curso curso = curso(true, AccesoCurso.ABIERTO, null, Set.of());
            assertThat(curso.visibleEnCatalogoPara(UserRole.TRAINEE, null)).isTrue();
        }

        @Test
        @DisplayName("restringido -> no visible (AC-02: hoy, en la practica, RESTRINGIDO nunca es visible por catalogo)")
        void restringidoNoVisible() {
            Curso curso = curso(true, AccesoCurso.RESTRINGIDO, null, Set.of());
            assertThat(curso.visibleEnCatalogoPara(UserRole.TRAINEE, null)).isFalse();
        }

        @Test
        @DisplayName("borrador (no publicado) -> no visible")
        void borradorNoVisible() {
            Curso curso = curso(false, AccesoCurso.ABIERTO, null, Set.of());
            assertThat(curso.visibleEnCatalogoPara(UserRole.TRAINEE, null)).isFalse();
        }
    }

    @Nested
    @DisplayName("gate de rol (limite duro)")
    class GateDeRol {

        @Test
        @DisplayName("rol permitido -> visible")
        void rolPermitidoVisible() {
            Curso curso = curso(true, AccesoCurso.ABIERTO, null, Set.of(UserRole.TRAINEE));
            assertThat(curso.visibleEnCatalogoPara(UserRole.TRAINEE, null)).isTrue();
        }

        @Test
        @DisplayName("rol no permitido -> no visible aunque publicado+abierto")
        void rolNoPermitidoNoVisible() {
            Curso curso = curso(true, AccesoCurso.ABIERTO, null, Set.of(UserRole.TRAINEE));
            assertThat(curso.visibleEnCatalogoPara(UserRole.MENTOR, null)).isFalse();
        }

        @Test
        @DisplayName("roles_permitidos vacio se trata como sin restriccion")
        void rolesVaciosSinRestriccion() {
            Curso curso = curso(true, AccesoCurso.ABIERTO, null, Set.of());
            assertThat(curso.visibleEnCatalogoPara(UserRole.MENTOR, null)).isTrue();
        }
    }

    @Nested
    @DisplayName("gate de dia (progresion, solo TRAINEE)")
    class GateDeDia {

        @Test
        @DisplayName("TRAINEE que aun no llega al dia -> no visible")
        void traineeAntesDelDiaNoVisible() {
            Curso curso = curso(true, AccesoCurso.ABIERTO, 30, Set.of());
            assertThat(curso.visibleEnCatalogoPara(UserRole.TRAINEE, 15)).isFalse();
        }

        @Test
        @DisplayName("TRAINEE que ya llego al dia -> visible")
        void traineeEnElDiaVisible() {
            Curso curso = curso(true, AccesoCurso.ABIERTO, 30, Set.of());
            assertThat(curso.visibleEnCatalogoPara(UserRole.TRAINEE, 30)).isTrue();
        }

        @Test
        @DisplayName("TRAINEE sin programDay (null) -> tratado como no alcanzado")
        void traineeSinProgramDayNoAlcanzado() {
            Curso curso = curso(true, AccesoCurso.ABIERTO, 30, Set.of());
            assertThat(curso.visibleEnCatalogoPara(UserRole.TRAINEE, null)).isFalse();
        }

        @Test
        @DisplayName("dia 0 (curso de bienvenida): visible desde el primer dia")
        void diaCeroVisibleDesdeElPrimerDia() {
            Curso curso = curso(true, AccesoCurso.ABIERTO, 0, Set.of());
            assertThat(curso.visibleEnCatalogoPara(UserRole.TRAINEE, 0)).isTrue();
        }

        @Test
        @DisplayName("dia 0 SIN fila de participante todavia -> igual visible")
        void diaCeroSinPerfilIgualVisible() {
            Curso curso = curso(true, AccesoCurso.ABIERTO, 0, Set.of());
            assertThat(curso.visibleEnCatalogoPara(UserRole.TRAINEE, null)).isTrue();
        }

        @Test
        @DisplayName("dia 1 sin perfil sigue bloqueado -- el hueco era solo el dia 0")
        void diaUnoSinPerfilBloqueado() {
            Curso curso = curso(true, AccesoCurso.ABIERTO, 1, Set.of());
            assertThat(curso.visibleEnCatalogoPara(UserRole.TRAINEE, null)).isFalse();
        }

        @Test
        @DisplayName("MENTOR no se bloquea por dia -- no tiene dia de programa")
        void mentorNoSeBloqueaPorDia() {
            Curso curso = curso(true, AccesoCurso.ABIERTO, 30, Set.of());
            assertThat(curso.visibleEnCatalogoPara(UserRole.MENTOR, null)).isTrue();
        }

        @Test
        @DisplayName("ADMIN no se bloquea por dia")
        void adminNoSeBloqueaPorDia() {
            Curso curso = curso(true, AccesoCurso.ABIERTO, 30, Set.of());
            assertThat(curso.visibleEnCatalogoPara(UserRole.ADMIN, null)).isTrue();
        }
    }

    @Nested
    @DisplayName("rol y dia combinados")
    class RolYDiaCombinados {

        @Test
        @DisplayName("curso solo-TRAINEE y con dia 30: TRAINEE en dia 10 sigue sin verlo")
        void soloTraineeDia30EnDia10NoVisible() {
            Curso curso = curso(true, AccesoCurso.ABIERTO, 30, Set.of(UserRole.TRAINEE));
            assertThat(curso.visibleEnCatalogoPara(UserRole.TRAINEE, 10)).isFalse();
        }

        @Test
        @DisplayName("curso solo-TRAINEE y con dia 30: TRAINEE en dia 30 si lo ve")
        void soloTraineeDia30EnDia30Visible() {
            Curso curso = curso(true, AccesoCurso.ABIERTO, 30, Set.of(UserRole.TRAINEE));
            assertThat(curso.visibleEnCatalogoPara(UserRole.TRAINEE, 30)).isTrue();
        }
    }

    /** Espejo de `catalogo_cursos_bloqueados` (0018) — inversa exacta de `visibleEnCatalogoPara`, solo por dia. */
    @Nested
    @DisplayName("bloqueadoPorDiaPara (AC-15, catalogo_cursos_bloqueados)")
    class BloqueadoPorDia {

        @Test
        @DisplayName("TRAINEE antes del dia, curso publicado+abierto+sin restriccion de rol -> bloqueado por dia")
        void traineeAntesDelDiaBloqueado() {
            Curso curso = curso(true, AccesoCurso.ABIERTO, 30, Set.of());
            assertThat(curso.bloqueadoPorDiaPara(UserRole.TRAINEE, 10)).isTrue();
        }

        @Test
        @DisplayName("TRAINEE que ya llego al dia -> no esta en la lista de bloqueados (ya lo ve)")
        void traineeEnElDiaNoBloqueado() {
            Curso curso = curso(true, AccesoCurso.ABIERTO, 30, Set.of());
            assertThat(curso.bloqueadoPorDiaPara(UserRole.TRAINEE, 30)).isFalse();
        }

        @Test
        @DisplayName("sin dia_desbloqueo -> nunca aparece como bloqueado por dia")
        void sinDiaDesbloqueoNuncaBloqueado() {
            Curso curso = curso(true, AccesoCurso.ABIERTO, null, Set.of());
            assertThat(curso.bloqueadoPorDiaPara(UserRole.TRAINEE, 0)).isFalse();
        }

        @Test
        @DisplayName("MENTOR no tiene dia de programa -> nunca bloqueado por dia")
        void mentorNuncaBloqueadoPorDia() {
            Curso curso = curso(true, AccesoCurso.ABIERTO, 30, Set.of());
            assertThat(curso.bloqueadoPorDiaPara(UserRole.MENTOR, null)).isFalse();
        }

        @Test
        @DisplayName("borrador (no publicado) -> nunca revelado, aunque el dia todavia no llegue")
        void borradorNuncaRevelado() {
            Curso curso = curso(false, AccesoCurso.ABIERTO, 30, Set.of());
            assertThat(curso.bloqueadoPorDiaPara(UserRole.TRAINEE, 10)).isFalse();
        }

        @Test
        @DisplayName("RESTRINGIDO -> nunca revelado (AC-02 aplica igual aca)")
        void restringidoNuncaRevelado() {
            Curso curso = curso(true, AccesoCurso.RESTRINGIDO, 30, Set.of());
            assertThat(curso.bloqueadoPorDiaPara(UserRole.TRAINEE, 10)).isFalse();
        }

        @Test
        @DisplayName("rol no permitido -> nunca revelado, aunque el dia todavia no llegue")
        void rolNoPermitidoNuncaRevelado() {
            Curso curso = curso(true, AccesoCurso.ABIERTO, 30, Set.of(UserRole.MENTOR));
            assertThat(curso.bloqueadoPorDiaPara(UserRole.TRAINEE, 10)).isFalse();
        }

        @Test
        @DisplayName("TRAINEE sin programDay (null) -> tratado como dia 0, bloqueado")
        void sinProgramDayTratadoComoDiaCero() {
            Curso curso = curso(true, AccesoCurso.ABIERTO, 30, Set.of());
            assertThat(curso.bloqueadoPorDiaPara(UserRole.TRAINEE, null)).isTrue();
        }
    }
}
