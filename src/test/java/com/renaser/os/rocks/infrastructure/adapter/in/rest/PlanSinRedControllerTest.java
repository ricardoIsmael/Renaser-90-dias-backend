package com.renaser.os.rocks.infrastructure.adapter.in.rest;

import com.renaser.os.rocks.application.ports.in.rocadiaria.AccionesSinRedUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.AccionesSinRedUseCase.AccionMarcada;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CompletarRocaDiariaUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.ConsultarRocasDeHoyUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.ConsultarRocasDeMananaUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CrearPlanDiarioUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.SolicitarUrlAdjuntoRocaUseCase;
import com.renaser.os.rocks.application.ports.in.rocamaestra.ConsultarRocasMaestrasUseCase;
import com.renaser.os.rocks.application.ports.in.rocamaestra.DefinirRocaMaestraUseCase;
import com.renaser.os.rocks.application.ports.in.rocamensual.ConsultarObjetivoDelMesUseCase;
import com.renaser.os.rocks.application.ports.in.rocamensual.ConsultarRocasMensualesUseCase;
import com.renaser.os.rocks.application.ports.in.rocamensual.DefinirRocaMensualUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CerrarSemanaUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.ConsultarRocasSemanalesUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CrearPlanSemanalUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.EditarDentroDe48hUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.ObjetivosSinRedUseCase;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.infrastructure.adapter.in.rest.rocadiaria.RocaDiariaController;
import com.renaser.os.rocks.infrastructure.adapter.in.rest.rocamaestra.RocaMaestraController;
import com.renaser.os.rocks.infrastructure.adapter.in.rest.rocamensual.RocaMensualController;
import com.renaser.os.rocks.infrastructure.adapter.in.rest.rocasemanal.RocaSemanalController;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.MotivoSinPuntos;
import com.renaser.os.shared.domain.NoAplicadoSinRedException;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.ANA;
import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.pendiente;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El Plan con las tres cabeceras de lo hecho sin red (spec 048, fase 3, FR-019): con {@code X-Id-Cliente} y {@code X-Hecho-En} cada escritura va por el caso de uso de lo hecho sin red;
 * sin ellas, por lo de siempre; marcar una acción suma {@code motivoSinPuntos}; lo que no se aplica es un 409 con {@code codigo}; una cuenta suspendida, 403.
 */
@WebMvcTest(controllers = {RocaDiariaController.class, RocaSemanalController.class, RocaMaestraController.class, RocaMensualController.class})
@AutoConfigureMockMvc(addFilters = false)
class PlanSinRedControllerTest {

    private static final String ID = "5f0c1f4e-2d0a-4b7e-9d77-0c9f3f6b1a10";
    private static final String HORA = "2026-11-10T02:40:13.250Z";

    @Autowired MockMvc mvc;
    @MockitoBean CrearPlanDiarioUseCase crearPlan;
    @MockitoBean CompletarRocaDiariaUseCase completar;
    @MockitoBean SolicitarUrlAdjuntoRocaUseCase urlAdjunto;
    @MockitoBean ConsultarRocasDeHoyUseCase hoy;
    @MockitoBean ConsultarRocasDeMananaUseCase manana;
    @MockitoBean AccionesSinRedUseCase accionesSinRed;
    @MockitoBean CrearPlanSemanalUseCase crearSemana;
    @MockitoBean EditarDentroDe48hUseCase editarSemana;
    @MockitoBean CerrarSemanaUseCase cerrarSemana;
    @MockitoBean ConsultarRocasSemanalesUseCase consultarSemanas;
    @MockitoBean ConsultarRocasMaestrasUseCase consultarMaestras;
    @MockitoBean DefinirRocaMaestraUseCase definirMaestra;
    @MockitoBean ConsultarRocasMensualesUseCase consultarMensuales;
    @MockitoBean ConsultarObjetivoDelMesUseCase planMensual;
    @MockitoBean DefinirRocaMensualUseCase definirMensual;
    @MockitoBean ObjetivosSinRedUseCase objetivosSinRed;
    @MockitoBean UserSummaryFinder users;
    private final UserId actor = ANA;

    @BeforeEach
    void actorActivo() {
        when(users.findById(actor)).thenReturn(Optional.of(new UserSummary(actor, "Fixture", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequestBuilder conCabeceras(MockHttpServletRequestBuilder pedido) {
        return pedido.header("X-Actor-Id", actor.toString()).header("X-Id-Cliente", ID).header("X-Hecho-En", HORA)
                .header("X-Origen-De-La-Hora", "TELEFONO_CORREGIDO").contentType("application/json");
    }

    @Test
    @DisplayName("POST /rocks/plan con las cabeceras agenda sin red (201 como siempre); sin ellas, lo de siempre")
    void agendar() throws Exception { // spec 048 FR-019
        when(accionesSinRed.agendar(any(), any())).thenReturn(List.of(pendiente(LocalDate.of(2026, 11, 10), 1, "Salir a correr", null)));
        String cuerpo = "{\"fecha\":\"2026-11-10\",\"rocas\":[{\"eje\":\"CUERPO\",\"posicion\":1,\"titulo\":\"Salir a correr\",\"puntajeImpacto\":5,\"esDelegable\":false}]}";

        mvc.perform(conCabeceras(post("/api/v1/rocks/plan")).content(cuerpo)).andExpect(status().isCreated()).andExpect(jsonPath("$[0].titulo").value("Salir a correr"));

        ArgumentCaptor<HechoSinRed> hecho = ArgumentCaptor.forClass(HechoSinRed.class);
        verify(accionesSinRed).agendar(any(), hecho.capture());
        assertThat(hecho.getValue().hechoEn()).isEqualTo(Instant.parse(HORA));
        verifyNoInteractions(crearPlan);

        mvc.perform(post("/api/v1/rocks/plan").header("X-Actor-Id", actor.toString()).contentType("application/json").content(cuerpo)).andExpect(status().isCreated());
        verify(crearPlan).crear(any());
    }

    @Test
    @DisplayName("POST /rocks/{id}/evidence con las cabeceras suma motivoSinPuntos a la respuesta; sin ellas, el campo va en null")
    void marcarConMotivo() throws Exception { // spec 048 FR-019
        UUID roca = UUID.randomUUID();
        when(accionesSinRed.marcar(any(), any())).thenReturn(new AccionMarcada(pendiente(LocalDate.of(2026, 11, 9), 1, "Salir a correr", null), MotivoSinPuntos.LLEGO_DESPUES_DE_48_H));
        when(completar.completar(any())).thenReturn(pendiente(LocalDate.of(2026, 11, 9), 1, "Salir a correr", null));
        String cuerpo = "{\"tipo\":\"TEXTO\",\"contenidoTexto\":\"Lo hice\"}";

        mvc.perform(conCabeceras(post("/api/v1/rocks/" + roca + "/evidence")).content(cuerpo)).andExpect(status().isOk())
                .andExpect(jsonPath("$.motivoSinPuntos").value("LLEGO_DESPUES_DE_48_H"));
        verifyNoInteractions(completar);

        mvc.perform(post("/api/v1/rocks/" + roca + "/evidence").header("X-Actor-Id", actor.toString()).contentType("application/json").content(cuerpo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.motivoSinPuntos").doesNotExist());
    }

    @Test
    @DisplayName("lo que no se aplicó es un 409 con codigo y message; una cuenta suspendida, 403 (acciones y objetivos)")
    void noAplicadoYSuspendida() throws Exception { // spec 048 FR-019
        UUID roca = UUID.randomUUID();
        doThrow(new NoAplicadoSinRedException(CodigoNoAplicado.RECURSO_YA_NO_EXISTE)).when(accionesSinRed).marcar(any(), any());
        mvc.perform(conCabeceras(post("/api/v1/rocks/" + roca + "/evidence")).content("{\"tipo\":\"TEXTO\",\"contenidoTexto\":\"Lo hice\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("RECURSO_YA_NO_EXISTE"))
                .andExpect(jsonPath("$.message").value("Lo que tocaste ya no existe en tu cuenta. Se quedó lo del servidor."));

        doThrow(new NotAuthorizedException("Cuenta suspendida", NotAuthorizedException.CUENTA_SUSPENDIDA)).when(objetivosSinRed).definirObjetivoDe90Dias(any(), any());
        mvc.perform(conCabeceras(put("/api/v1/rocks/master/CUERPO")).content("{\"objetivo\":\"Bajar de peso\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("CUENTA_SUSPENDIDA"));
    }

    @Test
    @DisplayName("el objetivo de 90 días y el del mes con las cabeceras van por lo hecho sin red")
    void objetivos() throws Exception { // spec 048 FR-019
        RocaMaestra maestra = RocaMaestra.rehydrate(RocaMaestraId.of(UUID.randomUUID()), actor, EjeObjetivo.CUERPO, "Bajar de peso", null, Instant.parse("2026-10-30T14:00:00Z"), Instant.parse("2026-10-30T14:00:00Z"));
        when(objetivosSinRed.definirObjetivoDe90Dias(any(), any())).thenReturn(maestra);

        mvc.perform(conCabeceras(put("/api/v1/rocks/master/CUERPO")).content("{\"objetivo\":\"Bajar de peso\"}")).andExpect(status().isOk());
        verify(objetivosSinRed).definirObjetivoDe90Dias(any(), any());
        verifyNoInteractions(definirMaestra);

        doThrow(new NoAplicadoSinRedException(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR)).when(objetivosSinRed).definirObjetivoDelMes(any(), any());
        mvc.perform(conCabeceras(put("/api/v1/rocks/monthly/CUERPO/1")).content("{\"titulo\":\"Correr 12 km\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR"));
        verifyNoInteractions(definirMensual);
    }

    @Test
    @DisplayName("la semana (crear, editar, revisar) con las cabeceras va por lo hecho sin red")
    void semana() throws Exception { // spec 048 FR-019
        when(objetivosSinRed.crearSemana(any(), any())).thenReturn(List.of());

        mvc.perform(conCabeceras(post("/api/v1/rocks/weekly")).content("{\"rocas\":[{\"eje\":\"CUERPO\",\"titulo\":\"Correr\",\"obstaculo\":\"Pereza\",\"contingencia\":\"Salir igual\",\"autoevaluacionInicio\":5}]}"))
                .andExpect(status().isCreated());

        verify(objetivosSinRed).crearSemana(any(), any());
        verifyNoInteractions(crearSemana);
    }
}
