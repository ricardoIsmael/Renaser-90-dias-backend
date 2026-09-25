package com.renaser.os.rag.infrastructure.adapter.out.participante;

import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * De donde saca el agente el dia y la fase de quien le escribe.
 *
 * <p>Lo que se prueba aca no es el mapeo —eso es trivial— sino las dos decisiones que lo hacen
 * correcto: que la fase se DERIVA del dia en vez de leerse de la columna guardada, y que quien no
 * esta cursando no recibe un dia inventado.
 */
class ConsultarSituacionDelAprendizAdapterTest {

    private static final UserId ALGUIEN = UserId.of(UUID.randomUUID());

    /** 03:00 UTC del 26: en Lima todavia es el 25 (regla 02 — el caso que el fixture de las 10:00 escondia). */
    private static final Instant TRES_AM_UTC = Instant.parse("2026-09-26T03:00:00Z");

    private ConsultarSituacionDelAprendizAdapter adaptador(ParticipacionPrograma participacion) {
        return new ConsultarSituacionDelAprendizAdapter(new FinderFijo(participacion), FixedClock.at(TRES_AM_UTC));
    }

    /** Bateria 2026-09-25 (#41): sin el año, el modelo armaba las fechas con el de su entrenamiento. */
    @Test
    @DisplayName("la fecha de hoy es la de su zona: a las 03:00 UTC en Lima todavia es el dia anterior")
    void hoyEnSuZona() {
        Optional<SituacionDelAprendiz> situacion = adaptador(inscritoEnDia(18, FasePrograma.initial())).de(ALGUIEN);

        assertThat(situacion).hasValueSatisfying(s -> assertThat(s.hoy()).isEqualTo(LocalDate.of(2026, 9, 25)));
    }

    @ParameterizedTest(name = "dia {0} -> fase {1}")
    @DisplayName("La fase sale del dia, con los cortes 1-7, 8-34, 35-64 y 65-90")
    @CsvSource({"1,1", "7,1", "8,2", "34,2", "35,3", "64,3", "65,4", "90,4"})
    void laFaseSaleDelDia(int dia, int faseEsperada) {
        Optional<SituacionDelAprendiz> situacion = adaptador(inscritoEnDia(dia, FasePrograma.initial())).de(ALGUIEN);

        assertThat(situacion).hasValueSatisfying(s -> {
            assertThat(s.diaPrograma()).isEqualTo(dia);
            assertThat(s.fase()).isEqualTo(faseEsperada);
        });
    }

    /**
     * El caso que justifica derivarla. {@code users.api.FasePrograma} avisa en su javadoc de que
     * hay filas con el dia y la fase desincronizados (D-66) y pide no confiar nunca en el valor
     * guardado. Aca la columna dice fase 1 y el dia dice 38: gana el dia.
     */
    @Test
    @DisplayName("Si la columna guardada miente, manda el dia")
    void laColumnaGuardadaNoManda() {
        Optional<SituacionDelAprendiz> situacion =
                adaptador(inscritoEnDia(38, FasePrograma.PHASE_1_REBIRTH)).de(ALGUIEN);

        assertThat(situacion).hasValueSatisfying(s -> assertThat(s.fase()).isEqualTo(3));
    }

    /**
     * Un mentor, un administrador o alguien que todavia no activo el programa aparece con
     * {@code inscrito=false} y {@code diaPrograma=0} — es un caso legitimo y frecuente, no un
     * error. Devolver "dia 0 de 90" seria peor que no devolver nada: el prompt lo leeria como un
     * dato y el agente hablaria de un dia que no existe.
     */
    @Test
    @DisplayName("Quien no esta cursando no recibe un dia inventado")
    void sinInscripcionNoHayDia() {
        ParticipacionPrograma noInscrito = new ParticipacionPrograma(ALGUIEN, false, 0,
                LocalDate.of(2026, 9, 1), ZoneId.of("America/Lima"), FasePrograma.initial(),
                null, null, UserRole.MENTOR, false, false);

        assertThat(adaptador(noInscrito).de(ALGUIEN)).isEmpty();
    }

    @Test
    @DisplayName("Un usuario que no existe tampoco")
    void sinParticipacionNoHayDia() {
        assertThat(adaptador(null).de(ALGUIEN)).isEmpty();
    }

    private static ParticipacionPrograma inscritoEnDia(int dia, FasePrograma faseGuardada) {
        return new ParticipacionPrograma(ALGUIEN, true, dia, LocalDate.of(2026, 9, 1),
                ZoneId.of("America/Lima"), faseGuardada, null, null, UserRole.TRAINEE, false, true);
    }

    /** Doble minimo: solo se le pregunta la participacion de una persona. */
    private record FinderFijo(ParticipacionPrograma participacion) implements ParticipacionProgramaFinder {

        @Override
        public Optional<ParticipacionPrograma> deParticipante(UserId participanteId) {
            return Optional.ofNullable(participacion);
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
