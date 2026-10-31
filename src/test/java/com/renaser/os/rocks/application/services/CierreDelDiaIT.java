package com.renaser.os.rocks.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.api.EntradaDiarioFinder;
import com.renaser.os.rocks.api.CierresDelDiaFinder;
import com.renaser.os.rocks.api.CierresDelDiaFinder.CierresDeLaSemana;
import com.renaser.os.rocks.application.ports.in.cierre.CerrarElDiaUseCase;
import com.renaser.os.rocks.application.ports.in.cierre.CerrarElDiaUseCase.CerrarElDia;
import com.renaser.os.rocks.application.ports.in.cierre.CerrarElDiaUseCase.RespuestaDeAccion;
import com.renaser.os.rocks.application.ports.in.cierre.ConsultarCierreDelDiaUseCase;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.rocks.application.ports.out.rocadiaria.SaveRocaDiariaPort;
import com.renaser.os.rocks.domain.model.cierre.CierreDeOtroDiaException;
import com.renaser.os.rocks.domain.model.cierre.MotivoDeAccionPendiente;
import com.renaser.os.rocks.domain.model.cierre.PlanDeManana;
import com.renaser.os.rocks.domain.model.cierre.RespuestaDeCierre;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * «Cierra tu día» contra Postgres de verdad (spec 017, V121). Escrita para correr con {@code ./mvnw verify} (failsafe): necesita Docker.
 * Fija lo que ningún doble puede: que los {@code CHECK} de V121 frenen un valor roto, que cerrar deje {@code completada} y los puntos
 * exactamente como estaban —también cuando la persona cumple la acción a la vez—, que la marca sea una fila {@code EVALUACION_DIA} y que
 * el Espejo Sombra no la lea.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DisplayName("Cierra tu día: lo que se guarda de verdad (spec 017)")
class CierreDelDiaIT {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired
    private CerrarElDiaUseCase cerrar;
    @Autowired
    private ConsultarCierreDelDiaUseCase consultar;
    @Autowired
    private CierresDelDiaFinder semana;
    @Autowired
    private EntradaDiarioFinder espejo;
    @Autowired
    private LoadRocaDiariaPort cargar;
    @Autowired
    private SaveRocaDiariaPort guardar;
    @Autowired
    private Clock clock;
    @Autowired
    private JdbcTemplate jdbc;

    private UserId persona;
    private LocalDate hoy;
    private UUID cumplida;
    private UUID pendiente;

    @BeforeEach
    void sembrar() {
        UUID id = UUID.randomUUID();
        persona = UserId.of(id);
        hoy = clock.now().atZone(LIMA).toLocalDate();
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) "
                + "VALUES (?, ?, 'Prueba', 'APRENDIZ', 'ACTIVO')", id, id + "@prueba.test");
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, fecha_inicio, dia_programa, "
                + "programa_activado_en, timezone) VALUES (?, ?, 10, now(), 'America/Lima')", id, hoy.minusDays(9));
        cumplida = sembrarRoca(1, "Enviar la propuesta", true, 8);
        pendiente = sembrarRoca(2, "Revisar las facturas", false, 0);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", persona.value());
    }

    private UUID sembrarRoca(int posicion, String titulo, boolean completada, int puntos) {
        return sembrarRoca(posicion, titulo, completada, puntos, "CUERPO");
    }

    /** Hay una restricción única (persona, fecha, eje, posición): otra acción en el mismo día va en otro eje. */
    private UUID sembrarRoca(int posicion, String titulo, boolean completada, int puntos, String eje) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.rocas_diarias (id, participante_id, fecha, posicion, titulo, color, puntaje_impacto, eje, "
                        + "completada, completada_en, puntos_otorgados) VALUES (?, ?, ?, ?, ?, ?::renaser.color_pareto, 5, "
                        + "?::renaser.eje_objetivo, ?, ?, ?)",
                id, persona.value(), hoy, posicion, titulo, posicion == 1 ? "VERDE" : "AMARILLA", eje, completada,
                completada ? java.sql.Timestamp.from(clock.now()) : null, puntos);
        return id;
    }

    /** Una acción pendiente con hora de inicio (`HH:mm:ss`). */
    private UUID sembrarPendienteConHora(String eje, String titulo, String hora) {
        UUID id = sembrarRoca(1, titulo, false, 0, eje);
        jdbc.update("UPDATE renaser.rocas_diarias SET hora_inicio = ?::time WHERE id = ?", hora, id);
        return id;
    }

    private Map<String, Object> fila(UUID id) {
        return jdbc.queryForMap("SELECT completada, puntos_otorgados, completada_en, cierre_motivo, cierre_manana, cierre_nota, cierre_en "
                + "FROM renaser.rocas_diarias WHERE id = ?", id);
    }

    private CerrarElDia cierre(String nota) {
        return new CerrarElDia(persona, hoy, List.of(new RespuestaDeAccion(pendiente, "SIN_TIEMPO", "OTRA_HORA", "Se alargó la reunión")), nota);
    }

    @Test
    @DisplayName("cerrar guarda la respuesta y la marca, y deja completada y puntos EXACTAMENTE como estaban")
    void cerrarNoCumpleNiPaga() {
        cerrar.cerrar(cierre("Descansé"));

        Map<String, Object> hecha = fila(cumplida);
        Map<String, Object> sinHacer = fila(pendiente);
        assertThat(hecha.get("completada")).isEqualTo(true);
        assertThat(((Number) hecha.get("puntos_otorgados")).intValue()).isEqualTo(8);
        assertThat(sinHacer.get("completada")).isEqualTo(false);
        assertThat(((Number) sinHacer.get("puntos_otorgados")).intValue()).isZero();
        assertThat(sinHacer.get("completada_en")).isNull();
        assertThat(sinHacer.get("cierre_motivo")).isEqualTo("SIN_TIEMPO");
        assertThat(sinHacer.get("cierre_manana")).isEqualTo("OTRA_HORA");
        assertThat(sinHacer.get("cierre_nota")).isEqualTo("Se alargó la reunión");
        assertThat(hecha.get("cierre_motivo")).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.entradas_diario WHERE participante_id = ? AND fecha = ? "
                + "AND tipo = 'EVALUACION_DIA'::renaser.tipo_entrada_diario AND contenido_texto = 'Descansé'",
                Integer.class, persona.value(), hoy)).isEqualTo(1);
        assertThat(consultar.consultar(persona, null).dia().estado()).isEqualTo("CERRADO");
    }

    @Test
    @DisplayName("volver a cerrar reemplaza la respuesta y no duplica la marca del dia")
    void cerrarDosVeces() {
        cerrar.cerrar(cierre("Primera"));
        cerrar.cerrar(new CerrarElDia(persona, hoy, List.of(new RespuestaDeAccion(pendiente, "SIN_ENERGIA", "DEJARLA", null)), null));

        assertThat(fila(pendiente).get("cierre_motivo")).isEqualTo("SIN_ENERGIA");
        assertThat(fila(pendiente).get("cierre_nota")).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.entradas_diario WHERE participante_id = ? AND tipo = 'EVALUACION_DIA'::renaser.tipo_entrada_diario",
                Integer.class, persona.value())).isEqualTo(1);
    }

    @Test
    @DisplayName("un dia que no es hoy no se cierra: 409 y no queda nada")
    void cerrarAyer() {
        assertThatThrownBy(() -> cerrar.cerrar(new CerrarElDia(persona, hoy.minusDays(1), List.of(), null)))
                .isInstanceOf(CierreDeOtroDiaException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.entradas_diario WHERE participante_id = ?", Integer.class, persona.value())).isZero();
    }

    @Test
    @DisplayName("si la persona cumple la accion mientras cierra, guardar el cierre NO pisa el cumplimiento ni los puntos")
    void cumplirAlMismoTiempo() {
        RocaDiaria vieja = cargar.deParticipanteYFecha(persona, hoy).stream()
                .filter(r -> r.id().value().equals(pendiente)).findFirst().orElseThrow();
        vieja.cerrarConRespuesta(new RespuestaDeCierre(MotivoDeAccionPendiente.SIN_TIEMPO, PlanDeManana.OTRA_HORA, null), clock.now());
        // Mientras tanto, otro telefono cumple la accion y se le pagan los puntos.
        jdbc.update("UPDATE renaser.rocas_diarias SET completada = true, completada_en = now(), puntos_otorgados = 9 WHERE id = ?", pendiente);

        guardar.guardarSoloElCierre(List.of(vieja));

        Map<String, Object> fila = fila(pendiente);
        assertThat(fila.get("completada")).isEqualTo(true);
        assertThat(((Number) fila.get("puntos_otorgados")).intValue()).isEqualTo(9);
        assertThat(fila.get("cierre_motivo")).isNull();
    }

    @Test
    @DisplayName("los CHECK de V121 frenan un valor roto, una respuesta a medias y una nota sin respuesta")
    void checksDeLaMigracion() {
        assertThatThrownBy(() -> jdbc.update("UPDATE renaser.rocas_diarias SET cierre_motivo = 'OTRO', cierre_manana = 'DEJARLA', cierre_en = now() WHERE id = ?", pendiente))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE renaser.rocas_diarias SET cierre_motivo = 'SIN_TIEMPO', cierre_en = now() WHERE id = ?", pendiente))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE renaser.rocas_diarias SET cierre_nota = 'suelta' WHERE id = ?", pendiente))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE renaser.rocas_diarias SET cierre_motivo = 'SIN_TIEMPO', cierre_manana = 'DEJARLA', cierre_en = now(), cierre_nota = '' WHERE id = ?", pendiente))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("la nota del cierre no entra al Espejo Sombra: EntradaDiarioFinder no devuelve EVALUACION_DIA")
    void elEspejoNoLaLee() {
        cerrar.cerrar(cierre("Esto es para mi mentor"));

        assertThat(espejo.entradasEntre(persona, hoy.minusDays(7), hoy)).isEmpty();
    }

    @Test
    @DisplayName("la ficha lee la semana: hoy cerrado, con la respuesta de la accion pendiente")
    void lecturaDeLaSemana() {
        cerrar.cerrar(cierre(null));

        CierresDeLaSemana lectura = semana.semanaDe(persona, null);

        assertThat(lectura.dias()).extracting(d -> d.fecha()).contains(hoy);
        var hoyDelCierre = lectura.dias().stream().filter(d -> d.fecha().equals(hoy)).findFirst().orElseThrow();
        assertThat(hoyDelCierre.estado()).isEqualTo("CERRADO");
        assertThat(hoyDelCierre.acciones()).filteredOn(a -> !a.cumplida()).singleElement()
                .satisfies(a -> assertThat(a.cierre().motivo()).isEqualTo("SIN_TIEMPO"));
        assertThat(lectura.resumen().cerrados()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("solo se exige lo que ya llegó a su hora: la de las 23:59:59 no, la de las 00:00 sí y la sin hora sí (hora de la persona)")
    void soloSeExigeLoQueYaLlego() {
        UUID futura = sembrarPendienteConHora("TRABAJO", "Cena con el equipo", "23:59:59");
        UUID pasada = sembrarPendienteConHora("RELACIONES", "Madrugar a revisar", "00:00:00");

        assertThat(consultar.consultar(persona, null).exigidas()).contains(pendiente, pasada).doesNotContain(futura, cumplida);
        // Sin la respuesta de la que ya pasó (y de la sin hora) no se puede cerrar: 400 y nada queda a medio guardar.
        assertThatThrownBy(() -> cerrar.cerrar(new CerrarElDia(persona, hoy,
                List.of(new RespuestaDeAccion(pendiente, "SIN_TIEMPO", "OTRA_HORA", null)), null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(fila(pendiente).get("cierre_motivo")).isNull();

        // Con las exigidas basta: la de hora futura queda sin respuesta, pendiente y sin puntos.
        cerrar.cerrar(new CerrarElDia(persona, hoy, List.of(
                new RespuestaDeAccion(pendiente, "SIN_TIEMPO", "OTRA_HORA", null),
                new RespuestaDeAccion(pasada, "SIN_ENERGIA", "MAS_CORTA", null)), null));

        assertThat(fila(pasada).get("cierre_motivo")).isEqualTo("SIN_ENERGIA");
        assertThat(fila(futura).get("cierre_motivo")).isNull();
        assertThat(fila(futura).get("completada")).isEqualTo(false);
        assertThat(((Number) fila(futura).get("puntos_otorgados")).intValue()).isZero();
        assertThat(consultar.consultar(persona, null).dia().estado()).isEqualTo("CERRADO");
    }
}
