package com.renaser.os.academy.domain.model.curso;

import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Espejo de `puedeVerSeccion` (RenaserBack `cursos/repository.ts:751-757`). */
class SeccionCursoTest {

    private static SeccionCurso seccion(Integer diaDesbloqueo) {
        return new SeccionCurso(SeccionCursoId.of("seccion-1"), CursoId.of("curso-1"), "Seccion 1", 0, diaDesbloqueo);
    }

    @Test
    @DisplayName("sin dia de desbloqueo -> siempre visible")
    void sinDiaDeDesbloqueoVisible() {
        assertThat(seccion(null).visibleEnCatalogoPara(UserRole.TRAINEE, 0)).isTrue();
    }

    @Test
    @DisplayName("personal (no TRAINEE) -> visible aunque no llegue al dia")
    void personalSiempreVisible() {
        assertThat(seccion(30).visibleEnCatalogoPara(UserRole.MENTOR, null)).isTrue();
    }

    @Test
    @DisplayName("TRAINEE que ya llego al dia -> visible")
    void traineeEnElDiaVisible() {
        assertThat(seccion(30).visibleEnCatalogoPara(UserRole.TRAINEE, 30)).isTrue();
    }

    @Test
    @DisplayName("TRAINEE que todavia no llega -> no visible")
    void traineeAntesDelDiaNoVisible() {
        assertThat(seccion(30).visibleEnCatalogoPara(UserRole.TRAINEE, 29)).isFalse();
    }

    @Test
    @DisplayName("TRAINEE sin programDay (null) -> tratado como dia 0")
    void traineeSinProgramDayTratadoComoDiaCero() {
        assertThat(seccion(1).visibleEnCatalogoPara(UserRole.TRAINEE, null)).isFalse();
        assertThat(seccion(0).visibleEnCatalogoPara(UserRole.TRAINEE, null)).isTrue();
    }
}
