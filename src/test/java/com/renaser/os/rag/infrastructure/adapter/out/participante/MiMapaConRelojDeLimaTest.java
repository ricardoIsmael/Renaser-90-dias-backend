package com.renaser.os.rag.infrastructure.adapter.out.participante;

import com.renaser.os.rag.application.services.herramientas.ConsultarMiMapaHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.mapa.MapaDeLaPersona;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-233, regla 02/03: la fecha del proximo hito de {@code consultar_mi_mapa} sale de la fecha de hoy EN SU
 * ZONA, con el adaptador real de la situacion. A las 03:30 UTC del 01/10 en Lima todavia es el 30/09: un
 * codigo que usara la fecha del servidor diria que el dia 30 cae un dia mas tarde.
 */
class MiMapaConRelojDeLimaTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    /** Empezo el jueves 03/09 en Lima: el 30/09 es su dia 28 y el 01/10 su dia 29 (fixture coherente, regla 03). */
    private static String contenido(Instant ahora, int diaEnLima) {
        ParticipacionPrograma participacion = new ParticipacionPrograma(APRENDIZ, true, diaEnLima,
                LocalDate.of(2026, 9, 3), LIMA, FasePrograma.paraDiaPrograma(diaEnLima), null, null, UserRole.TRAINEE,
                false, true);
        var situacion = new ConsultarSituacionDelAprendizAdapter(new FinderFijo(participacion), FixedClock.at(ahora));
        MapaDeLaPersona mapa = new MapaDeLaPersona(true, true, "salud", List.of(),
                List.of(new MapaDeLaPersona.Hito("salud", 30, "89 kg")), null, List.of(), List.of());
        var resultado = new ConsultarMiMapaHerramienta(id -> mapa, situacion)
                .ejecutar(APRENDIZ, InvocacionHerramienta.sinArgumentos(ConsultarMiMapaHerramienta.NOMBRE));
        return ((ResultadoHerramienta.Exito) resultado).contenido();
    }

    @Test
    @DisplayName("a las 03:30 UTC del 01/10 en Lima es 30/09 (dia 28): el dia 30 cae el viernes 02/10, no el sabado")
    void madrugadaUtc() {
        assertThat(contenido(Instant.parse("2026-10-01T03:30:00Z"), 28))
                .contains("- Dia 30 [PROXIMO: faltan 2 dias, el viernes 02/10/2026]: Salud: 89 kg");
    }

    @Test
    @DisplayName("control: a las 15:00 UTC del 01/10 ya es su dia 29 y el dia 30 sigue cayendo el viernes 02/10")
    void mediodiaUtc() {
        assertThat(contenido(Instant.parse("2026-10-01T15:00:00Z"), 29))
                .contains("- Dia 30 [PROXIMO: falta 1 dia, el viernes 02/10/2026]");
    }

    /** Solo {@code deParticipante} importa aca. */
    private record FinderFijo(ParticipacionPrograma participacion) implements ParticipacionProgramaFinder {
        @Override
        public Optional<ParticipacionPrograma> deParticipante(UserId participanteId) {
            return Optional.of(participacion);
        }

        @Override
        public List<UserId> miembrosActivosDeCelula(UUID celulaId) {
            return List.of();
        }

        @Override
        public List<UserId> miembrosDeCelula(UUID celulaId) {
            return List.of();
        }

        @Override
        public List<UserId> usuariosActivosConRol(Set<UserRole> roles) {
            return List.of();
        }

        @Override
        public List<UsuarioConDiaPrograma> usuariosActivosConDiaPrograma(Set<UserRole> roles) {
            return List.of();
        }

        @Override
        public List<UserId> participantesInscritosActivos() {
            return List.of();
        }

        @Override
        public int contarMiembrosDeCelula(UUID celulaId) {
            return 0;
        }
    }
}
