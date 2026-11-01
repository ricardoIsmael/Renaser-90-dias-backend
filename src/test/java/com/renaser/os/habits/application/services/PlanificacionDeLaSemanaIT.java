package com.renaser.os.habits.application.services;

import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.in.preferencia.PlanDeLaSemanaUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.RepetirSemanaUseCase;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.CambioDeFecha;
import com.renaser.os.habits.domain.model.preferencia.CombinacionDeAlarma;
import com.renaser.os.habits.domain.model.preferencia.RecordatorioDelDia;
import com.renaser.os.habits.domain.model.preferencia.SemanaCalendaria;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 028 (D-296) de punta a punta contra Postgres real: «Solo esta semana», repetir la semana y deshacerla.
 *
 * <p>Fixture coherente (regla 03): el programa arranco el 28/9, asi que el miercoles 28/10 es el Dia 31. El reloj esta a las 03:00 UTC del
 * 29/10, que en Lima todavia es la noche del 28 (el dia local, no el de UTC). La semana en curso es la del 26/10 y la siguiente la del 2/11.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PlanificacionDeLaSemanaIT {

    private static final Instant AHORA = Instant.parse("2026-10-29T03:00:00Z");
    private static final SemanaCalendaria MODELO = SemanaCalendaria.desde(LocalDate.of(2026, 10, 26));
    private static final SemanaCalendaria DESTINO = MODELO.siguiente();
    private static final LocalDate SABADO = LocalDate.of(2026, 10, 31);
    private static final LocalDate SABADO_SIGUIENTE = LocalDate.of(2026, 11, 7);
    private static final LocalDate MIERCOLES_SIGUIENTE = LocalDate.of(2026, 11, 4);

    @Autowired JdbcTemplate jdbc;
    @Autowired RelojDePrueba reloj;
    @Autowired PlanDeLaSemanaUseCase planDeLaSemana;
    @Autowired RepetirSemanaUseCase repetir;
    @Autowired LoadPreferenciaHorarioPort load;

    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> habitosCreados = new ArrayList<>();
    private UserId persona;
    private UserId otra;
    private HabitoId despertar;
    private HabitoId gimnasio;

    @BeforeEach
    void preparar() {
        reloj.fijar(AHORA);
        persona = usuario();
        otra = usuario();
        despertar = habito("Despertar");
        gimnasio = habito("Gimnasio");
    }

    @AfterEach
    void limpiar() {
        usuarios.forEach(id -> {
            jdbc.update("DELETE FROM renaser.horarios_habito_por_fecha WHERE participante_id = ?", id);
            try {
                jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id);
            } catch (DataAccessException quedaReferenciado) {
                // La base de la suite es descartable.
            }
        });
        habitosCreados.forEach(id -> jdbc.update("DELETE FROM renaser.habitos WHERE id = ?", id));
    }

    private CambioDeFecha mover(LocalDate fecha, int hora) {
        return new CambioDeFecha(fecha, false, null, LocalTime.of(hora, 0), null, null, null);
    }

    private LocalTime horaResuelta(UserId quien, HabitoId habito, LocalDate fecha) {
        return load.porParticipanteHabitosYFecha(quien, List.of(habito), fecha).stream().findFirst().map(p -> p.horaDisparo()).orElse(null);
    }

    @Test
    @DisplayName("un cambio «solo esta semana» rige esa fecha y ninguna otra, y el plan de siempre no cambia")
    void soloEstaSemana() {
        planDeLaSemana.guardar(persona, despertar, MODELO, List.of(mover(SABADO, 9)));

        assertThat(horaResuelta(persona, despertar, SABADO)).isEqualTo(LocalTime.of(9, 0));
        assertThat(horaResuelta(persona, despertar, SABADO_SIGUIENTE)).as("la semana siguiente sigue a la de siempre").isNull();
        assertThat(load.porParticipanteYHabito(persona, despertar)).as("el plan de siempre no se toca").isEmpty();
        assertThat(horaResuelta(otra, despertar, SABADO)).as("la otra persona no ve nada").isNull();
    }

    @Test
    @DisplayName("repetir deja la semana siguiente indistinguible de haberla planificado a mano, y es idempotente")
    void repetirLaSemana() {
        planDeLaSemana.guardar(persona, despertar, MODELO, List.of(mover(SABADO, 9)));
        planDeLaSemana.guardar(persona, gimnasio, MODELO, List.of(new CambioDeFecha(LocalDate.of(2026, 10, 30), false, null, null, null,
                RecordatorioDelDia.con(List.of(30, 0)), CombinacionDeAlarma.de(false, true, false, true))));

        var oferta = repetir.ofrecer(persona).orElseThrow();
        assertThat(oferta.modelo()).isEqualTo(MODELO);
        assertThat(oferta.destino()).isEqualTo(DESTINO);
        assertThat(oferta.habitos()).hasSize(2);
        assertThat(oferta.reemplazaLoPropio()).isFalse();

        var resultado = repetir.repetir(persona, MODELO, DESTINO, false);

        assertThat(resultado.diasCopiados()).isEqualTo(2);
        // «A mano»: la misma semana planificada con el mismo editor da exactamente lo mismo.
        var aMano = planDeLaSemana.consultar(persona, despertar, DESTINO);
        assertThat(aMano.get(5).fecha()).isEqualTo(SABADO_SIGUIENTE);
        assertThat(aMano.get(5).propioDeLaFecha()).isTrue();
        assertThat(aMano.get(5).dia().horaDisparo()).isEqualTo(LocalTime.of(9, 0));
        assertThat(horaResuelta(persona, despertar, SABADO_SIGUIENTE)).isEqualTo(LocalTime.of(9, 0));
        var gym = planDeLaSemana.consultar(persona, gimnasio, DESTINO).get(4);
        assertThat(gym.dia().recordatorio().antelaciones()).containsExactly(30, 0);
        assertThat(gym.dia().alarma()).isEqualTo(CombinacionDeAlarma.de(false, true, false, true));
        assertThat(horaResuelta(otra, despertar, SABADO_SIGUIENTE)).isNull();

        assertThat(repetir.ofrecer(persona)).as("ya quedo igual: no hay nada que ofrecer").isEmpty();
        assertThat(repetir.repetir(persona, MODELO, DESTINO, false).diasCopiados()).as("idempotente").isZero();
        assertThat(load.fechasPropiasEntre(persona, DESTINO.lunes(), DESTINO.domingo())).hasSize(2);
    }

    @Test
    @DisplayName("la semana siguiente con otro cambio propio exige confirmar, y confirmado la deja como la modelo")
    void reemplazarPideConfirmacion() {
        planDeLaSemana.guardar(persona, despertar, MODELO, List.of(mover(SABADO, 9)));
        planDeLaSemana.guardar(persona, despertar, DESTINO, List.of(mover(SABADO_SIGUIENTE, 11), mover(MIERCOLES_SIGUIENTE, 5)));

        assertThat(repetir.ofrecer(persona).orElseThrow().reemplazaLoPropio()).isTrue();
        assertThatThrownBy(() -> repetir.repetir(persona, MODELO, DESTINO, false)).isInstanceOf(IllegalStateException.class);
        assertThat(horaResuelta(persona, despertar, SABADO_SIGUIENTE)).as("nada se escribio").isEqualTo(LocalTime.of(11, 0));

        var resultado = repetir.repetir(persona, MODELO, DESTINO, true);

        assertThat(resultado.diasCopiados()).isEqualTo(1);
        assertThat(resultado.diasQueVuelvenAlPlan()).isEqualTo(1);
        assertThat(horaResuelta(persona, despertar, SABADO_SIGUIENTE)).isEqualTo(LocalTime.of(9, 0));
        assertThat(horaResuelta(persona, despertar, MIERCOLES_SIGUIENTE)).as("lo que la modelo no tenia vuelve al plan de siempre").isNull();
    }

    @Test
    @DisplayName("deshacer deja la semana en el plan de siempre sin tocar la modelo ni a la otra persona")
    void deshacer() {
        planDeLaSemana.guardar(persona, despertar, MODELO, List.of(mover(SABADO, 9)));
        planDeLaSemana.guardar(otra, despertar, DESTINO, List.of(mover(SABADO_SIGUIENTE, 5)));
        repetir.repetir(persona, MODELO, DESTINO, false);

        int deshechas = repetir.deshacerSemana(persona, DESTINO);

        assertThat(deshechas).isEqualTo(1);
        assertThat(horaResuelta(persona, despertar, SABADO_SIGUIENTE)).isNull();
        assertThat(horaResuelta(persona, despertar, SABADO)).as("la modelo sigue igual").isEqualTo(LocalTime.of(9, 0));
        assertThat(horaResuelta(otra, despertar, SABADO_SIGUIENTE)).as("la otra persona no se toca").isEqualTo(LocalTime.of(5, 0));
        assertThat(repetir.deshacerSemana(persona, DESTINO)).as("idempotente").isZero();
        assertThat(repetir.ofrecer(persona)).as("la oferta vuelve").isPresent();
    }

    @Test
    @DisplayName("hoy y los dias pasados no se cambian nunca, ni al guardar ni al repetir ni al deshacer")
    void hoyYLoPasadoNoSeTocan() {
        LocalDate hoy = LocalDate.of(2026, 10, 28);
        assertThatThrownBy(() -> planDeLaSemana.guardar(persona, despertar, MODELO, List.of(mover(hoy, 9))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> planDeLaSemana.guardar(persona, despertar, MODELO, List.of(mover(LocalDate.of(2026, 10, 27), 9))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(load.fechasPropiasEntre(persona, MODELO.lunes(), MODELO.domingo())).isEmpty();
    }

    @Test
    @DisplayName("un habito desactivado se deja afuera de la repeticion y de lo que se puede planificar")
    void habitoQueYaNoLeCorresponde() {
        planDeLaSemana.guardar(persona, despertar, MODELO, List.of(mover(SABADO, 9)));
        planDeLaSemana.guardar(persona, gimnasio, MODELO, List.of(mover(SABADO, 8)));
        jdbc.update("UPDATE renaser.habitos SET activo = false WHERE id = ?", gimnasio.value());

        var oferta = repetir.ofrecer(persona).orElseThrow();

        assertThat(oferta.habitos()).extracting(h -> h.habitoId()).containsExactly(despertar);
        assertThat(oferta.excluidos()).extracting(e -> e.habitoId()).containsExactly(gimnasio);
        repetir.repetir(persona, MODELO, DESTINO, false);
        assertThat(load.fechasPropiasDeUnHabitoEntre(persona, gimnasio, DESTINO.lunes(), DESTINO.domingo())).isEmpty();
        assertThatThrownBy(() -> planDeLaSemana.guardar(persona, gimnasio, DESTINO, List.of(mover(SABADO_SIGUIENTE, 8))))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("sin elegir el Dia 1 no se ofrece nada")
    void sinDia1() {
        UserId nueva = usuario();
        jdbc.update("UPDATE renaser.participantes_programa SET dia_programa = 0 WHERE usuario_id = ?", nueva.value());

        assertThat(repetir.ofrecer(nueva)).isEmpty();
    }

    private UserId usuario() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Fixture', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@semana.test");
        jdbc.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, programa_activado_en, timezone)
                VALUES (?, 31, ?, ?, 'America/Lima')
                """, id, LocalDate.of(2026, 9, 28), Timestamp.from(Instant.parse("2026-09-26T15:00:00Z")));
        usuarios.add(id);
        return UserId.of(id);
    }

    private HabitoId habito(String titulo) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.habitos (id, titulo, categoria_clave) VALUES (?, ?, 'CUERPO')", id, titulo + " " + id);
        jdbc.update("""
                INSERT INTO renaser.horarios_habito (habito_id, dia_inicio, tipo_dia, hora_disparo, hora_limite)
                VALUES (?, 1, 'TODOS', '06:00', '22:00')
                """, id);
        habitosCreados.add(id);
        return HabitoId.of(id);
    }
}
