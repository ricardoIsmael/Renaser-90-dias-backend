package com.renaser.os.rocks.infrastructure.adapter.in.rest.cierre;

import com.renaser.os.rocks.api.CierresDelDiaFinder.AccionDelCierre;
import com.renaser.os.rocks.api.CierresDelDiaFinder.DiaDelCierre;
import com.renaser.os.rocks.api.CierresDelDiaFinder.RespuestaDelCierre;
import com.renaser.os.rocks.application.ports.in.cierre.CerrarElDiaSinRedUseCase;
import com.renaser.os.rocks.application.ports.in.cierre.CerrarElDiaUseCase;
import com.renaser.os.rocks.application.ports.in.cierre.CerrarElDiaUseCase.CerrarElDia;
import com.renaser.os.rocks.application.ports.in.cierre.ConsultarCierreDelDiaUseCase;
import com.renaser.os.rocks.application.ports.in.cierre.ConsultarCierreDelDiaUseCase.AvanceDeLaSemana;
import com.renaser.os.rocks.application.ports.in.cierre.ConsultarCierreDelDiaUseCase.VistaDelCierre;
import com.renaser.os.rocks.domain.model.cierre.CierreDeOtroDiaException;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.NoAplicadoSinRedException;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.OrigenDeLaHora;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET/PUT /api/v1/rocks/day-closure} (spec 017): el contrato, que nada se cachee, la validación de la entrada y cómo
 * se traduce cada error. El permiso es {@code FOLLOW_OWN_PROGRAM}; que la cuenta no esté suspendida y que el programa esté andando
 * lo decide el servicio (probado en {@code CierreDelDiaServiceTest}): acá los casos de uso son dobles.
 */
@WebMvcTest(CierreDelDiaController.class)
@AutoConfigureMockMvc(addFilters = false)
class CierreDelDiaControllerTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 28);
    private static final UserId ANA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID FACTURAS = UUID.fromString("00000000-0000-0000-0000-0000000000f3");

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ConsultarCierreDelDiaUseCase consultar;
    @MockitoBean
    private CerrarElDiaUseCase cerrar;
    @MockitoBean
    private CerrarElDiaSinRedUseCase cerrarSinRed;

    @BeforeEach
    void preparar() {
        reset(consultar, cerrar, cerrarSinRed);
        VistaDelCierre vista = new VistaDelCierre(new DiaDelCierre(HOY, "CERRADO", Instant.parse("2026-10-29T03:02:00Z"), "Descansé",
                List.of(new AccionDelCierre(UUID.randomUUID(), "Enviar la propuesta", LocalTime.of(10, 0), true,
                                Instant.parse("2026-10-28T15:20:00Z"), null),
                        new AccionDelCierre(FACTURAS, "Revisar las facturas", LocalTime.of(20, 30), false, null,
                                new RespuestaDelCierre("SIN_TIEMPO", "OTRA_HORA", "Se alargó la reunión")))),
                true, new AvanceDeLaSemana(7, 12), Set.of(FACTURAS));
        when(consultar.consultar(any(), any())).thenReturn(vista);
        when(cerrar.cerrar(any())).thenReturn(vista);
        when(cerrarSinRed.cerrar(any(), any())).thenReturn(vista);
    }

    private ResultActions cerrarCon(String cuerpo) throws Exception {
        return mockMvc.perform(put("/api/v1/rocks/day-closure").header("X-Actor-Id", ANA.toString())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static String cuerpoValido() {
        return """
                {"fecha":"2026-10-28","nota":"Descansé","acciones":[
                  {"id":"%s","motivo":"SIN_TIEMPO","manana":"OTRA_HORA","nota":"Se alargó la reunión"}]}
                """.formatted(FACTURAS);
    }

    @Test
    @DisplayName("GET devuelve el cierre con el JSON del contrato y sin cache")
    void contratoDelGet() throws Exception {
        mockMvc.perform(get("/api/v1/rocks/day-closure").header("X-Actor-Id", ANA.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.fecha").value("2026-10-28"))
                .andExpect(jsonPath("$.estado").value("CERRADO"))
                .andExpect(jsonPath("$.puedeCerrar").value(true))
                .andExpect(jsonPath("$.cerradoEn").value("2026-10-29T03:02:00Z"))
                .andExpect(jsonPath("$.nota").value("Descansé"))
                .andExpect(jsonPath("$.acciones", hasSize(2)))
                .andExpect(jsonPath("$.acciones[0].titulo").value("Enviar la propuesta"))
                .andExpect(jsonPath("$.acciones[0].horaInicio").value("10:00:00"))
                .andExpect(jsonPath("$.acciones[0].cumplida").value(true))
                .andExpect(jsonPath("$.acciones[0].cierre").value(nullValue()))
                .andExpect(jsonPath("$.acciones[1].cumplida").value(false))
                .andExpect(jsonPath("$.acciones[1].cierre.motivo").value("SIN_TIEMPO"))
                .andExpect(jsonPath("$.acciones[1].cierre.manana").value("OTRA_HORA"))
                .andExpect(jsonPath("$.acciones[1].cierre.nota").value("Se alargó la reunión"))
                .andExpect(jsonPath("$.acciones[0].exigeRespuesta").value(false))
                .andExpect(jsonPath("$.acciones[1].exigeRespuesta").value(true))
                .andExpect(jsonPath("$.semana.cumplidas").value(7))
                .andExpect(jsonPath("$.semana.planificadas").value(12));
        verify(consultar).consultar(ANA, null);
    }

    @Test
    @DisplayName("GET con date la pasa al caso de uso; una fecha que no es fecha es 400")
    void fechaDelGet() throws Exception {
        mockMvc.perform(get("/api/v1/rocks/day-closure?date=2026-10-27").header("X-Actor-Id", ANA.toString()))
                .andExpect(status().isOk());
        verify(consultar).consultar(ANA, LocalDate.of(2026, 10, 27));

        mockMvc.perform(get("/api/v1/rocks/day-closure?date=ayer").header("X-Actor-Id", ANA.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("sin «tu semana» el campo semana va en null")
    void sinSemana() throws Exception {
        when(consultar.consultar(any(), any())).thenReturn(new VistaDelCierre(
                new DiaDelCierre(HOY.minusDays(1), "SIN_CERRAR", null, null, List.of()), false, null, Set.of()));

        mockMvc.perform(get("/api/v1/rocks/day-closure?date=2026-10-27").header("X-Actor-Id", ANA.toString()))
                .andExpect(jsonPath("$.estado").value("SIN_CERRAR"))
                .andExpect(jsonPath("$.puedeCerrar").value(false))
                .andExpect(jsonPath("$.semana").value(nullValue()))
                .andExpect(jsonPath("$.cerradoEn").value(nullValue()));
    }

    @Test
    @DisplayName("PUT cierra el dia: el comando lleva quien, la fecha vista, las respuestas y la nota; devuelve la misma forma que GET")
    void contratoDelPut() throws Exception {
        cerrarCon(cuerpoValido())
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.estado").value("CERRADO"))
                .andExpect(jsonPath("$.acciones[1].cierre.motivo").value("SIN_TIEMPO"));

        ArgumentCaptor<CerrarElDia> comando = ArgumentCaptor.forClass(CerrarElDia.class);
        verify(cerrar).cerrar(comando.capture());
        assertThat(comando.getValue().actorId()).isEqualTo(ANA);
        assertThat(comando.getValue().fecha()).isEqualTo(HOY);
        assertThat(comando.getValue().nota()).isEqualTo("Descansé");
        assertThat(comando.getValue().acciones()).singleElement().satisfies(a -> {
            assertThat(a.accionId()).isEqualTo(FACTURAS);
            assertThat(a.motivo()).isEqualTo("SIN_TIEMPO");
            assertThat(a.manana()).isEqualTo("OTRA_HORA");
            assertThat(a.nota()).isEqualTo("Se alargó la reunión");
        });
    }

    @Test
    @DisplayName("PUT sin acciones es valido (un dia sin acciones se cierra igual) y sin nota tambien")
    void diaSinAcciones() throws Exception {
        cerrarCon("{\"fecha\":\"2026-10-28\",\"acciones\":[]}").andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT mal formado es 400 y no llega al caso de uso: sin fecha, sin lista, sin motivo, sin id o con mas de 9 acciones")
    void validacionDeLaEntrada() throws Exception {
        cerrarCon("{\"acciones\":[]}").andExpect(status().isBadRequest());
        cerrarCon("{\"fecha\":\"2026-10-28\"}").andExpect(status().isBadRequest());
        cerrarCon("{\"fecha\":\"2026-10-28\",\"acciones\":[{\"id\":\"%s\",\"manana\":\"DEJARLA\"}]}".formatted(FACTURAS))
                .andExpect(status().isBadRequest());
        cerrarCon("{\"fecha\":\"2026-10-28\",\"acciones\":[{\"motivo\":\"SIN_TIEMPO\",\"manana\":\"DEJARLA\"}]}")
                .andExpect(status().isBadRequest());
        String diez = ("{\"id\":\"%s\",\"motivo\":\"SIN_TIEMPO\",\"manana\":\"DEJARLA\"},".repeat(10)).formatted(
                java.util.Collections.nCopies(10, FACTURAS).toArray());
        cerrarCon("{\"fecha\":\"2026-10-28\",\"acciones\":[" + diez.substring(0, diez.length() - 1) + "]}")
                .andExpect(status().isBadRequest());
        verifyNoInteractions(cerrar);
    }

    @Test
    @DisplayName("un dia que ya cerro es 409 con el mensaje que la app reconoce; una fecha futura o una respuesta que falta, 400")
    void erroresDelCierre() throws Exception {
        doThrow(new CierreDeOtroDiaException()).when(cerrar).cerrar(any());
        cerrarCon(cuerpoValido()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Ese día ya cerró")));

        doThrow(new IllegalArgumentException("Cuenta qué pasó con «Revisar las facturas»")).when(cerrar).cerrar(any());
        cerrarCon(cuerpoValido()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Revisar las facturas")));
    }

    @Test
    @DisplayName("autorizacion negativa: cuenta suspendida o personal sin su programa es 403; sin participacion, 404")
    void autorizacionNegativa() throws Exception {
        doThrow(new NotAuthorizedException("Cuenta suspendida")).when(cerrar).cerrar(any());
        cerrarCon(cuerpoValido()).andExpect(status().isForbidden());

        doThrow(new NotAuthorizedException("Cuenta suspendida")).when(consultar).consultar(any(), any());
        mockMvc.perform(get("/api/v1/rocks/day-closure").header("X-Actor-Id", ANA.toString())).andExpect(status().isForbidden());

        doThrow(new NoSuchElementException("Participante no encontrado")).when(consultar).consultar(any(), any());
        mockMvc.perform(get("/api/v1/rocks/day-closure").header("X-Actor-Id", ANA.toString())).andExpect(status().isNotFound());
    }

    // ─── lo hecho sin red (spec 048, fase 3) ───────────────────────────────

    private ResultActions cerrarSinRedCon(String cuerpo) throws Exception {
        return mockMvc.perform(put("/api/v1/rocks/day-closure").header("X-Actor-Id", ANA.toString())
                .header("X-Id-Cliente", "5f0c1f4e-2d0a-4b7e-9d77-0c9f3f6b1a10").header("X-Hecho-En", "2026-10-29T02:40:13.250Z")
                .header("X-Origen-De-La-Hora", "RELOJ_DEL_SISTEMA").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    @Test
    @DisplayName("con X-Id-Cliente y X-Hecho-En va por el caso de uso de lo hecho sin red, con su hora real, y responde la misma forma")
    void conCabecerasVaPorLoHechoSinRed() throws Exception { // spec 048 FR-019
        cerrarSinRedCon(cuerpoValido()).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CERRADO"));

        ArgumentCaptor<HechoSinRed> hecho = ArgumentCaptor.forClass(HechoSinRed.class);
        verify(cerrarSinRed).cerrar(any(CerrarElDia.class), hecho.capture());
        assertThat(hecho.getValue().idCliente()).isEqualTo(UUID.fromString("5f0c1f4e-2d0a-4b7e-9d77-0c9f3f6b1a10"));
        assertThat(hecho.getValue().hechoEn()).isEqualTo(Instant.parse("2026-10-29T02:40:13.250Z"));
        assertThat(hecho.getValue().origenDeLaHora()).isEqualTo(OrigenDeLaHora.RELOJ_DEL_SISTEMA);
        verifyNoInteractions(cerrar);
    }

    @Test
    @DisplayName("sin las cabeceras responde exactamente lo de hoy: el caso de uso de siempre y nunca el de lo hecho sin red")
    void sinCabecerasEsComoAntes() throws Exception { // spec 048 FR-019
        cerrarCon(cuerpoValido()).andExpect(status().isOk());

        verify(cerrar).cerrar(any());
        verifyNoInteractions(cerrarSinRed);
    }

    @Test
    @DisplayName("lo que no se aplicó es un 409 con codigo y message listos para mostrar (el formato ApiErrorConCodigo)")
    void noAplicadoEsUn409ConCodigo() throws Exception { // spec 048 FR-019
        doThrow(new NoAplicadoSinRedException(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR)).when(cerrarSinRed)
                .cerrar(any(), any());

        cerrarSinRedCon(cuerpoValido()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR"))
                .andExpect(jsonPath("$.message").value("Esto cambió en tu cuenta después de que lo hiciste sin conexión. Se quedó lo más nuevo."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("autorizacion negativa con cabeceras: una cuenta suspendida es 403 también cuando es lo hecho sin red")
    void autorizacionNegativaConCabeceras() throws Exception { // spec 048 FR-019
        doThrow(new NotAuthorizedException("Cuenta suspendida", NotAuthorizedException.CUENTA_SUSPENDIDA)).when(cerrarSinRed)
                .cerrar(any(), any());

        cerrarSinRedCon(cuerpoValido()).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("CUENTA_SUSPENDIDA"));
    }

    @Test
    @DisplayName("un X-Id-Cliente que no es UUID o un X-Hecho-En que no es un instante es un 400 de siempre y no llega al caso de uso")
    void cabecerasMalHechas() throws Exception { // spec 048 FR-019
        mockMvc.perform(put("/api/v1/rocks/day-closure").header("X-Actor-Id", ANA.toString()).header("X-Id-Cliente", "no-es-uuid")
                .header("X-Hecho-En", "2026-10-29T02:40:13Z").contentType(MediaType.APPLICATION_JSON).content(cuerpoValido()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/rocks/day-closure").header("X-Actor-Id", ANA.toString())
                .header("X-Id-Cliente", "5f0c1f4e-2d0a-4b7e-9d77-0c9f3f6b1a10").header("X-Hecho-En", "ayer")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpoValido())).andExpect(status().isBadRequest());

        verifyNoInteractions(cerrar, cerrarSinRed);
    }
}
