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
}
