package com.renaser.os.academy.domain.model.curso;

import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF-03: el mentor entra a los cursos publicados sin esperar el día que sí espera el aprendiz.
 *
 * <p><b>No hizo falta cambiar nada.</b> {@link Curso#visibleEnCatalogoPara} ya condiciona el
 * bloqueo por día a {@code rol == TRAINEE}. Estas pruebas existen para fijar ese comportamiento:
 * hoy es correcto por diseño, y sin un test que lo diga, la próxima persona que toque esa
 * condición puede quitarle el {@code rol == TRAINEE} sin notar que le está cerrando la Academia
 * a todos los mentores.
 *
 * <p>Lo que sigue cerrado también se prueba: un borrador y un curso restringido no se abren por
 * ser mentor. Acompañar no es administrar.
 */
class CursoVisibilidadMentorTest {

    private static final Instant AHORA = Instant.parse("2026-09-10T12:00:00Z");

    private static Curso curso(boolean publicado, AccesoCurso acceso, Integer diaDesbloqueo,
                                Set<UserRole> roles) {
        return new Curso(CursoId.of("curso-1"), "curso-1", "Curso de prueba", null, null, 0, publicado, acceso,
                "skool", diaDesbloqueo, roles, AHORA, AHORA);
    }

    @Test
    @DisplayName("el mentor ve un curso publicado que el aprendiz recien desbloquea el dia 60")
    void mentorNoEsperaElDiaDeDesbloqueo() {
        Curso avanzado = curso(true, AccesoCurso.ABIERTO, 60, Set.of());

        // Sin programa personal: su dia es null y aun asi lo ve.
        assertThat(avanzado.visibleEnCatalogoPara(UserRole.MENTOR, null)).isTrue();
        // Con programa personal recien empezado: tampoco espera.
        assertThat(avanzado.visibleEnCatalogoPara(UserRole.MENTOR, 3)).isTrue();
        // El aprendiz en el dia 3 sigue esperando: la regla que existe no se toco.
        assertThat(avanzado.visibleEnCatalogoPara(UserRole.TRAINEE, 3)).isFalse();
        assertThat(avanzado.visibleEnCatalogoPara(UserRole.TRAINEE, 60)).isTrue();
    }

    @Test
    @DisplayName("un borrador NO se abre por ser mentor: acompanar no es administrar")
    void borradorSigueOculto() {
        Curso borrador = curso(false, AccesoCurso.ABIERTO, null, Set.of());

        assertThat(borrador.visibleEnCatalogoPara(UserRole.MENTOR, null)).isFalse();
        assertThat(borrador.visibleEnCatalogoPara(UserRole.MENTOR_LEAD, null)).isFalse();
    }

    @Test
    @DisplayName("un curso restringido tampoco")
    void restringidoSigueCerrado() {
        Curso restringido = curso(true, AccesoCurso.RESTRINGIDO, null, Set.of());

        assertThat(restringido.visibleEnCatalogoPara(UserRole.MENTOR, null)).isFalse();
    }

    @Test
    @DisplayName("si el curso limita roles, el mentor queda fuera cuando no esta en la lista")
    void rolesPermitidosSeRespetan() {
        Curso soloAprendices = curso(true, AccesoCurso.ABIERTO, null, Set.of(UserRole.TRAINEE));

        assertThat(soloAprendices.visibleEnCatalogoPara(UserRole.MENTOR, null)).isFalse();
        assertThat(soloAprendices.visibleEnCatalogoPara(UserRole.TRAINEE, 1)).isTrue();
    }

    @Test
    @DisplayName("el candado por dia no se le muestra al mentor: no tiene nada que esperar")
    void sinCandadoParaElMentor() {
        Curso avanzado = curso(true, AccesoCurso.ABIERTO, 60, Set.of());

        assertThat(avanzado.bloqueadoPorDiaPara(UserRole.MENTOR, null)).isFalse();
        assertThat(avanzado.bloqueadoPorDiaPara(UserRole.TRAINEE, 3)).isTrue();
    }
}
