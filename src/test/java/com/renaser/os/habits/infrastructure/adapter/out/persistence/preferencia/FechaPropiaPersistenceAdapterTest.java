package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.SavePreferenciaHorarioPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.AjusteDeAlarma;
import com.renaser.os.habits.domain.model.preferencia.CombinacionDeAlarma;
import com.renaser.os.habits.domain.model.preferencia.FechaPropia;
import com.renaser.os.habits.domain.model.preferencia.HorarioPorFecha;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.habits.domain.model.preferencia.RecordatorioDelDia;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 028 (D-296, V130 y V131): lo que la persona decidio solo para una FECHA —hora, aviso con varias antelaciones y «Como suena»—, guardado en
 * la misma tabla de siempre y resuelto por capas, campo por campo, por la misma funcion que usa el barrido, los avisos y Mi Radar.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class FechaPropiaPersistenceAdapterTest {

    @Autowired EntityManager em;
    @Autowired LoadPreferenciaHorarioPort load;
    @Autowired SavePreferenciaHorarioPort save;
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
                .setParameter("id", habito.value()).setParameter("titulo", "Semana " + habito).executeUpdate();
        em.createNativeQuery("INSERT INTO renaser.horarios_habito(habito_id, dia_inicio, tipo_dia, hora_disparo) "
                + "VALUES (:id, 1, 'TODOS', '06:00')").setParameter("id", habito.value()).executeUpdate();
    }

    UserId participante() {
        UserId id = UserId.of(UUID.randomUUID());
        em.createNativeQuery("INSERT INTO renaser.usuarios(id,email,nombre_completo,rol,estado) "
                + "VALUES (:id,:email,'Fixture','APRENDIZ','ACTIVO')")
                .setParameter("id", id.value()).setParameter("email", id + "@semana.test").executeUpdate();
        em.createNativeQuery("INSERT INTO renaser.participantes_programa(usuario_id,dia_programa,timezone) "
                + "VALUES (:id,1,'America/Lima')").setParameter("id", id.value()).executeUpdate();
        return id;
    }

    /** El plan de siempre: 08:00 con aviso de 10 minutos. */
    private void planDeSiempre() {
        var general = PreferenciaHorario.crear(actor, habito, LocalTime.of(8, 0), null, clock.now());
        general.actualizarRecordatorioConAntelaciones(true, List.of(10), clock.now());
        save.save(general);
    }

    private PreferenciaHorario resuelta(LocalDate fecha) {
        return load.porParticipanteHabitosYFecha(actor, List.of(habito), fecha).stream().findFirst().orElseThrow();
    }

    private FechaPropia fila(LocalDate fecha, boolean activo, LocalTime hora, RecordatorioDelDia aviso, AjusteDeAlarma alarma) {
        return new FechaPropia(habito, fecha, activo, hora, null, aviso, alarma);
    }

    private void guardar(FechaPropia... filas) {
        save.guardarFechasPropias(actor, List.of(filas));
        em.flush();
        em.clear();
    }

    @Test
    void guardaYLeeUnaFilaConHoraAvisosYComoSuena() {
        var vibrar = AjusteDeAlarma.de(CombinacionDeAlarma.de(false, true, false, true));
        guardar(fila(hoy.plusDays(3), true, LocalTime.of(9, 30), RecordatorioDelDia.con(List.of(30, 0)), vibrar));

        var leida = load.fechasPropiasEntre(actor, hoy, hoy.plusDays(20));

        assertThat(leida).singleElement().satisfies(f -> {
            assertThat(f.horaDisparo()).isEqualTo(LocalTime.of(9, 30));
            assertThat(f.recordatorio().antelaciones()).containsExactly(30, 0);
            assertThat(f.alarma()).isEqualTo(vibrar);
            assertThat(f.activo()).isTrue();
        });
    }

    @Test
    void guardarDeNuevoLaMismaFechaLaReemplazaSinDuplicar() {
        guardar(fila(hoy.plusDays(3), true, LocalTime.of(9, 30), null, null));
        guardar(fila(hoy.plusDays(3), true, LocalTime.of(10, 0), null, null));

        assertThat(load.fechasPropiasEntre(actor, hoy, hoy.plusDays(20))).singleElement()
                .satisfies(f -> assertThat(f.horaDisparo()).isEqualTo(LocalTime.of(10, 0)));
    }

    @Test
    void unaFilaDeSoloAvisoNoPisaLaHoraDeSiempreYUnaDeSoloHoraHeredaElAviso() {
        planDeSiempre();
        guardar(fila(hoy.plusDays(2), true, null, RecordatorioDelDia.con(List.of(30, 0)), null),
                fila(hoy.plusDays(3), true, LocalTime.of(9, 0), null, null));

        var soloAviso = resuelta(hoy.plusDays(2));
        assertThat(soloAviso.horaDisparo()).isEqualTo(LocalTime.of(8, 0));
        assertThat(soloAviso.recordatorioActivo()).isTrue();
        assertThat(soloAviso.antelacionesRecordatorio()).containsExactly(30, 0);
        assertThat(soloAviso.minutosRecordatorio()).isEqualTo(30);

        var soloHora = resuelta(hoy.plusDays(3));
        assertThat(soloHora.horaDisparo()).isEqualTo(LocalTime.of(9, 0));
        assertThat(soloHora.recordatorioActivo()).isTrue();
        assertThat(soloHora.antelacionesRecordatorio()).containsExactly(10);

        var otroDia = resuelta(hoy.plusDays(4));
        assertThat(otroDia.horaDisparo()).isEqualTo(LocalTime.of(8, 0));
        assertThat(otroDia.antelacionesRecordatorio()).containsExactly(10);
    }

    @Test
    void unDiaSinAvisoPropioYConAvisoApagadoQuedaSinAviso() {
        planDeSiempre();
        guardar(fila(hoy.plusDays(2), true, null, RecordatorioDelDia.sinAviso(), null));

        var apagado = resuelta(hoy.plusDays(2));

        assertThat(apagado.recordatorioActivo()).isFalse();
        assertThat(apagado.horaDisparo()).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    void unaFilaApagadaOSoloDeAlarmaNoPisaLaHoraNiElAviso() {
        planDeSiempre();
        guardar(FechaPropia.apagada(habito, hoy.plusDays(2)),
                fila(hoy.plusDays(3), true, null, null, AjusteDeAlarma.de(CombinacionDeAlarma.de(true, false, false, false))));

        assertThat(resuelta(hoy.plusDays(2)).horaDisparo()).isEqualTo(LocalTime.of(8, 0));
        assertThat(resuelta(hoy.plusDays(3)).horaDisparo()).isEqualTo(LocalTime.of(8, 0));
        assertThat(resuelta(hoy.plusDays(3)).antelacionesRecordatorio()).containsExactly(10);
        assertThat(load.habitosApagadosEn(actor, hoy.plusDays(2))).containsExactly(habito);
        assertThat(load.habitosApagadosEn(actor, hoy.plusDays(3))).isEmpty();
    }

    @Test
    void unaFilaDeAntesDeV130SeLeeIgualQueSiempre() {
        // La ruta de antes: hora y aviso propios con un solo numero de minutos (PATCH con fecha).
        planDeSiempre();
        var pref = PreferenciaHorario.crear(actor, habito, LocalTime.of(9, 0), null, clock.now());
        pref.actualizarRecordatorio(true, 15, clock.now());
        save.saveParaFecha(new HorarioPorFecha(hoy.plusDays(2), pref));
        em.flush();
        em.clear();

        var resuelta = resuelta(hoy.plusDays(2));
        assertThat(resuelta.horaDisparo()).isEqualTo(LocalTime.of(9, 0));
        assertThat(resuelta.recordatorioActivo()).isTrue();
        assertThat(resuelta.minutosRecordatorio()).isEqualTo(15);

        var comoFecha = load.fechasPropiasEntre(actor, hoy, hoy.plusDays(20)).getFirst();
        assertThat(comoFecha.horaDisparo()).isEqualTo(LocalTime.of(9, 0));
        assertThat(comoFecha.recordatorio().antelaciones()).containsExactly(15);
        assertThat(comoFecha.alarma()).isNull();
    }

    @Test
    void laRutaDeAntesNoBorraComoSuenaDeUnaFechaQueYaLoTenia() {
        var vibrar = AjusteDeAlarma.de(CombinacionDeAlarma.de(false, true, false, false));
        guardar(fila(hoy.plusDays(2), true, LocalTime.of(9, 0), null, vibrar));

        save.saveParaFecha(new HorarioPorFecha(hoy.plusDays(2), PreferenciaHorario.crear(actor, habito, LocalTime.of(10, 0), null, clock.now())));
        em.flush();
        em.clear();

        assertThat(load.fechasPropiasEntre(actor, hoy, hoy.plusDays(20))).singleElement().satisfies(f -> {
            assertThat(f.horaDisparo()).isEqualTo(LocalTime.of(10, 0));
            assertThat(f.alarma()).isEqualTo(vibrar);
        });
    }

    @Test
    void laListaDePreferenciasTraeElAvisoGeneralNoElDeLaFechaPeroSiSuHora() {
        planDeSiempre();
        guardar(fila(hoy.plusDays(2), true, LocalTime.of(9, 0), RecordatorioDelDia.sinAviso(), null));

        var porLista = load.porParticipanteHabitosYFechaSinAvisoDelDia(actor, List.of(habito), hoy.plusDays(2)).getFirst();

        assertThat(porLista.horaDisparo()).isEqualTo(LocalTime.of(9, 0));
        assertThat(porLista.antelacionesRecordatorio()).containsExactly(10);
        assertThat(porLista.recordatorioActivo()).isTrue();
    }

    @Test
    void laListaTraeLasFechasPropiasResueltasHastaVeinteDiasYSoloDeLaPersona() {
        planDeSiempre();
        guardar(fila(hoy.plusDays(3), true, LocalTime.of(9, 30), RecordatorioDelDia.con(List.of(30)), null),
                fila(hoy.plusDays(25), true, LocalTime.of(10, 0), null, null));
        save.guardarFechasPropias(otro, List.of(new FechaPropia(habito, hoy.plusDays(3), true, LocalTime.of(5, 0), null, null, null)));
        em.flush();
        em.clear();

        var item = consultar.consultar(actor).habitos().stream().filter(h -> h.habitoId().equals(habito)).findFirst().orElseThrow();

        assertThat(item.fechasPropias()).singleElement().satisfies(f -> {
            assertThat(f.fecha()).isEqualTo(hoy.plusDays(3));
            assertThat(f.propioDeLaFecha()).isTrue();
            assertThat(f.dia().horaDisparo()).isEqualTo(LocalTime.of(9, 30));
            assertThat(f.dia().recordatorio().antelaciones()).containsExactly(30);
        });
        // El nivel de arriba de la lista sigue siendo el plan de siempre.
        assertThat(item.antelacionesRecordatorio()).containsExactly(10);
    }

    @Test
    void borrarEntreDosFechasBorraSoloEsasDeEsaPersonaYElHabitoPedido() {
        guardar(fila(hoy.plusDays(2), true, LocalTime.of(9, 0), null, null), fila(hoy.plusDays(9), true, LocalTime.of(9, 0), null, null));
        save.guardarFechasPropias(otro, List.of(new FechaPropia(habito, hoy.plusDays(2), true, LocalTime.of(5, 0), null, null, null)));
        em.flush();
        em.clear();

        int borradas = save.borrarFechasPropias(actor, Set.of(), hoy.plusDays(1), hoy.plusDays(7));

        assertThat(borradas).isEqualTo(1);
        assertThat(load.fechasPropiasEntre(actor, hoy, hoy.plusDays(20))).extracting(FechaPropia::fecha).containsExactly(hoy.plusDays(9));
        assertThat(load.fechasPropiasEntre(otro, hoy, hoy.plusDays(20))).hasSize(1);
        assertThat(save.borrarFechasPropias(actor, Set.of(HabitoId.of(UUID.randomUUID())), hoy, hoy.plusDays(20))).isZero();
    }

    @Test
    void laBaseRechazaUnaFilaEncendidaSinNadaPropioYUnaAlarmaSinSonidoNiVibracion() {
        assertThatThrownBy(() -> insertar(hoy.plusDays(2), "true", "NULL", "NULL", "NULL", "NULL"))
                .hasMessageContaining("horarios_fecha_activo_exige_hora_aviso_o_alarma");
    }

    @Test
    void laBaseRechazaUnaAlarmaSinSonidoNiVibracion() {
        assertThatThrownBy(() -> insertar(hoy.plusDays(2), "true", "'09:00'", "false", "false", "NULL"))
                .hasMessageContaining("horarios_fecha_alarma_sonido_o_vibracion");
    }

    @Test
    void laBaseRechazaLaVozSinSonido() {
        assertThatThrownBy(() -> insertar(hoy.plusDays(2), "true", "'09:00'", "false", "true", "true"))
                .hasMessageContaining("horarios_fecha_alarma_voz_con_sonido");
    }

    private void insertar(LocalDate fecha, String activo, String hora, String sonido, String vibracion, String voz) {
        em.createNativeQuery("INSERT INTO renaser.horarios_habito_por_fecha(participante_id, habito_id, fecha, hora_disparo, activo, "
                + "alarma_sonido, alarma_vibracion, alarma_voz, creado_en, actualizado_en) VALUES (:p, :h, :f, " + hora + ", " + activo
                + ", " + sonido + ", " + vibracion + ", " + voz + ", now(), now())")
                .setParameter("p", actor.value()).setParameter("h", habito.value()).setParameter("f", fecha).executeUpdate();
    }
}
