package com.renaser.os.users.domain.model.participante;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** El programa del aprendiz de prueba de «ver como»: activo y a mitad del camino (D-270). Sin Spring. */
class ProgramaDeLaCuentaDePruebaTest {

    /**
     * En la madrugada UTC (todavía el día anterior en Lima): la fecha de inicio tiene que salir del día del participante,
     * no del de UTC (E-91, E-541).
     */
    private static final FixedClock MADRUGADA_UTC = FixedClock.at(Instant.parse("2026-10-15T03:30:00Z"));
    private static final LocalDate HOY_EN_LIMA = LocalDate.parse("2026-10-14");

    @Test
    @DisplayName("vive hoy el día 45: activo, fase del día 45, sin ajuste, con el inicio 44 días antes en su zona")
    void viveElDia45() {
        UserId aprendiz = UserId.of(UUID.randomUUID());

        ParticipacionPrograma programa = ParticipacionPrograma.aMitadDelProgramaParaCuentaDePrueba(aprendiz, null,
                MADRUGADA_UTC);

        assertThat(programa.estaActivado()).isTrue();
        assertThat(programa.diaVigente(MADRUGADA_UTC)).isEqualTo(45);
        assertThat(programa.diaPrograma()).isEqualTo(45);
        assertThat(programa.fase()).isEqualTo(FasePrograma.paraDiaPrograma(45));
        assertThat(programa.diasAjuste()).isZero();
        assertThat(programa.fechaInicio()).isEqualTo(HOY_EN_LIMA.minusDays(44));
        assertThat(programa.programaCompletado()).isFalse();
        assertThat(programa.sigueAMitadDelPrograma(MADRUGADA_UTC)).isTrue();
    }

    @Test
    @DisplayName("conserva el grupo y el mentor que ya tenía la fila (el grupo de prueba los sincroniza aparte)")
    void conservaElGrupo() {
        UserId aprendiz = UserId.of(UUID.randomUUID());
        UserId mentor = UserId.of(UUID.randomUUID());
        UUID grupo = UUID.randomUUID();
        ParticipacionPrograma anterior = ParticipacionPrograma.aMitadDelProgramaParaCuentaDePrueba(aprendiz, null,
                MADRUGADA_UTC);
        anterior.asignarCelula(grupo, MADRUGADA_UTC);
        anterior.sincronizarMentor(mentor, MADRUGADA_UTC);

        ParticipacionPrograma otraVez = ParticipacionPrograma.aMitadDelProgramaParaCuentaDePrueba(aprendiz, anterior,
                MADRUGADA_UTC);

        assertThat(otraVez.celulaId()).isEqualTo(grupo);
        assertThat(otraVez.mentorId()).isEqualTo(mentor);
    }

    @Test
    @DisplayName("fuera del tramo 30–60 deja de estar «a mitad»: «ver como» la vuelve al día 45")
    void fueraDelTramo() {
        ParticipacionPrograma programa = ParticipacionPrograma.aMitadDelProgramaParaCuentaDePrueba(
                UserId.of(UUID.randomUUID()), null, MADRUGADA_UTC);

        assertThat(programa.sigueAMitadDelPrograma(FixedClock.at(MADRUGADA_UTC.now().plusSeconds(15L * 86_400))))
                .as("día 60").isTrue();
        assertThat(programa.sigueAMitadDelPrograma(FixedClock.at(MADRUGADA_UTC.now().plusSeconds(16L * 86_400))))
                .as("día 61").isFalse();
    }

    @Test
    @DisplayName("un programa sin activar no está «a mitad»")
    void sinActivar() {
        ParticipacionPrograma pendiente = ParticipacionPrograma.inscribirTraineeAprobado(UserId.of(UUID.randomUUID()),
                MADRUGADA_UTC);

        assertThat(pendiente.sigueAMitadDelPrograma(MADRUGADA_UTC)).isFalse();
    }
}
