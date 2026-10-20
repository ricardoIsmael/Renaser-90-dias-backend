package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.SavePreferenciaHorarioPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.AjusteDeAlarma;
import com.renaser.os.habits.domain.model.preferencia.CombinacionDeAlarma;
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
 * Spec 009 (V112, D-283): como suena la alarma de un habito, general y por dia de la semana. Lo de la 008
 * (hora y aviso del dia) tiene que seguir igual: guardar uno conserva los otros y viceversa.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PreferenciaHorarioPersistenceAdapterAlarmaTest {

    private static final CombinacionDeAlarma SOLO_VIBRAR = CombinacionDeAlarma.de(false, true, false, false);
    private static final CombinacionDeAlarma CON_VOZ_Y_REPETIR = CombinacionDeAlarma.de(true, true, true, true);

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
                .setParameter("id", actor.value()).setParameter("email", actor + "@alarma.test").executeUpdate();
        em.createNativeQuery("INSERT INTO renaser.participantes_programa(usuario_id,dia_programa,timezone) "
                + "VALUES (:id,1,'America/Lima')").setParameter("id", actor.value()).executeUpdate();
        em.createNativeQuery("INSERT INTO renaser.habitos(id, ambito, titulo, tipo, categoria_clave) "
                + "VALUES (:id, 'SISTEMA', :titulo, 'CHECKBOX', 'MENTE')")
                .setParameter("id", habito.value()).setParameter("titulo", "Alarma " + habito).executeUpdate();
        em.createNativeQuery("INSERT INTO renaser.horarios_habito(habito_id, dia_inicio, tipo_dia, hora_disparo) "
                + "VALUES (:id, 1, 'TODOS', '06:00')").setParameter("id", habito.value()).executeUpdate();
    }

    /** Hora 07:00 con aviso general de 10 min antes. */
    private PreferenciaHorario horarioGeneral() {
        var general = PreferenciaHorario.crear(actor, habito, LocalTime.of(7, 0), null, clock.now());
        general.actualizarRecordatorioConAntelaciones(true, List.of(10), clock.now());
        return save.save(general);
    }

    private void diaConHora(DayOfWeek dia, LocalTime hora) {
        save.saveParaDiaSemana(actor, habito,
                new HorarioSemanal(dia, PreferenciaHorario.crear(actor, habito, hora, null, clock.now())));
    }

    private HorarioSemanal fila(DayOfWeek dia) {
        return load.horarioSemanalDe(actor, habito).stream().filter(f -> f.diaSemana() == dia).findFirst()
                .orElseThrow();
    }

    private void vaciarCache() {
        em.flush();
        em.clear();
    }

    private LocalDate proximo(DayOfWeek dia) {
        LocalDate f = hoy.plusDays(1);
        while (f.getDayOfWeek() != dia) {
            f = f.plusDays(1);
        }
        return f;
    }

    // ---- la combinacion GENERAL del habito -----------------------------------------------------

    @Test
    void laAlarmaGeneralVaYVuelveDeLaBaseSinTocarHoraNiAviso() {
        var general = horarioGeneral();
        general.actualizarAlarma(AjusteDeAlarma.de(CON_VOZ_Y_REPETIR), clock.now());
        save.save(general);
        vaciarCache();

        var leida = load.porParticipanteYHabito(actor, habito).orElseThrow();

        assertThat(leida.alarma()).isEqualTo(AjusteDeAlarma.de(CON_VOZ_Y_REPETIR));
        assertThat(leida.horaDisparo()).isEqualTo(LocalTime.of(7, 0));
        assertThat(leida.antelacionesRecordatorio()).containsExactly(10);
        assertThat(leida.recordatorioActivo()).isTrue();
    }

    @Test
    void sinNadaGuardadoLaAlarmaGeneralEsNula() {
        horarioGeneral();
        vaciarCache();

        assertThat(load.porParticipanteYHabito(actor, habito).orElseThrow().alarma()).isNull();
    }

    /** Guardar la hora o el aviso desde el camino de siempre no pisa la alarma que ya estaba. */
    @Test
    void editarElAvisoGeneralConservaLaAlarma() {
        var general = horarioGeneral();
        general.actualizarAlarma(AjusteDeAlarma.de(SOLO_VIBRAR), clock.now());
        save.save(general);
        vaciarCache();

        var otra = load.porParticipanteYHabito(actor, habito).orElseThrow();
        otra.actualizarRecordatorioConAntelaciones(true, List.of(30), clock.now());
        save.save(otra);
        vaciarCache();

        var leida = load.porParticipanteYHabito(actor, habito).orElseThrow();
        assertThat(leida.alarma()).isEqualTo(AjusteDeAlarma.de(SOLO_VIBRAR));
        assertThat(leida.antelacionesRecordatorio()).containsExactly(30);
    }

    /** Volver a lo de hoy deja las cuatro columnas en NULL. */
    @Test
    void volverALoDeHoyVaciaLasCuatroColumnas() {
        var general = horarioGeneral();
        general.actualizarAlarma(AjusteDeAlarma.de(SOLO_VIBRAR), clock.now());
        save.save(general);
        vaciarCache();

        var otra = load.porParticipanteYHabito(actor, habito).orElseThrow();
        otra.actualizarAlarma(null, clock.now());
        save.save(otra);
        vaciarCache();

        assertThat(load.porParticipanteYHabito(actor, habito).orElseThrow().alarma()).isNull();
        var columnas = em.createNativeQuery("SELECT alarma_sonido, alarma_vibracion, alarma_voz, alarma_repetir "
                + "FROM renaser.preferencias_horario WHERE participante_id = :p AND habito_id = :h")
                .setParameter("p", actor.value()).setParameter("h", habito.value()).getSingleResult();
        assertThat((Object[]) columnas).containsOnlyNulls();
    }

    /**
     * Un habito sin preferencia que solo guarda como suena: la fila nace SIN hora ni hora limite (la hora sigue
     * siendo la del catalogo) y con el aviso en la forma de siempre (activo, sin minutos): el push no se apaga.
     */
    @Test
    void laFilaQueNaceSoloParaLaAlarmaNoCongelaLaHoraDelCatalogo() {
        var soloAlarma = PreferenciaHorario.crear(actor, habito, null, null, clock.now());
        soloAlarma.actualizarAlarma(AjusteDeAlarma.de(SOLO_VIBRAR), clock.now());
        save.save(soloAlarma);
        vaciarCache();

        var leida = load.porParticipanteYHabito(actor, habito).orElseThrow();
        assertThat(leida.horaDisparo()).isNull();
        assertThat(leida.horaLimite()).isNull();
        assertThat(leida.minutosRecordatorio()).isNull();
        assertThat(leida.recordatorioActivo()).isTrue();
        assertThat(leida.esSoloDeAlarma()).isTrue();
    }

    // ---- la combinacion de UN dia --------------------------------------------------------------

    @Test
    void unDiaGuardaSuAlarmaYLosDemasNoTienen() {
        horarioGeneral();

        save.fijarAlarmaParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, AjusteDeAlarma.de(SOLO_VIBRAR));
        vaciarCache();

        assertThat(fila(DayOfWeek.SATURDAY).alarma()).isEqualTo(AjusteDeAlarma.de(SOLO_VIBRAR));
        assertThat(load.horarioSemanalDe(actor, habito)).hasSize(1);
    }

    /** La fila de solo alarma no trae hora ni aviso: hereda los dos del general. */
    @Test
    void unaFilaDeSoloAlarmaNoTraeHoraNiAviso() {
        horarioGeneral();
        save.fijarAlarmaParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, AjusteDeAlarma.de(SOLO_VIBRAR));
        vaciarCache();

        var sabado = fila(DayOfWeek.SATURDAY);
        assertThat(sabado.activo()).isTrue();
        assertThat(sabado.tieneHoraPropia()).isFalse();
        assertThat(sabado.recordatorio()).isNull();
    }

    /** Lo de la 008 sigue igual: la fila de solo alarma tampoco pisa la hora ni el aviso que rigen ese dia. */
    @Test
    void laFilaDeSoloAlarmaNoCambiaLaHoraNiElAvisoResueltos() {
        horarioGeneral();
        save.fijarAlarmaParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, AjusteDeAlarma.de(SOLO_VIBRAR));
        vaciarCache();

        var sabado = load.porParticipanteHabitosYFecha(actor, List.of(habito), proximo(DayOfWeek.SATURDAY))
                .getFirst();

        assertThat(sabado.horaDisparo()).isEqualTo(LocalTime.of(7, 0));
        assertThat(sabado.antelacionesRecordatorio()).containsExactly(10);
        assertThat(sabado.minutosRecordatorio()).isEqualTo(10);
    }

    @Test
    void laFilaDeSoloAlarmaCuentaComoEsquemaPorDia() {
        assertThat(load.habitosConEsquemaPorDia(actor, List.of(habito))).isEmpty();

        save.fijarAlarmaParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, AjusteDeAlarma.de(SOLO_VIBRAR));

        assertThat(load.habitosConEsquemaPorDia(actor, List.of(habito))).containsExactly(habito);
    }

    @Test
    void guardarLaHoraDelDiaConservaSuAlarma() {
        horarioGeneral();
        save.fijarAlarmaParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, AjusteDeAlarma.de(SOLO_VIBRAR));

        diaConHora(DayOfWeek.SATURDAY, LocalTime.of(9, 0));
        vaciarCache();

        var sabado = fila(DayOfWeek.SATURDAY);
        assertThat(sabado.preferencia().horaDisparo()).isEqualTo(LocalTime.of(9, 0));
        assertThat(sabado.alarma()).isEqualTo(AjusteDeAlarma.de(SOLO_VIBRAR));
    }

    @Test
    void guardarElAvisoDelDiaConservaSuAlarmaYLaAlarmaConservaElAviso() {
        horarioGeneral();
        save.fijarAlarmaParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, AjusteDeAlarma.de(SOLO_VIBRAR));
        save.fijarRecordatorioParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, RecordatorioDelDia.con(List.of(30)));
        vaciarCache();

        var sabado = fila(DayOfWeek.SATURDAY);
        assertThat(sabado.recordatorio()).isEqualTo(RecordatorioDelDia.con(List.of(30)));
        assertThat(sabado.alarma()).isEqualTo(AjusteDeAlarma.de(SOLO_VIBRAR));

        save.fijarAlarmaParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, AjusteDeAlarma.de(CON_VOZ_Y_REPETIR));
        vaciarCache();

        sabado = fila(DayOfWeek.SATURDAY);
        assertThat(sabado.alarma()).isEqualTo(AjusteDeAlarma.de(CON_VOZ_Y_REPETIR));
        assertThat(sabado.recordatorio()).isEqualTo(RecordatorioDelDia.con(List.of(30)));
    }

    /** Quitar el aviso de una fila que sigue teniendo alarma NO la borra: todavia dice algo propio. */
    @Test
    void heredarElAvisoDeUnaFilaConAlarmaNoBorraLaFila() {
        horarioGeneral();
        save.fijarAlarmaParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, AjusteDeAlarma.de(SOLO_VIBRAR));
        save.fijarRecordatorioParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, RecordatorioDelDia.con(List.of(30)));

        save.fijarRecordatorioParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, null);
        vaciarCache();

        var sabado = fila(DayOfWeek.SATURDAY);
        assertThat(sabado.recordatorio()).isNull();
        assertThat(sabado.alarma()).isEqualTo(AjusteDeAlarma.de(SOLO_VIBRAR));
    }

    @Test
    void apagarUnDiaConservaSuAlarmaGuardada() {
        horarioGeneral();
        save.fijarAlarmaParaDiaSemana(actor, habito, DayOfWeek.TUESDAY, AjusteDeAlarma.de(SOLO_VIBRAR));

        save.saveParaDiaSemana(actor, habito, new HorarioSemanal(DayOfWeek.TUESDAY,
                PreferenciaHorario.crear(actor, habito, null, null, clock.now()), false));
        vaciarCache();

        var martes = fila(DayOfWeek.TUESDAY);
        assertThat(martes.activo()).isFalse();
        assertThat(martes.alarma()).isEqualTo(AjusteDeAlarma.de(SOLO_VIBRAR));
    }

    /** Heredar la alarma de una fila que no tiene otra cosa propia borra la fila, no la deja vacia. */
    @Test
    void heredarLaAlarmaDeUnaFilaDeSoloAlarmaBorraLaFila() {
        horarioGeneral();
        save.fijarAlarmaParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, AjusteDeAlarma.de(SOLO_VIBRAR));

        save.fijarAlarmaParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, null);
        vaciarCache();

        assertThat(load.horarioSemanalDe(actor, habito)).isEmpty();
        assertThat(load.habitosConEsquemaPorDia(actor, List.of(habito))).isEmpty();
    }

    @Test
    void heredarLaAlarmaDeUnaFilaConHoraConservaLaFilaYLaHora() {
        horarioGeneral();
        diaConHora(DayOfWeek.SATURDAY, LocalTime.of(9, 0));
        save.fijarAlarmaParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, AjusteDeAlarma.de(SOLO_VIBRAR));

        save.fijarAlarmaParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, AjusteDeAlarma.hereda());
        vaciarCache();

        var sabado = fila(DayOfWeek.SATURDAY);
        assertThat(sabado.alarma()).isNull();
        assertThat(sabado.preferencia().horaDisparo()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void heredarUnaAlarmaQueNuncaSePusoNoCreaNada() {
        save.fijarAlarmaParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, null);
        em.flush();

        assertThat(load.horarioSemanalDe(actor, habito)).isEmpty();
    }

    /** FR-010: quitar el dia (DELETE) se lleva tambien la combinacion propia. */
    @Test
    void quitarElDiaBorraLaAlarma() {
        horarioGeneral();
        diaConHora(DayOfWeek.SATURDAY, LocalTime.of(9, 0));
        save.fijarAlarmaParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, AjusteDeAlarma.de(SOLO_VIBRAR));

        save.borrarParaDiaSemana(actor, habito, DayOfWeek.SATURDAY);
        vaciarCache();

        assertThat(load.horarioSemanalDe(actor, habito)).isEmpty();
    }

    @Test
    void lasAlarmasDeVariosDiasVanYVuelvenEnUnaSolaLectura() {
        save.fijarAlarmaParaDiaSemana(actor, habito, DayOfWeek.SATURDAY, AjusteDeAlarma.de(SOLO_VIBRAR));
        save.fijarAlarmaParaDiaSemana(actor, habito, DayOfWeek.MONDAY, AjusteDeAlarma.de(CON_VOZ_Y_REPETIR));
        vaciarCache();

        var filas = load.horariosSemanalesDe(actor, List.of(habito));

        assertThat(filas).hasSize(2);
        assertThat(filas.stream().filter(f -> f.diaSemana() == DayOfWeek.SATURDAY).findFirst().orElseThrow().alarma())
                .isEqualTo(AjusteDeAlarma.de(SOLO_VIBRAR));
        assertThat(filas.stream().filter(f -> f.diaSemana() == DayOfWeek.MONDAY).findFirst().orElseThrow().alarma())
                .isEqualTo(AjusteDeAlarma.de(CON_VOZ_Y_REPETIR));
    }

    /** La preferencia que lee el barrido de avisos trae la alarma general (los dias no la cambian). */
    @Test
    void laPreferenciaResueltaPorFechaConservaLaAlarmaGeneral() {
        var general = horarioGeneral();
        general.actualizarAlarma(AjusteDeAlarma.de(SOLO_VIBRAR), clock.now());
        save.save(general);
        diaConHora(DayOfWeek.SATURDAY, LocalTime.of(9, 0));
        vaciarCache();

        var sabado = load.porParticipanteHabitosYFecha(actor, List.of(habito), proximo(DayOfWeek.SATURDAY)).getFirst();

        assertThat(sabado.horaDisparo()).isEqualTo(LocalTime.of(9, 0));
        assertThat(sabado.alarma()).isEqualTo(AjusteDeAlarma.de(SOLO_VIBRAR));
    }

    // ---- los CHECK de V112 ---------------------------------------------------------------------

    private void insertarFilaDelDia(String columnas, String valores) {
        em.createNativeQuery("INSERT INTO renaser.horario_semanal_habito(participante_id, habito_id, dia_semana, "
                + "activo, " + columnas + ") VALUES (:p, :h, 6, true, " + valores + ")")
                .setParameter("p", actor.value()).setParameter("h", habito.value()).executeUpdate();
        em.flush();
    }

    @Test
    void laBaseAceptaUnaFilaDeSoloAlarma() {
        insertarFilaDelDia("alarma_sonido, alarma_vibracion", "false, true");

        assertThat(load.horarioSemanalDe(actor, habito)).hasSize(1);
    }

    @Test
    void laBaseRechazaUnDiaEncendidoSinHoraAvisoNiAlarma() {
        assertThatThrownBy(() -> insertarFilaDelDia("alarma_repetir", "NULL"))
                .isInstanceOf(PersistenceException.class).hasMessageContaining("horario_semanal_activo_exige");
    }

    @Test
    void laBaseRechazaSonidoYVibracionApagadosEnElDia() {
        assertThatThrownBy(() -> insertarFilaDelDia("alarma_sonido, alarma_vibracion", "false, false"))
                .isInstanceOf(PersistenceException.class).hasMessageContaining("horario_semanal_alarma");
    }

    @Test
    void laBaseRechazaVozSinSonidoEnElDia() {
        assertThatThrownBy(() -> insertarFilaDelDia("alarma_sonido, alarma_vibracion, alarma_voz", "false, true, true"))
                .isInstanceOf(PersistenceException.class).hasMessageContaining("horario_semanal_alarma");
    }

    @Test
    void laBaseRechazaSonidoYVibracionApagadosEnLaGeneral() {
        assertThatThrownBy(() -> {
            em.createNativeQuery("INSERT INTO renaser.preferencias_horario(participante_id, habito_id, "
                    + "alarma_sonido, alarma_vibracion) VALUES (:p, :h, false, false)")
                    .setParameter("p", actor.value()).setParameter("h", habito.value()).executeUpdate();
            em.flush();
        }).isInstanceOf(PersistenceException.class).hasMessageContaining("preferencias_horario_alarma");
    }

    @Test
    void laBaseRechazaVozSinSonidoEnLaGeneral() {
        assertThatThrownBy(() -> {
            em.createNativeQuery("INSERT INTO renaser.preferencias_horario(participante_id, habito_id, "
                    + "alarma_sonido, alarma_voz) VALUES (:p, :h, false, true)")
                    .setParameter("p", actor.value()).setParameter("h", habito.value()).executeUpdate();
            em.flush();
        }).isInstanceOf(PersistenceException.class).hasMessageContaining("preferencias_horario_alarma");
    }
}
