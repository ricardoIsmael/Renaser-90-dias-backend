package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.in.propuesta.ResolverPropuestaUseCase;
import com.renaser.os.rag.application.ports.out.plan.CrearHabitoPersonalPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.renaser.os.rag.application.services.herramientas.PropuestaDeCrearHabitoPersonalTest.invocacion;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * D-229 contra Postgres real: SER propone un habito propio, la persona confirma y el habito existe
 * con su horario y aparece en los tracks del dia; cancelar no crea nada; otra persona no puede
 * confirmar la tarjeta ajena.
 *
 * <p>La herramienta se construye a mano con los beans reales porque solo existe con
 * {@code confirmacion-con-botones} prendido; prenderlo aca abriria otro contexto de Spring para
 * nada. La confirmacion ({@code CrearHabitoPersonalConfirmable}) si es un bean siempre, y es la que
 * ejecuta {@link ResolverPropuestaUseCase}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CrearHabitoPersonalPorPropuestaIT {

    @Autowired
    private GestionarPlanDeHabitosPort planPort;
    @Autowired
    private CrearHabitoPersonalPort crearPort;
    @Autowired
    private ProponerAccionUseCase proponerAccion;
    @Autowired
    private ResolverPropuestaUseCase resolver;
    @Autowired
    private GenerarTracksDelDiaUseCase generarTracks;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private PropuestaDeCrearHabitoPersonal herramienta;
    private final List<UUID> usuarios = new ArrayList<>();

    @BeforeEach
    void armar() {
        herramienta = new PropuestaDeCrearHabitoPersonal(planPort, crearPort, proponerAccion);
    }

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra participacion, habitos, horarios, registros y propuestas.
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        usuarios.clear();
    }

    private UserId aprendiz() {
        UUID id = UUID.randomUUID();
        usuarios.add(id);
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Fixture', 'APRENDIZ', 'ACTIVO')
                """, id, id + "@renaser.test");
        jdbcTemplate.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa) VALUES (?, 23)", id);
        return UserId.of(id);
    }

    private UUID proponerLeer(UserId persona) {
        ResultadoHerramienta resultado = herramienta.ejecutar(persona,
                invocacion("nombre", "Leer 20 minutos", "categoria", "Mente", "hora", "21:00"));
        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Exito.class);
        return proponerAccion.pendienteDe(persona, PropuestaDeCrearHabitoPersonal.NOMBRE).orElseThrow().id();
    }

    private List<UUID> habitosPropiosLlamados(UserId persona, String titulo) {
        return jdbcTemplate.queryForList("SELECT id FROM renaser.habitos WHERE participante_id = ? AND titulo = ?",
                UUID.class, persona.value(), titulo);
    }

    @Test
    @DisplayName("proponer no crea; confirmar crea el habito con su categoria y horario, y aparece en los tracks")
    void proponerYConfirmarCrea() {
        UserId persona = aprendiz();

        UUID propuesta = proponerLeer(persona);
        assertThat(habitosPropiosLlamados(persona, "Leer 20 minutos")).as("proponer no escribe").isEmpty();

        ResultadoHerramienta resultado = resolver.confirmar(persona, propuesta);

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Exito.class);
        List<UUID> creados = habitosPropiosLlamados(persona, "Leer 20 minutos");
        assertThat(creados).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("SELECT categoria_clave FROM renaser.habitos WHERE id = ?",
                String.class, creados.getFirst())).isEqualTo("MENTE");
        assertThat(jdbcTemplate.queryForObject("SELECT hora_disparo::text FROM renaser.horarios_habito WHERE habito_id = ?",
                String.class, creados.getFirst())).startsWith("21:00");
        List<RegistroHabito> tracks = generarTracks.generar(persona, LocalDate.of(2026, 9, 29));
        assertThat(tracks).extracting(registro -> registro.habitoId().value()).contains(creados.getFirst());

        // Un segundo toque devuelve lo mismo y no crea otro.
        resolver.confirmar(persona, propuesta);
        assertThat(habitosPropiosLlamados(persona, "Leer 20 minutos")).hasSize(1);

        // Y ya no se vuelve a proponer: lo tiene.
        ResultadoHerramienta otraVez = herramienta.ejecutar(persona,
                invocacion("nombre", "leer 20 MINUTOS", "categoria", "Mente"));
        assertThat(((ResultadoHerramienta.Fallo) otraVez).motivo()).contains("Ya tiene un habito llamado");
    }

    @Test
    @DisplayName("cancelar no crea nada")
    void cancelarNoCrea() {
        UserId persona = aprendiz();

        resolver.cancelar(persona, proponerLeer(persona));

        assertThat(habitosPropiosLlamados(persona, "Leer 20 minutos")).isEmpty();
    }

    @Test
    @DisplayName("otra persona no puede confirmar la tarjeta ajena")
    void otraPersonaNoConfirma() {
        UserId duena = aprendiz();
        UserId otra = aprendiz();
        UUID propuesta = proponerLeer(duena);

        assertThatThrownBy(() -> resolver.confirmar(otra, propuesta)).isInstanceOf(NotAuthorizedException.class);

        assertThat(habitosPropiosLlamados(duena, "Leer 20 minutos")).isEmpty();
        assertThat(habitosPropiosLlamados(otra, "Leer 20 minutos")).isEmpty();
    }
}
