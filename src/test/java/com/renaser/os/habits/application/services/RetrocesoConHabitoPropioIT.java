package com.renaser.os.habits.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.in.habito.ConsultarMisHabitosUseCase;
import com.renaser.os.habits.application.ports.in.habito.ConsultarMisHabitosUseCase.HabitoConDias;
import com.renaser.os.habits.application.ports.in.habito.CrearHabitoPersonalUseCase;
import com.renaser.os.habits.application.ports.in.habito.CrearHabitoPersonalUseCase.CrearHabitoPersonalCommand;
import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.PlantillaHabitoPersonal;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-200 y D-216 de punta a punta, contra Postgres real: el habito PERSONAL que se crea el dia 30 nace
 * con {@code horarios_habito.dia_inicio = 30}. Si la retroceden al 25, se le sigue generando y "Mis
 * habitos" no le pone candado, lo haya hecho ya o no: crearlo prueba que llego a su primer dia.
 * Lo que un test con mocks no prueba: que los servicios reales, con el cableado de Spring, armen los
 * horarios sabiendo de quien es el habito.
 *
 * <p><b>Corregido 2026-09-27 (D-216).</b> Decia «si nunca lo hizo, sigue esperando su dia». TZ-15 del
 * e2e lo marco como falla P0 y el dueño ya habia decidido que lo propio que venia corriendo sigue.
 *
 * <p>El participante se siembra SIN activar: su dia es la columna {@code dia_programa}
 * ({@code users} la devuelve tal cual mientras el reloj no arranco), asi que el "retroceso" es un
 * UPDATE de esa columna y no depende del reloj del sistema (E-78).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RetrocesoConHabitoPropioIT {

    @Autowired
    private CrearHabitoPersonalUseCase crearUseCase;
    @Autowired
    private GenerarTracksDelDiaUseCase generarUseCase;
    @Autowired
    private ConsultarMisHabitosUseCase misHabitosUseCase;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserId participanteId;

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra participantes_programa, habitos, horarios_habito y registros_habito.
        if (participanteId != null) {
            jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", participanteId.value());
        }
    }

    private void seedParticipanteEnElDia(int diaPrograma) {
        participanteId = UserId.of(UUID.randomUUID());
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Fixture D-200', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                """, participanteId.value(), participanteId + "@renaser.test");
        jdbcTemplate.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa) VALUES (?, ?)",
                participanteId.value(), diaPrograma);
    }

    private void pasarAlDia(int diaPrograma) {
        jdbcTemplate.update("UPDATE renaser.participantes_programa SET dia_programa = ? WHERE usuario_id = ?",
                diaPrograma, participanteId.value());
    }

    private Habito crearHabitoPropio() {
        return crearUseCase.crear(new CrearHabitoPersonalCommand(participanteId, "Correr 5km", TipoHabito.CHECKBOX,
                "CUERPO", PlantillaHabitoPersonal.CORRER, "Terminar una carrera de 5km", null, LocalTime.of(6, 0),
                LocalTime.of(22, 0), null));
    }

    private HabitoConDias enMisHabitos(Habito habito) {
        return misHabitosUseCase.consultar(participanteId).stream()
                .filter(vista -> vista.habito().id().equals(habito.id()))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("D-200: el habito propio del dia 30 que ya corrio se sigue generando y sin candado tras bajar al 25")
    void unHabitoPropioQueYaCorrioSigueTrasRetroceder() {
        seedParticipanteEnElDia(30);
        Habito propio = crearHabitoPropio();
        assertThat(generarUseCase.generar(participanteId, LocalDate.of(2026, 9, 20)))
                .extracting(RegistroHabito::habitoId).contains(propio.id());
        pasarAlDia(31);
        generarUseCase.generar(participanteId, LocalDate.of(2026, 9, 21));

        pasarAlDia(25);
        List<RegistroHabito> generados = generarUseCase.generar(participanteId, LocalDate.of(2026, 9, 22));

        assertThat(generados).filteredOn(r -> r.habitoId().equals(propio.id())).singleElement()
                .satisfies(r -> assertThat(r.diaPrograma()).as("snapshot: el dia real").isEqualTo(25));
        HabitoConDias vista = enMisHabitos(propio);
        assertThat(vista.bloqueado()).isFalse();
        assertThat(vista.diaDesbloqueo()).isEqualTo(30);
    }

    /**
     * TZ-15 del e2e (D-216): la persona crea su habito el dia 30 y, antes de que se le genere ningun
     * registro, la retroceden al 25. Crearlo ya prueba que llego al dia 30 con el habito andando: se
     * le sigue generando y "Mis habitos" no le pone candado.
     *
     * <p><b>Corregido 2026-09-27 (D-216).</b> Esta prueba se llamaba «el habito propio del dia 30
     * que nunca corrio sigue esperando su dia tras bajar al 25» y afirmaba lo contrario: que no se
     * generaba y viajaba con candado, a 5 dias. Es exactamente lo que el e2e marco como falla P0.
     */
    @Test
    @DisplayName("D-216: el habito propio creado el dia 30, sin registros todavia, sigue activo tras bajar al 25")
    void unHabitoPropioSinRegistrosSigueActivoTrasRetroceder() {
        seedParticipanteEnElDia(30);
        Habito propio = crearHabitoPropio();

        pasarAlDia(25);
        List<RegistroHabito> generados = generarUseCase.generar(participanteId, LocalDate.of(2026, 9, 22));

        assertThat(generados).filteredOn(r -> r.habitoId().equals(propio.id())).singleElement()
                .satisfies(r -> assertThat(r.diaPrograma()).as("snapshot: el dia real").isEqualTo(25));
        HabitoConDias vista = enMisHabitos(propio);
        assertThat(vista.bloqueado()).isFalse();
        assertThat(vista.diasParaDesbloqueo()).isZero();
        assertThat(vista.diaDesbloqueo()).isEqualTo(30);
    }
}
