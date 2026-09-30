package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase.PropuestaCreada;
import com.renaser.os.rag.application.ports.out.conversacion.LoadMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.FichaDeHabito;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasiaId;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code proponer_renombrar_habito} (D-236): propone, nunca renombra, con las mismas reglas del
 * endpoint de renombre, y no ofrece un boton que {@code habits} va a rechazar.
 */
class PropuestaDeRenombrarHabitoTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final UUID JUGO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID AGUA = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CAMINAR = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private final GestionarPlanDeHabitosPort planPort = mock(GestionarPlanDeHabitosPort.class);
    private final ProponerAccionUseCase proponerAccion = mock(ProponerAccionUseCase.class);
    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-30T03:00:00Z"));
    private final LoadMensajeRenasiaPort mensajesPort = mock(LoadMensajeRenasiaPort.class);
    private final PropuestaDeRenombrarHabito herramienta = new PropuestaDeRenombrarHabito(planPort, proponerAccion,
            mensajesPort, CLOCK);

    /** Lo que la persona escribio en la ultima hora, como lo devuelve el puerto. */
    private void escribio(String... textos) {
        when(mensajesPort.escritosPorElUsuarioDesde(APRENDIZ, CLOCK.now().minus(PropuestaDeRenombrarHabito.VENTANA_DEL_MOTIVO)))
                .thenReturn(java.util.Arrays.stream(textos).map(texto -> MensajeRenasia.escribirDeUsuario(
                        MensajeRenasiaId.of(UUID.randomUUID()), APRENDIZ, AgenteConversacional.COMPANION, texto,
                        CLOCK.now())).toList());
    }

    private void conFichas() {
        escribio("quiero que mi jugo verde se llame batido de papaya", "es que el apio me cae mal", "tengo gastritis",
                "me gusta mas asi");
        when(planPort.fichasDe(APRENDIZ)).thenReturn(List.of(
                new FichaDeHabito(JUGO, "JUGO VERDE", null, "Foto del vaso con Jugo Verde", true),
                new FichaDeHabito(AGUA, "AGUA TIBIA CON LIMÓN", "Agua tibia sola", "Foto del vaso", true),
                new FichaDeHabito(CAMINAR, "Caminar", null, null, false)));
    }

    private static InvocacionHerramienta invocacion(String habitoId, String accion, String nombre, String motivo) {
        Map<String, String> argumentos = new HashMap<>();
        argumentos.put(PropuestaDeRenombrarHabito.ARGUMENTO_HABITO_ID, habitoId);
        argumentos.put(PropuestaDeRenombrarHabito.ARGUMENTO_ACCION, accion);
        if (nombre != null) {
            argumentos.put(PropuestaDeRenombrarHabito.ARGUMENTO_NOMBRE, nombre);
        }
        if (motivo != null) {
            argumentos.put(PropuestaDeRenombrarHabito.ARGUMENTO_MOTIVO, motivo);
        }
        return new InvocacionHerramienta(PropuestaDeRenombrarHabito.NOMBRE, argumentos);
    }

    @Test
    @DisplayName("propone el renombre con la invocacion normalizada y el resumen exacto, sin renombrar")
    void proponeRenombre() {
        conFichas();

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ,
                invocacion(" " + JUGO + " ", " Renombrar ", "  Batido de papaya ", " me cae mal el apio "));

        String resumen = "Cambiar el nombre de 'JUGO VERDE' a 'Batido de papaya'";
        verify(proponerAccion).proponer(APRENDIZ,
                invocacion(JUGO.toString(), "renombrar", "Batido de papaya", "me cae mal el apio"), resumen);
        verify(planPort, never()).renombrar(any(), any(), any(), any());
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido()).contains(resumen)
                .contains("TODAVIA NO esta hecho");
    }

    @Test
    @DisplayName("volver al original propone quitar el renombre; si no estaba renombrado, no propone")
    void volverAlOriginal() {
        conFichas();

        herramienta.ejecutar(APRENDIZ, invocacion(AGUA.toString(), "volver_al_original", "algo", "algo"));
        verify(proponerAccion).proponer(APRENDIZ, invocacion(AGUA.toString(), "volver_al_original", null, null),
                "Volver a llamar 'Agua tibia sola' por su nombre del programa, 'AGUA TIBIA CON LIMÓN'");

        ResultadoHerramienta sinRenombre = herramienta.ejecutar(APRENDIZ,
                invocacion(JUGO.toString(), "volver_al_original", null, null));
        assertThat(((ResultadoHerramienta.Fallo) sinRenombre).motivo()).contains("ya tiene el nombre del programa");
    }

    @Test
    @DisplayName("un habito que el renombre no acepta no genera propuesta, y dice cuales si")
    void noRenombrable() {
        conFichas();

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ,
                invocacion(CAMINAR.toString(), "renombrar", "Paseo con mi perro", "me gusta mas"));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("'Caminar' no se puede renombrar")
                .contains("habito_id=" + JUGO).doesNotContain("habito_id=" + CAMINAR);
        verifyNoInteractions(proponerAccion);
    }

    @Test
    @DisplayName("sin motivo pide preguntarlo y no inventarlo; los largos son los del endpoint (60 y 200)")
    void reglasDelEndpoint() {
        ResultadoHerramienta sinMotivo = herramienta.ejecutar(APRENDIZ,
                invocacion(JUGO.toString(), "renombrar", "Batido", "  "));
        assertThat(((ResultadoHerramienta.Fallo) sinMotivo).motivo()).contains("Preguntale").contains("no lo inventes");

        ResultadoHerramienta largo = herramienta.ejecutar(APRENDIZ,
                invocacion(JUGO.toString(), "renombrar", "x".repeat(61), "motivo"));
        assertThat(((ResultadoHerramienta.Fallo) largo).motivo()).contains("hasta 60 caracteres");

        ResultadoHerramienta motivoLargo = herramienta.ejecutar(APRENDIZ,
                invocacion(JUGO.toString(), "renombrar", "Batido", "m".repeat(201)));
        assertThat(((ResultadoHerramienta.Fallo) motivoLargo).motivo()).contains("hasta 200 caracteres");
        verifyNoInteractions(proponerAccion);

        conFichas();
        escribio("m".repeat(200));
        herramienta.ejecutar(APRENDIZ, invocacion(JUGO.toString(), "renombrar", "x".repeat(60), "m".repeat(200)));
        verify(proponerAccion).proponer(any(), any(), any());
    }

    @Test
    @DisplayName("el mismo nombre que ya tiene, o un id que no es suyo, no genera propuesta")
    void mismoNombreOIdAjeno() {
        conFichas();

        ResultadoHerramienta mismo = herramienta.ejecutar(APRENDIZ,
                invocacion(AGUA.toString(), "renombrar", "agua tibia sola", "porque si"));
        assertThat(((ResultadoHerramienta.Fallo) mismo).motivo()).contains("ya se llama 'Agua tibia sola'");

        ResultadoHerramienta ajeno = herramienta.ejecutar(APRENDIZ,
                invocacion(UUID.randomUUID().toString(), "renombrar", "Batido", "porque si"));
        assertThat(((ResultadoHerramienta.Fallo) ajeno).motivo()).contains("no es de ninguno de sus habitos");
        verifyNoInteractions(proponerAccion);
    }

    /**
     * E-466: en la prueba con IA real el modelo propuso con motivos que la persona nunca escribio. Contra
     * la herramienta sin la guarda, estas dos llamadas dejaban tarjeta.
     */
    @Test
    @DisplayName("E-466: un motivo que la persona no escribio no genera tarjeta, y pide preguntarlo")
    void motivoInventado() {
        when(planPort.fichasDe(APRENDIZ)).thenReturn(List.of(
                new FichaDeHabito(JUGO, "JUGO VERDE", null, "Foto del vaso con Jugo Verde", true)));
        escribio("quiero que mi jugo verde se llame batido de papaya");

        ResultadoHerramienta deducido = herramienta.ejecutar(APRENDIZ,
                invocacion(JUGO.toString(), "renombrar", "batido de papaya", "Prefiero llamarlo batido de papaya"));
        ResultadoHerramienta copiado = herramienta.ejecutar(APRENDIZ,
                invocacion(JUGO.toString(), "renombrar", "batido de papaya", "porque el apio me cae mal"));
        ResultadoHerramienta soloElPedido = herramienta.ejecutar(APRENDIZ,
                invocacion(JUGO.toString(), "renombrar", "batido de papaya", "quiero que se llame batido de papaya"));

        assertThat(List.of(deducido, copiado, soloElPedido)).allSatisfy(resultado ->
                assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("no lo escribio la persona")
                        .contains("preguntale"));
        verifyNoInteractions(proponerAccion);
    }

    @Test
    @DisplayName("D-176: si ya estaba pendiente la misma, no anuncia una tarjeta nueva")
    void yaPendiente() {
        conFichas();
        when(proponerAccion.proponer(any(), any(), any())).thenReturn(
                new PropuestaCreada(UUID.randomUUID(), "Cambiar el nombre", Instant.now(), true));

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ,
                invocacion(JUGO.toString(), "renombrar", "Batido", "tengo gastritis"));

        assertThat(((ResultadoHerramienta.Exito) resultado).contenido()).contains("no se creo otra");
    }
}
