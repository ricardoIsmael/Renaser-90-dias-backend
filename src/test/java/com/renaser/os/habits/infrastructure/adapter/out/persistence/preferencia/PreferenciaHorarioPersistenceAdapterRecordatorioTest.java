package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.SavePreferenciaHorarioPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.HorarioPorFecha;
import com.renaser.os.habits.domain.model.preferencia.HorarioSemanal;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.habits.domain.model.preferencia.RecordatorioDelDia;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 008 (V111, D-282): el aviso propio de cada dia de la semana. Se prueba por
 * {@code porParticipanteHabitosYFecha}, que es EXACTAMENTE el metodo que llama el barrido de avisos: si
 * la precedencia por campo se resuelve aca, el barrido la ve.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PreferenciaHorarioPersistenceAdapterRecordatorioTest {

    @Autowired EntityManager em;
    @Autowired LoadPreferenciaHorarioPort load;
    @Autowired SavePreferenciaHorarioPort save;
    @Autowired Clock clock;
    UserId actor;
    HabitoId habito;
    LocalDate hoy;

    @BeforeEach
    void seed() {
        actor = UserId.of(UUID.randomUUID());
        habito = HabitoId.of(UUID.randomUUID());
        hoy = clock.now().atZone(ZoneId.of("America/Lima")).toLocalDate();
        em.createNativeQuery("INSERT INTO renaser.usuarios(id,email,nombre_completo,rol,estado) "
                + "VALUES (:id,:email,'Fixture','APRENDIZ','ACTIVO')")
                .setParameter("id", actor.value()).setParameter("email", actor + "@aviso.test").executeUpdate();
        em.createNativeQuery("INSERT INTO renaser.participantes_programa(usuario_id,dia_programa,timezone) "
                + "VALUES (:id,1,'America/Lima')").setParameter("id", actor.value()).executeUpdate();
        em.createNativeQuery("INSERT INTO renaser.habitos(id, ambito, titulo, tipo, categoria_clave) "
                + "VALUES (:id, 'SISTEMA', :titulo, 'CHECKBOX', 'MENTE')")
                .setParameter("id", habito.value()).setParameter("titulo", "Aviso " + habito).executeUpdate();
        em.createNativeQuery("INSERT INTO renaser.horarios_habito(habito_id, dia_inicio, tipo_dia, hora_disparo) "
                + "VALUES (:id, 1, 'TODOS', '06:00')").setParameter("id", habito.value()).executeUpdate();
    }

    /** Hora 07:00 con aviso general de 10 min antes. */
    private void horarioGeneral() {
        var general = PreferenciaHorario.crear(actor, habito, LocalTime.of(7, 0), null, clock.now());
        general.actualizarRecordatorioConAntelaciones(true, List.of(10), clock.now());
        save.save(general);
    }

    private void diaConHora(DayOfWeek dia, LocalTime hora) {
        save.saveParaDiaSemana(actor, habito,
                new HorarioSemanal(dia, PreferenciaHorario.crear(actor, habito, hora, null, clock.now())));
    }

    private PreferenciaHorario resueltaEl(LocalDate fecha) {
        return load.porParticipanteHabitosYFecha(actor, List.of(habito), fecha).stream().findFirst().orElseThrow();
    }

    private LocalDate proximo(DayOfWeek dia) {
        LocalDate f = hoy.plusDays(1);
        while (f.getDayOfWeek() != dia) {
            f = f.plusDays(1);
        }
        return f;
    }

    @Test
    void unaFilaConHoraYSinAvisoDejaLaHoraPropiaYElAvisoGeneral() {
        horarioGeneral();
        diaConHora(DayOfWeek.SATURDAY, LocalTime.of(9, 0));

        var sabado = resueltaEl(proximo(DayOfWeek.SATURDAY));

        assertThat(sabado.horaDisparo()).isEqualTo(LocalTime.of(9, 0));
        assertThat(sabado.recordatorioActivo()).isTrue();
        assertThat(sabado.antelacionesRecordatorio()).containsExactly(10);
        assertThat(sabado.minutosRecordatorio()).isEqualTo(10);
    }

    @Test
    void unaFilaConHoraYAvisoTraeLosDosPropios() {
        horarioGeneral();
        diaConHora(DayOfWeek.SATURDAY, LocalTime.of(9, 0));
        save.fijarRecordatorioParaDiaSemana(actor, habito, DayOfWeek.SATURDAY,
                RecordatorioDelDia.con(List.of(10, 30)));

        var sabado = resueltaEl(proximo(DayOfWeek.SATURDAY));

        assertThat(sabado.horaDisparo()).isEqualTo(LocalTime.of(9, 0));
        assertThat(sabado.recordatorioActivo()).isTrue();
        assertThat(sabado.antelacionesRecordatorio()).containsExactly(30, 10);
        assertThat(sabado.minutosRecordatorio()).as("D-217: los minutos son la antelacion mas temprana").isEqualTo(30);
        assertThat(resueltaEl(proximo(DayOfWeek.FRIDAY)).antelacionesRecordatorio())
                .as("los demas dias siguen con el aviso general").containsExactly(10);
    }

    /** La fila nueva de V111: sin hora propia. Sigue la hora general si esta cambia, y trae el aviso. */
    @Test
    void unaFilaDeSoloAvisoDejaLaHoraGeneralYElAvisoPropio() {
        horarioGeneral();
        save.fijarRecordatorioParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, RecordatorioDelDia.con(List.of(30)));

        var sabado = resueltaEl(proximo(DayOfWeek.SATURDAY));

        assertThat(sabado.horaDisparo()).as("la hora sigue siendo la general").isEqualTo(LocalTime.of(7, 0));
        assertThat(sabado.antelacionesRecordatorio()).containsExactly(30);
        assertThat(sabado.minutosRecordatorio()).isEqualTo(30);
        assertThat(sabado.recordatorioActivo()).isTrue();
    }

    @Test
    void laFilaDeSoloAvisoSinPreferenciaGeneralResuelveElAvisoYDejaLaHoraAlCatalogo() {
        save.fijarRecordatorioParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, RecordatorioDelDia.con(List.of(30)));

        var sabado = resueltaEl(proximo(DayOfWeek.SATURDAY));

        assertThat(sabado.horaDisparo()).as("sin hora propia: la resuelve el catalogo, no esta capa").isNull();
        assertThat(sabado.recordatorioActivo()).isTrue();
        assertThat(sabado.antelacionesRecordatorio()).containsExactly(30);
        assertThat(load.porParticipanteHabitosYFecha(actor, List.of(habito), proximo(DayOfWeek.SUNDAY)))
                .as("otro dia no tiene nada propio").isEmpty();
    }

    /** V40: un dia apagado no pisa ni la hora ni el aviso, aunque la fila traiga un aviso guardado. */
    @Test
    void unaFilaApagadaNoPisaNadaAunqueTengaAviso() {
        horarioGeneral();
        save.fijarRecordatorioParaDiaSemana(actor, habito, DayOfWeek.TUESDAY, RecordatorioDelDia.con(List.of(30)));
        save.saveParaDiaSemana(actor, habito, new HorarioSemanal(DayOfWeek.TUESDAY,
                PreferenciaHorario.crear(actor, habito, null, null, clock.now()), false));

        var martes = resueltaEl(proximo(DayOfWeek.TUESDAY));

        assertThat(martes.horaDisparo()).isEqualTo(LocalTime.of(7, 0));
        assertThat(martes.antelacionesRecordatorio()).as("apagado no aplica su aviso").containsExactly(10);
        assertThat(load.habitosApagadosEnDiaSemana(actor, DayOfWeek.TUESDAY)).containsExactly(habito);
    }

    @Test
    void laExcepcionDeUnaFechaLeGanaATodoIncluidoElAvisoDelDia() {
        horarioGeneral();
        diaConHora(DayOfWeek.SATURDAY, LocalTime.of(9, 0));
        save.fijarRecordatorioParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, RecordatorioDelDia.con(List.of(30)));
        LocalDate eseSabado = proximo(DayOfWeek.SATURDAY);
        var excepcion = PreferenciaHorario.crear(actor, habito, LocalTime.of(11, 0), null, clock.now());
        excepcion.actualizarRecordatorio(true, 5, clock.now());
        save.saveParaFecha(new HorarioPorFecha(eseSabado, excepcion));

        var resuelta = resueltaEl(eseSabado);

        assertThat(resuelta.horaDisparo()).isEqualTo(LocalTime.of(11, 0));
        assertThat(resuelta.minutosRecordatorio()).isEqualTo(5);
        assertThat(resueltaEl(eseSabado.plusWeeks(1)).minutosRecordatorio())
                .as("la semana siguiente vuelve a mandar el aviso del dia").isEqualTo(30);
    }

    /** «Sin aviso» propio es un aviso propio: apaga ese dia aunque el general avise. */
    @Test
    void unDiaSinAvisoPropioQuedaApagadoAunqueElGeneralAvise() {
        horarioGeneral();
        save.fijarRecordatorioParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, RecordatorioDelDia.sinAviso());

        var sabado = resueltaEl(proximo(DayOfWeek.SATURDAY));

        assertThat(sabado.recordatorioActivo()).isFalse();
        assertThat(sabado.minutosRecordatorio()).isNull();
        assertThat(sabado.antelacionesRecordatorio()).isNull();
        assertThat(sabado.horaDisparo()).isEqualTo(LocalTime.of(7, 0));
        assertThat(resueltaEl(proximo(DayOfWeek.FRIDAY)).recordatorioActivo()).isTrue();
    }

    /** FR-013: un APK anterior que guarda la hora de un dia no puede borrar su aviso propio. */
    @Test
    void fijarLaHoraDeUnDiaConservaSuAvisoPropio() {
        horarioGeneral();
        save.fijarRecordatorioParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, RecordatorioDelDia.con(List.of(30)));

        diaConHora(DayOfWeek.SATURDAY, LocalTime.of(9, 0));
        em.flush();
        em.clear();

        var sabado = resueltaEl(proximo(DayOfWeek.SATURDAY));
        assertThat(sabado.horaDisparo()).isEqualTo(LocalTime.of(9, 0));
        assertThat(sabado.antelacionesRecordatorio()).containsExactly(30);
    }

    /** Y al reves: poner el aviso conserva la hora propia del dia. */
    @Test
    void fijarElAvisoDeUnDiaConservaSuHoraPropia() {
        horarioGeneral();
        diaConHora(DayOfWeek.SATURDAY, LocalTime.of(9, 0));

        save.fijarRecordatorioParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, RecordatorioDelDia.sinAviso());
        em.flush();
        em.clear();

        assertThat(resueltaEl(proximo(DayOfWeek.SATURDAY)).horaDisparo()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void apagarUnDiaConservaSuAvisoGuardado() {
        horarioGeneral();
        save.fijarRecordatorioParaDiaSemana(actor, habito, DayOfWeek.TUESDAY, RecordatorioDelDia.con(List.of(30)));

        save.saveParaDiaSemana(actor, habito, new HorarioSemanal(DayOfWeek.TUESDAY,
                PreferenciaHorario.crear(actor, habito, null, null, clock.now()), false));
        em.flush();
        em.clear();

        var fila = load.horarioSemanalDe(actor, habito).getFirst();
        assertThat(fila.activo()).isFalse();
        assertThat(fila.recordatorio()).isEqualTo(RecordatorioDelDia.con(List.of(30)));
    }

    @Test
    void heredarElAvisoQuitaElPropioYConservaLaHora() {
        horarioGeneral();
        diaConHora(DayOfWeek.SATURDAY, LocalTime.of(9, 0));
        save.fijarRecordatorioParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, RecordatorioDelDia.con(List.of(30)));

        save.fijarRecordatorioParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, null);
        em.flush();
        em.clear();

        var sabado = resueltaEl(proximo(DayOfWeek.SATURDAY));
        assertThat(sabado.horaDisparo()).isEqualTo(LocalTime.of(9, 0));
        assertThat(sabado.antelacionesRecordatorio()).as("vuelve al general").containsExactly(10);
        assertThat(load.horarioSemanalDe(actor, habito).getFirst().recordatorio()).isNull();
    }

    /** Una fila de solo aviso que vuelve a heredar queda sin nada propio: se borra, no queda vacia. */
    @Test
    void heredarElAvisoDeUnaFilaDeSoloAvisoBorraLaFila() {
        horarioGeneral();
        save.fijarRecordatorioParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, RecordatorioDelDia.con(List.of(30)));

        save.fijarRecordatorioParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, null);
        em.flush();
        em.clear();

        assertThat(load.horarioSemanalDe(actor, habito)).isEmpty();
        assertThat(load.habitosConEsquemaPorDia(actor, List.of(habito))).isEmpty();
    }

    @Test
    void heredarUnAvisoQueNuncaSePusoNoCreaNada() {
        save.fijarRecordatorioParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, null);
        em.flush();

        assertThat(load.horarioSemanalDe(actor, habito)).isEmpty();
    }

    /** Quitar el dia (DELETE) se lleva hora y aviso, FR-006. */
    @Test
    void quitarElDiaBorraLaHoraYElAviso() {
        horarioGeneral();
        diaConHora(DayOfWeek.SATURDAY, LocalTime.of(9, 0));
        save.fijarRecordatorioParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, RecordatorioDelDia.con(List.of(30)));

        save.borrarParaDiaSemana(actor, habito, DayOfWeek.SATURDAY);
        em.flush();
        em.clear();

        var sabado = resueltaEl(proximo(DayOfWeek.SATURDAY));
        assertThat(sabado.horaDisparo()).isEqualTo(LocalTime.of(7, 0));
        assertThat(sabado.antelacionesRecordatorio()).containsExactly(10);
    }

    @Test
    void elAvisoVaYVuelveDeLaBaseConSuConjunto() {
        save.fijarRecordatorioParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, RecordatorioDelDia.con(List.of(0, 30)));
        save.fijarRecordatorioParaDiaSemana(actor, habito, DayOfWeek.SUNDAY, RecordatorioDelDia.sinAviso());
        em.flush();
        em.clear();

        var filas = load.horariosSemanalesDe(actor, List.of(habito));

        assertThat(filas).hasSize(2);
        assertThat(filas.stream().filter(f -> f.diaSemana() == DayOfWeek.SATURDAY).findFirst().orElseThrow()
                .recordatorio()).isEqualTo(RecordatorioDelDia.con(List.of(30, 0)));
        assertThat(filas.stream().filter(f -> f.diaSemana() == DayOfWeek.SUNDAY).findFirst().orElseThrow()
                .recordatorio()).isEqualTo(RecordatorioDelDia.sinAviso());
        assertThat(filas).allMatch(f -> !f.tieneHoraPropia() && f.activo());
    }

    /**
     * El listado de preferencias (lo que leen el APK anterior y el editor para el aviso GENERAL) no
     * incluye el aviso del dia de la semana; la hora del dia si, como siempre.
     */
    @Test
    void laVarianteSinAvisoDelDiaDejaElAvisoGeneralPeroConservaLaHoraDelDia() {
        horarioGeneral();
        diaConHora(DayOfWeek.SATURDAY, LocalTime.of(9, 0));
        save.fijarRecordatorioParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, RecordatorioDelDia.con(List.of(30)));
        LocalDate sabado = proximo(DayOfWeek.SATURDAY);

        var general = load.porParticipanteHabitosYFechaSinAvisoDelDia(actor, List.of(habito), sabado).getFirst();

        assertThat(general.horaDisparo()).isEqualTo(LocalTime.of(9, 0));
        assertThat(general.antelacionesRecordatorio()).containsExactly(10);
        assertThat(general.minutosRecordatorio()).isEqualTo(10);
    }

    @Test
    void laVarianteSinAvisoDelDiaIgnoraUnaFilaDeSoloAviso() {
        horarioGeneral();
        save.fijarRecordatorioParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, RecordatorioDelDia.sinAviso());

        var general = load.porParticipanteHabitosYFechaSinAvisoDelDia(actor, List.of(habito),
                proximo(DayOfWeek.SATURDAY)).getFirst();

        assertThat(general.recordatorioActivo()).isTrue();
        assertThat(general.antelacionesRecordatorio()).containsExactly(10);
    }

    @Test
    void habitosConEsquemaPorDiaCuentaCualquierFilaDelHabito() {
        var otroHabito = HabitoId.of(UUID.randomUUID());
        em.createNativeQuery("INSERT INTO renaser.habitos(id, ambito, titulo, tipo, categoria_clave) "
                + "VALUES (:id, 'SISTEMA', 'Otro', 'CHECKBOX', 'MENTE')")
                .setParameter("id", otroHabito.value()).executeUpdate();
        assertThat(load.habitosConEsquemaPorDia(actor, List.of(habito, otroHabito))).isEmpty();

        save.fijarRecordatorioParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, RecordatorioDelDia.sinAviso());
        assertThat(load.habitosConEsquemaPorDia(actor, List.of(habito, otroHabito))).containsExactly(habito);

        save.saveParaDiaSemana(actor, otroHabito, new HorarioSemanal(DayOfWeek.MONDAY,
                PreferenciaHorario.crear(actor, otroHabito, null, null, clock.now()), false));
        assertThat(load.habitosConEsquemaPorDia(actor, List.of(habito, otroHabito)))
                .as("un dia apagado tambien cuenta").containsExactlyInAnyOrder(habito, otroHabito);
        assertThat(load.habitosConEsquemaPorDia(actor, List.of())).isEmpty();
    }

    /** El CHECK de V111: un aviso activo sin ninguna antelacion no entra aunque se lo escriba a mano. */
    @Test
    void laBaseRechazaUnAvisoActivoSinAntelaciones() {
        assertThatThrownBy(() -> {
            em.createNativeQuery("INSERT INTO renaser.horario_semanal_habito(participante_id, habito_id, dia_semana, "
                    + "activo, recordatorio_activo) VALUES (:p, :h, 6, true, true)")
                    .setParameter("p", actor.value()).setParameter("h", habito.value()).executeUpdate();
            em.flush();
        }).isInstanceOf(PersistenceException.class).hasMessageContaining("horario_semanal_aviso");
    }
}
