package com.renaser.os.mentoring.domain.model.historial;

import com.renaser.os.mentoring.domain.model.historial.TramosDelGrupo.Tramo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Juntar los tramos del mismo grupo en una sola fila (spec 057, FR-020 y FR-021). */
class TramosDelGrupoTest {

    private static final UUID FENIX = UUID.randomUUID();
    private static final UUID AURORA = UUID.randomUUID();

    private static Tramo tramo(UUID grupo, String desde, String hasta, boolean enCurso) {
        LocalDate d = LocalDate.parse(desde);
        LocalDate h = LocalDate.parse(hasta);
        return new Tramo(grupo, "G", d.atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
                enCurso ? null : h.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC), d, h, enCurso);
    }

    @Test
    @DisplayName("spec 057 FR-020: los tramos del mismo grupo pegados, solapados o separados por un día se juntan: desde el más temprano, hasta el más tardío")
    void juntaLosQueSeTocan() { // spec 057 FR-020
        List<Tramo> juntos = TramosDelGrupo.juntar(List.of(
                tramo(FENIX, "2026-09-27", "2026-09-27", false),
                tramo(FENIX, "2026-09-06", "2026-09-27", false),
                tramo(FENIX, "2026-09-29", "2026-09-30", false)));

        assertThat(juntos).hasSize(1);
        assertThat(juntos.getFirst().primerDia()).isEqualTo(LocalDate.of(2026, 9, 6));
        assertThat(juntos.getFirst().ultimoDia()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(juntos.getFirst().desde()).isEqualTo(Instant.parse("2026-09-06T00:00:00Z"));
        assertThat(juntos.getFirst().hasta()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
    }

    @Test
    @DisplayName("spec 057 FR-020: con dos días o más de separación son dos pasos distintos por el grupo, y otro grupo nunca se junta")
    void noJuntaLosSeparadosNiLosDeOtroGrupo() { // spec 057 FR-020
        List<Tramo> juntos = TramosDelGrupo.juntar(List.of(
                tramo(FENIX, "2026-09-01", "2026-09-10", false),
                tramo(FENIX, "2026-09-13", "2026-09-20", false),
                tramo(AURORA, "2026-09-05", "2026-09-12", false)));

        assertThat(juntos).hasSize(3);
    }

    @Test
    @DisplayName("spec 057 FR-020: si uno de los tramos juntados sigue en curso, el resultado queda en curso (es un grupo activo, no anterior)")
    void unTramoEnCursoAbsorbeAlCerradoPegado() { // spec 057 FR-020
        List<Tramo> juntos = TramosDelGrupo.juntar(List.of(
                tramo(FENIX, "2026-09-01", "2026-09-27", false),
                tramo(FENIX, "2026-09-28", "2026-11-20", true)));

        assertThat(juntos).singleElement().satisfies(t -> {
            assertThat(t.enCurso()).isTrue();
            assertThat(t.hasta()).isNull();
        });
    }
}
