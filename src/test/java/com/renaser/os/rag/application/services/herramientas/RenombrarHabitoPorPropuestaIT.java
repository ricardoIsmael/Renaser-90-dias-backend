package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.in.propuesta.ResolverPropuestaUseCase;
import com.renaser.os.rag.application.ports.out.conversacion.LoadMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.FichaDeHabito;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasiaId;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * D-236 contra Postgres real: SER propone renombrar JUGO VERDE, la persona confirma y queda en
 * {@code renombres_habito} con el motivo; la ficha lo muestra con los dos nombres; volver al
 * original borra el renombre; y un habito que el endpoint no deja renombrar no genera tarjeta.
 *
 * <p>La herramienta se construye a mano con los beans reales, igual que
 * {@link CrearHabitoPersonalPorPropuestaIT}: solo existe con {@code confirmacion-con-botones}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RenombrarHabitoPorPropuestaIT {

    @Autowired
    private GestionarPlanDeHabitosPort planPort;
    @Autowired
    private ProponerAccionUseCase proponerAccion;
    @Autowired
    private ResolverPropuestaUseCase resolver;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Clock clock;

    private PropuestaDeRenombrarHabito herramienta;
    private final List<UUID> usuarios = new ArrayList<>();

    @BeforeEach
    void armar() {
        // Lo que escribio la persona se dobla: el guardado de mensajes no es lo que prueba esta clase.
        LoadMensajeRenasiaPort mensajes = mock(LoadMensajeRenasiaPort.class);
        when(mensajes.escritosPorElUsuarioDesde(any(), any())).thenAnswer(invocacion -> List.of(
                MensajeRenasia.escribirDeUsuario(MensajeRenasiaId.of(UUID.randomUUID()), invocacion.getArgument(0),
                        AgenteConversacional.COMPANION, "ponle batido de papaya, tengo gastritis", Instant.now())));
        herramienta = new PropuestaDeRenombrarHabito(planPort, proponerAccion, mensajes, clock);
    }

    @AfterEach
    void limpiar() {
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

    private UUID habitoDeClave(String clave) {
        return jdbcTemplate.queryForObject("SELECT id FROM renaser.habitos WHERE clave_sistema = ?", UUID.class, clave);
    }

    private static InvocacionHerramienta invocacion(UUID habitoId, String accion, String nombre, String motivo) {
        Map<String, String> argumentos = new HashMap<>();
        argumentos.put(PropuestaDeRenombrarHabito.ARGUMENTO_HABITO_ID, habitoId.toString());
        argumentos.put(PropuestaDeRenombrarHabito.ARGUMENTO_ACCION, accion);
        if (nombre != null) {
            argumentos.put(PropuestaDeRenombrarHabito.ARGUMENTO_NOMBRE, nombre);
            argumentos.put(PropuestaDeRenombrarHabito.ARGUMENTO_MOTIVO, motivo);
        }
        return new InvocacionHerramienta(PropuestaDeRenombrarHabito.NOMBRE, argumentos);
    }

    private UUID pendiente(UserId persona) {
        return proponerAccion.pendienteDe(persona, PropuestaDeRenombrarHabito.NOMBRE).orElseThrow().id();
    }

    private FichaDeHabito ficha(UserId persona, UUID habitoId) {
        return planPort.fichasDe(persona).stream().filter(f -> f.habitoId().equals(habitoId)).findFirst().orElseThrow();
    }

    private List<Map<String, Object>> renombres(UserId persona) {
        return jdbcTemplate.queryForList("SELECT titulo_personal, motivo FROM renaser.renombres_habito "
                + "WHERE participante_id = ?", persona.value());
    }

    @Test
    @DisplayName("proponer no renombra; confirmar renombra con su motivo; volver al original lo borra")
    void renombrarYVolver() {
        UserId persona = aprendiz();
        UUID jugo = habitoDeClave("GREEN_JUICE");
        assertThat(ficha(persona, jugo).renombrable()).isTrue();

        assertThat(herramienta.ejecutar(persona, invocacion(jugo, "renombrar", "Batido de papaya", "gastritis")))
                .isInstanceOf(ResultadoHerramienta.Exito.class);
        assertThat(renombres(persona)).as("proponer no escribe").isEmpty();

        assertThat(resolver.confirmar(persona, pendiente(persona))).isInstanceOf(ResultadoHerramienta.Exito.class);
        assertThat(renombres(persona)).containsExactly(Map.of("titulo_personal", "Batido de papaya",
                "motivo", "gastritis"));
        FichaDeHabito renombrada = ficha(persona, jugo);
        assertThat(renombrada.tituloVisible()).isEqualTo("Batido de papaya");
        assertThat(renombrada.tituloDelPrograma()).isEqualTo("JUGO VERDE");

        assertThat(herramienta.ejecutar(persona, invocacion(jugo, "volver_al_original", null, null)))
                .isInstanceOf(ResultadoHerramienta.Exito.class);
        assertThat(resolver.confirmar(persona, pendiente(persona))).isInstanceOf(ResultadoHerramienta.Exito.class);
        assertThat(renombres(persona)).isEmpty();
        assertThat(ficha(persona, jugo).renombrado()).isFalse();
    }

    @Test
    @DisplayName("un habito que el renombre de la app no acepta no genera tarjeta")
    void noRenombrableNoPropone() {
        UserId persona = aprendiz();
        UUID despertar = habitoDeClave("WAKE_UP");

        ResultadoHerramienta resultado = herramienta.ejecutar(persona,
                invocacion(despertar, "renombrar", "Arriba", "me gusta mas"));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("no se puede renombrar");
        assertThat(proponerAccion.pendienteDe(persona, PropuestaDeRenombrarHabito.NOMBRE)).isEmpty();
    }
}
