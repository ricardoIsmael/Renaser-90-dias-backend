package com.renaser.os.mentoring.application.services;

import com.renaser.os.mentoring.application.ports.in.ConsultarRankingDeGruposUseCase.FilaDeGrupo;
import com.renaser.os.mentoring.application.ports.in.ConsultarRankingDeGruposUseCase.RankingDeGrupos;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ranking entre grupos. Usa el mismo motor que la evaluación del mentor, sin filtrar por quién
 * acompañaba: mide al grupo durante todo el mes.
 */
class RankingDeGruposServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-30T15:00:00Z");
    private static final YearMonth SETIEMBRE = YearMonth.of(2026, 9);
    private static final Instant DESDE = Instant.parse("2026-08-01T05:00:00Z");

    private static final UUID COHORTE = UUID.randomUUID();
    private static final UUID GRUPO_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID GRUPO_B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID GRUPO_C = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UserId ACTOR = UserId.of(UUID.randomUUID());

    private BancoDeMentoria banco;

    @BeforeEach
    void preparar() {
        banco = new BancoDeMentoria();
    }

    private RankingDeGruposService servicio() {
        return new RankingDeGruposService(banco.acompanamiento, banco.obligacionesFinder, banco.entregasFinder,
                banco.calculo, FixedClock.at(AHORA));
    }

    private UserId alumnoDe(UUID grupo, String nombre) {
        UserId alumno = UserId.of(UUID.randomUUID());
        banco.alumno(grupo, alumno, nombre, DESDE, null);
        return alumno;
    }

    /** Le crea {@code entregadas} de {@code total} obligaciones con evidencia. */
    private void cumplimiento(UserId alumno, int entregadas, int total) {
        for (int i = 0; i < total; i++) {
            var o = banco.obligacion(alumno, LocalDate.of(2026, 9, i + 2), i < entregadas, true);
            if (i < entregadas) {
                banco.entregada(o, LocalDate.of(2026, 9, i + 2));
            }
        }
    }

    private static FilaDeGrupo fila(RankingDeGrupos ranking, UUID grupoId) {
        return ranking.grupos().stream().filter(f -> f.grupoId().equals(grupoId)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("ordena por cumplimiento, de mayor a menor")
    void ordenPorCumplimiento() {
        banco.grupo(GRUPO_A, "Grupo A", UserId.of(UUID.randomUUID()), COHORTE, 3);
        banco.grupo(GRUPO_B, "Grupo B", UserId.of(UUID.randomUUID()), COHORTE, 3);
        cumplimiento(alumnoDe(GRUPO_A, "a1"), 1, 4);
        cumplimiento(alumnoDe(GRUPO_B, "b1"), 4, 4);

        RankingDeGrupos ranking = servicio().ranking(ACTOR, COHORTE, SETIEMBRE);

        assertThat(ranking.grupos()).extracting(FilaDeGrupo::grupoId).containsExactly(GRUPO_B, GRUPO_A);
        assertThat(fila(ranking, GRUPO_B).posicion()).isEqualTo(1);
        assertThat(fila(ranking, GRUPO_A).posicion()).isEqualTo(2);
    }

    @Test
    @DisplayName("los empates comparten posicion y la siguiente salta: 1, 2, 2, 4 (P-08)")
    void empatesComparten() {
        banco.grupo(GRUPO_A, "Grupo A", UserId.of(UUID.randomUUID()), COHORTE, 3);
        banco.grupo(GRUPO_B, "Grupo B", UserId.of(UUID.randomUUID()), COHORTE, 3);
        banco.grupo(GRUPO_C, "Grupo C", UserId.of(UUID.randomUUID()), COHORTE, 3);
        cumplimiento(alumnoDe(GRUPO_A, "a1"), 2, 4);
        cumplimiento(alumnoDe(GRUPO_B, "b1"), 2, 4);
        cumplimiento(alumnoDe(GRUPO_C, "c1"), 4, 4);

        RankingDeGrupos ranking = servicio().ranking(ACTOR, COHORTE, SETIEMBRE);

        assertThat(fila(ranking, GRUPO_C).posicion()).isEqualTo(1);
        assertThat(fila(ranking, GRUPO_A).posicion()).isEqualTo(2);
        assertThat(fila(ranking, GRUPO_B).posicion()).isEqualTo(2);
    }

    @Test
    @DisplayName("un grupo sin muestra aparece al final, SIN calificacion — no es el peor")
    void sinMuestraAlFinalSinNota() {
        banco.grupo(GRUPO_A, "Grupo A", UserId.of(UUID.randomUUID()), COHORTE, 3);
        banco.grupo(GRUPO_B, "Grupo Nuevo", UserId.of(UUID.randomUUID()), COHORTE, 3);
        cumplimiento(alumnoDe(GRUPO_A, "a1"), 1, 4);
        alumnoDe(GRUPO_B, "b1"); // sin obligaciones

        RankingDeGrupos ranking = servicio().ranking(ACTOR, COHORTE, SETIEMBRE);

        assertThat(ranking.grupos().getLast().grupoId()).isEqualTo(GRUPO_B);
        assertThat(fila(ranking, GRUPO_B).porcentaje()).isNull();
        assertThat(fila(ranking, GRUPO_B).estado()).isEqualTo("SIN_MUESTRA");
    }

    @Test
    @DisplayName("la muestra viaja: dos porcentajes iguales con distinta muestra no son comparables a ciegas")
    void muestraVisible() {
        banco.grupo(GRUPO_A, "Grupo A", UserId.of(UUID.randomUUID()), COHORTE, 3);
        cumplimiento(alumnoDe(GRUPO_A, "a1"), 2, 4);
        cumplimiento(alumnoDe(GRUPO_A, "a2"), 4, 4);

        RankingDeGrupos ranking = servicio().ranking(ACTOR, COHORTE, SETIEMBRE);

        assertThat(fila(ranking, GRUPO_A).muestra()).isEqualTo(2);
        // Promedio de porcentajes: (50 + 100) / 2 = 75, no 6/8 = 75... aca coinciden, pero el
        // campo que importa es que la muestra se vea.
        assertThat(fila(ranking, GRUPO_A).porcentaje()).isEqualByComparingTo("75");
    }

    @Test
    @DisplayName("solo se comparan grupos de la MISMA cohorte")
    void soloLaMismaCohorte() {
        banco.grupo(GRUPO_A, "Grupo A", UserId.of(UUID.randomUUID()), COHORTE, 3);
        banco.grupo(GRUPO_B, "De otra cohorte", UserId.of(UUID.randomUUID()), UUID.randomUUID(), 3);
        cumplimiento(alumnoDe(GRUPO_A, "a1"), 1, 2);
        cumplimiento(alumnoDe(GRUPO_B, "b1"), 2, 2);

        RankingDeGrupos ranking = servicio().ranking(ACTOR, COHORTE, SETIEMBRE);

        assertThat(ranking.grupos()).hasSize(1);
        assertThat(ranking.grupos().getFirst().grupoId()).isEqualTo(GRUPO_A);
    }

    @Test
    @DisplayName("una cohorte sin grupos devuelve lista vacia, no un error")
    void cohorteSinGrupos() {
        RankingDeGrupos ranking = servicio().ranking(ACTOR, COHORTE, SETIEMBRE);

        assertThat(ranking.grupos()).isEmpty();
    }

    @Test
    @DisplayName("el ranking no expone ningun dato de los miembros")
    void sinDatosDeMiembros() {
        banco.grupo(GRUPO_A, "Grupo A", UserId.of(UUID.randomUUID()), COHORTE, 3);
        UserId alumno = alumnoDe(GRUPO_A, "Ana Perez");
        cumplimiento(alumno, 1, 2);

        RankingDeGrupos ranking = servicio().ranking(ACTOR, COHORTE, SETIEMBRE);

        assertThat(ranking.toString()).doesNotContain("Ana Perez", alumno.value().toString());
    }
}
