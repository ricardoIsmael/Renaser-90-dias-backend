package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase.EditarPreferenciaHorarioCommand;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.SavePreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.SaveCambioHorarioPendientePort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.CambioHorarioPendiente;
import com.renaser.os.habits.domain.model.preferencia.HorarioPorFecha;
import com.renaser.os.habits.domain.model.preferencia.HorarioSemanal;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class HorarioPorFechaPersistenceAdapterTest {
    @Autowired EntityManager em;
    @Autowired LoadPreferenciaHorarioPort load;
    @Autowired SavePreferenciaHorarioPort save;
    @Autowired SaveCambioHorarioPendientePort pendientes;
    @Autowired EditarPreferenciaHorarioUseCase editar;
    @Autowired ConsultarPreferenciasHorarioUseCase consultar;
    @Autowired Clock clock;
    UserId actor;
    UserId otro;
    HabitoId habito;
    LocalDate hoy;

    @BeforeEach
    void seed() {
        actor = participante();
        otro = participante();
        habito = HabitoId.of(UUID.randomUUID());
        hoy = clock.now().atZone(ZoneId.of("America/Lima")).toLocalDate();
        em.createNativeQuery("INSERT INTO renaser.habitos(id, ambito, titulo, tipo, categoria_clave) "
                + "VALUES (:id, 'SISTEMA', :titulo, 'CHECKBOX', 'MENTE')")
                .setParameter("id", habito.value()).setParameter("titulo", "Fecha " + habito).executeUpdate();
        em.createNativeQuery("INSERT INTO renaser.horarios_habito(habito_id, dia_inicio, tipo_dia, hora_disparo) "
                + "VALUES (:id, 1, 'TODOS', '06:00')").setParameter("id", habito.value()).executeUpdate();
    }

    UserId participante() {
        UserId id = UserId.of(UUID.randomUUID());
        em.createNativeQuery("INSERT INTO renaser.usuarios(id,email,nombre_completo,rol,estado) "
                + "VALUES (:id,:email,'Fixture','APRENDIZ','ACTIVO')")
                .setParameter("id", id.value()).setParameter("email", id + "@fecha.test").executeUpdate();
        em.createNativeQuery("INSERT INTO renaser.participantes_programa(usuario_id,dia_programa,timezone) "
                + "VALUES (:id,1,'America/Lima')").setParameter("id", id.value()).executeUpdate();
        return id;
    }

    PreferenciaHorario preferencia(UserId user, int hora) {
        return PreferenciaHorario.crear(user, habito, LocalTime.of(hora, 0), null, clock.now());
    }

    LocalTime hora(UserId user, LocalDate fecha) {
        return consultar.consultar(user, fecha).habitos().stream().filter(h -> h.habitoId().equals(habito))
                .findFirst().orElseThrow().horaDisparo();
    }

    @Test
    void editarUnDiaRecargarYEditarOtroConservaAmbosYElHorarioBase() {
        save.save(preferencia(actor, 7));
        editar.editar(new EditarPreferenciaHorarioCommand(actor, habito, LocalTime.of(9, 0), null,
                false, null, hoy.plusDays(1)));
        em.flush(); em.clear();
        assertThat(hora(actor, hoy)).isEqualTo(LocalTime.of(7, 0));
        assertThat(hora(actor, hoy.plusDays(1))).isEqualTo(LocalTime.of(9, 0));
        assertThat(hora(actor, hoy.plusDays(2))).isEqualTo(LocalTime.of(7, 0));
        assertThat(hora(otro, hoy.plusDays(1))).isEqualTo(LocalTime.of(6, 0));
        editar.editar(new EditarPreferenciaHorarioCommand(actor, habito, LocalTime.of(10, 0), null,
                false, null, hoy.plusDays(2)));
        editar.editar(new EditarPreferenciaHorarioCommand(actor, habito, LocalTime.of(8, 0), null,
                false, null, hoy.plusDays(1)));
        em.flush(); em.clear();
        assertThat(hora(actor, hoy.plusDays(1))).isEqualTo(LocalTime.of(8, 0));
        assertThat(hora(actor, hoy.plusDays(2))).isEqualTo(LocalTime.of(10, 0));
        assertThat(hora(actor, hoy.plusDays(8))).isEqualTo(LocalTime.of(7, 0));
        assertThat(load.porParticipanteYHabito(actor, habito).orElseThrow().horaDisparo()).isEqualTo(LocalTime.of(7, 0));
        assertThat(load.habitosConHorarioEntre(actor, hoy.plusDays(1), hoy.plusDays(2))).containsExactly(habito);
        assertThat(load.habitosConHorarioEntre(actor, hoy.plusDays(3), hoy.plusDays(9))).isEmpty();
    }

    @Test
    void excepcionPuntualGanaAlCambioGeneralPendienteSinExtenderse() {
        save.save(preferencia(actor, 7));
        pendientes.save(CambioHorarioPendiente.programar(actor, habito, LocalTime.of(8, 0), null,
                false, null, hoy.plusDays(1), clock.now()));
        save.saveParaFecha(new HorarioPorFecha(hoy.plusDays(2), preferencia(actor, 9)));
        em.flush(); em.clear();
        assertThat(hora(actor, hoy)).isEqualTo(LocalTime.of(7, 0));
        assertThat(hora(actor, hoy.plusDays(1))).isEqualTo(LocalTime.of(8, 0));
        assertThat(hora(actor, hoy.plusDays(2))).isEqualTo(LocalTime.of(9, 0));
        assertThat(hora(actor, hoy.plusDays(3))).isEqualTo(LocalTime.of(8, 0));
        assertThat(load.porParticipanteHabitosYFecha(actor, List.of(), hoy)).isEmpty();
    }

    // ==================================================================================
    // V39 — hora por DIA DE LA SEMANA. Se prueba por `porParticipanteHabitosYFecha`, que es
    // EXACTAMENTE el metodo que llama el barrido nocturno (`RegistroService.generarInterno`):
    // si la precedencia se resuelve acá, el cron la ve. Ese era el riesgo #1 del plan.
    // ==================================================================================

    /** Fija la hora general del habito, la que rige cuando ningun dia dice otra cosa. */
    private void horarioGeneral(LocalTime disparo) {
        save.save(PreferenciaHorario.crear(actor, habito, disparo, null, clock.now()));
    }

    private void horarioDelDia(DayOfWeek dia, LocalTime disparo) {
        save.saveParaDiaSemana(actor, habito,
                new HorarioSemanal(dia, PreferenciaHorario.crear(actor, habito, disparo, null, clock.now())));
    }

    private LocalTime resueltaEl(LocalDate fecha) {
        return load.porParticipanteHabitosYFecha(actor, List.of(habito), fecha).stream()
                .findFirst().orElseThrow().horaDisparo();
    }

    /** El lunes siguiente a hoy, para no depender de que dia se corra la prueba. */
    private LocalDate proximo(DayOfWeek dia) {
        LocalDate f = hoy.plusDays(1);
        while (f.getDayOfWeek() != dia) {
            f = f.plusDays(1);
        }
        return f;
    }

    @Test
    void laHoraDeUnDiaDeLaSemanaPisaLaGeneralSoloEseDia() {
        horarioGeneral(LocalTime.of(9, 0));
        horarioDelDia(DayOfWeek.MONDAY, LocalTime.of(5, 0));

        assertThat(resueltaEl(proximo(DayOfWeek.MONDAY))).isEqualTo(LocalTime.of(5, 0));
        assertThat(resueltaEl(proximo(DayOfWeek.TUESDAY)))
                .as("el martes no tiene hora propia: sigue rigiendo la general")
                .isEqualTo(LocalTime.of(9, 0));
    }

    /** Lo que el dueno pidio con todas las letras: "que dias sea a las 5 o a las 4". */
    @Test
    void dosDiasDistintosPuedenTenerHorasDistintas() {
        horarioGeneral(LocalTime.of(9, 0));
        horarioDelDia(DayOfWeek.MONDAY, LocalTime.of(5, 0));
        horarioDelDia(DayOfWeek.TUESDAY, LocalTime.of(4, 0));

        assertThat(resueltaEl(proximo(DayOfWeek.MONDAY))).isEqualTo(LocalTime.of(5, 0));
        assertThat(resueltaEl(proximo(DayOfWeek.TUESDAY))).isEqualTo(LocalTime.of(4, 0));
        assertThat(resueltaEl(proximo(DayOfWeek.WEDNESDAY))).isEqualTo(LocalTime.of(9, 0));
    }

    /**
     * La excepcion de FECHA es mas especifica que el patron semanal y tiene que ganarle. Si esto se
     * invierte, "el lunes 8 a las 7" quedaria pisado por "los lunes a las 5" y mover un dia suelto
     * dejaria de funcionar.
     */
    @Test
    void laExcepcionDeFechaExactaLeGanaAlPatronSemanal() {
        horarioGeneral(LocalTime.of(9, 0));
        horarioDelDia(DayOfWeek.MONDAY, LocalTime.of(5, 0));
        LocalDate eseLunes = proximo(DayOfWeek.MONDAY);
        save.saveParaFecha(new HorarioPorFecha(eseLunes,
                PreferenciaHorario.crear(actor, habito, LocalTime.of(7, 0), null, clock.now())));

        assertThat(resueltaEl(eseLunes)).isEqualTo(LocalTime.of(7, 0));
        assertThat(resueltaEl(eseLunes.plusWeeks(1)))
                .as("la semana siguiente vuelve a mandar el patron: la excepcion era de UNA fecha")
                .isEqualTo(LocalTime.of(5, 0));
    }

    /** Quitar la hora del dia devuelve ese dia al horario general. Idempotente. */
    @Test
    void quitarLaHoraDelDiaLoDevuelveAlHorarioGeneral() {
        horarioGeneral(LocalTime.of(9, 0));
        horarioDelDia(DayOfWeek.MONDAY, LocalTime.of(5, 0));

        save.borrarParaDiaSemana(actor, habito, DayOfWeek.MONDAY);
        save.borrarParaDiaSemana(actor, habito, DayOfWeek.MONDAY);

        assertThat(resueltaEl(proximo(DayOfWeek.MONDAY))).isEqualTo(LocalTime.of(9, 0));
    }

    /** El patron es de QUIEN lo puso: el de al lado no hereda nada. */
    @Test
    void elPatronSemanalNoSeLeFiltraAOtroParticipante() {
        horarioGeneral(LocalTime.of(9, 0));
        horarioDelDia(DayOfWeek.MONDAY, LocalTime.of(5, 0));

        assertThat(load.porParticipanteHabitosYFecha(otro, List.of(habito), proximo(DayOfWeek.MONDAY)))
                .as("otro participante no tiene preferencia propia, asi que no resuelve nada")
                .isEmpty();
    }
}
