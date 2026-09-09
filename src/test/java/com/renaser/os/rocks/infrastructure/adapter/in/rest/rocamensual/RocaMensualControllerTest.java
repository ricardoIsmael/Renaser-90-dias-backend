package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocamensual;

import com.renaser.os.rocks.application.ports.in.rocamensual.ConsultarRocasMensualesUseCase;
import com.renaser.os.rocks.application.ports.in.rocamensual.DefinirRocaMensualUseCase;
import com.renaser.os.rocks.application.ports.in.rocamensual.DefinirRocaMensualUseCase.DefinirRocaMensualCommand;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.MetaCuantitativa;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensual;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensualId;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.SecurityConfig;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de {@code /api/v1/rocks/monthly}, con el que el aprendiz define el tramo de cada
 * mes de su objetivo de 90 dias.
 *
 * <p><b>Con la cadena de seguridad ENCENDIDA y con sesion real</b>, por lo mismo que
 * {@code RocaMaestraControllerTest}: {@code /api/v1/rocks/**} exige {@code authenticated()}, y lo
 * que mas importa verificar de este endpoint es de donde salen sus tres datos de identidad — el
 * eje y el mes vienen de la RUTA y el dueno de la SESION, ninguno del cuerpo. Probarlo con los
 * filtros apagados y el header {@code X-Actor-Id} verificaria el respaldo, no el camino real.
 */
@WebMvcTest(RocaMensualController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "renaser.web.cors.origenes=http://localhost:8081")
class RocaMensualControllerTest {

    private static final Instant AHORA = Instant.parse("2026-09-06T15:00:00Z");
    private static final String RUTA_TRABAJO_MES_2 = "/api/v1/rocks/monthly/TRABAJO/2";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultarRocasMensualesUseCase consultarUseCase;
    @MockitoBean
    private DefinirRocaMensualUseCase definirUseCase;
    @MockitoBean
    private UserSummaryFinder userSummaryFinder;

    private UUID actorId;
    private MockHttpSession sesion;

    @BeforeEach
    void aprendizConSesionAbierta() {
        actorId = UUID.randomUUID();
        sesion = sesionDe(actorId);
        when(userSummaryFinder.findById(UserId.of(actorId))).thenReturn(Optional.of(
                new UserSummary(UserId.of(actorId), "Aprendiz", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        // Devuelve lo que le pidieron: asi la respuesta del endpoint depende del comando que armo
        // el controller, y no de un objeto fijo que taparia un mapeo equivocado.
        when(definirUseCase.definir(any())).thenAnswer(invocacion -> {
            DefinirRocaMensualCommand comando = invocacion.getArgument(0);
            MetaCuantitativa meta = comando.tieneMeta()
                    ? new MetaCuantitativa(comando.meta(), comando.avance(), comando.unidad(), null)
                    : null;
            return RocaMensual.definir(RocaMensualId.of(UUID.randomUUID()),
                    RocaMaestraId.of(UUID.randomUUID()), comando.numeroMes(), comando.titulo(), meta, AHORA);
        });
    }

    /** La misma sesion que deja un login exitoso: el contexto guardado en la HttpSession. */
    private static MockHttpSession sesionDe(UUID actor) {
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(actor.toString(), null, List.of()));
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto);
        return sesion;
    }

    private DefinirRocaMensualCommand comandoRecibido() {
        ArgumentCaptor<DefinirRocaMensualCommand> captor =
                ArgumentCaptor.forClass(DefinirRocaMensualCommand.class);
        verify(definirUseCase).definir(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("el eje y el mes salen de la ruta y el dueno de la sesion, nunca del cuerpo")
    void definirConMetaCompleta() throws Exception {
        mockMvc.perform(put(RUTA_TRABAJO_MES_2)
                        .session(sesion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo":"Facturar 10 mil este mes","meta":10000.00,"avance":6500.00,
                                 "unidad":"USD"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroMes").value(2))
                .andExpect(jsonPath("$.diaDeCierre").value(60))
                .andExpect(jsonPath("$.porcentaje").value(65));

        DefinirRocaMensualCommand comando = comandoRecibido();
        assertThat(comando.eje()).isEqualTo(EjeObjetivo.TRABAJO);
        assertThat(comando.numeroMes()).isEqualTo(2);
        assertThat(comando.actorId()).isEqualTo(UserId.of(actorId));
        assertThat(comando.titulo()).isEqualTo("Facturar 10 mil este mes");
        assertThat(comando.meta()).isEqualByComparingTo(new BigDecimal("10000.00"));
        assertThat(comando.avance()).isEqualByComparingTo(new BigDecimal("6500.00"));
        assertThat(comando.unidad()).isEqualTo("USD");
    }

    @Test
    @DisplayName("un tramo sin numeros es valido: hay metas que no se cuentan")
    void definirSinMetaEsValido() throws Exception {
        mockMvc.perform(put(RUTA_TRABAJO_MES_2)
                        .session(sesion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Cerrar la propuesta que vengo pateando\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porcentaje").doesNotExist());

        assertThat(comandoRecibido().tieneMeta()).isFalse();
    }

    @Test
    @DisplayName("el listado devuelve los tramos del actor de la sesion")
    void listarDevuelveLosTramosDelActor() throws Exception {
        when(consultarUseCase.misRocasMensuales(UserId.of(actorId))).thenReturn(List.of(
                RocaMensual.definir(RocaMensualId.of(UUID.randomUUID()), RocaMaestraId.of(UUID.randomUUID()),
                        1, "Entrenar tres veces por semana", null, AHORA)));

        mockMvc.perform(get("/api/v1/rocks/monthly").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].numeroMes").value(1))
                .andExpect(jsonPath("$[0].diaDeCierre").value(30))
                .andExpect(jsonPath("$[0].titulo").value("Entrenar tres veces por semana"));
    }

    @Test
    @DisplayName("un titulo en blanco es 400, no un tramo vacio guardado")
    void tituloEnBlancoEsRechazado() throws Exception {
        mockMvc.perform(put(RUTA_TRABAJO_MES_2)
                        .session(sesion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(definirUseCase);
    }

    /**
     * La columna es {@code text} y no lo limita: sin el {@code @Size} del DTO, un cliente puede
     * mandar megabytes y quedan guardados.
     */
    @Test
    @DisplayName("un titulo de mas de 500 caracteres es 400")
    void tituloDemasiadoLargoEsRechazado() throws Exception {
        mockMvc.perform(put(RUTA_TRABAJO_MES_2)
                        .session(sesion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"" + "a".repeat(RocaMensual.MAX_TITULO + 1) + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(definirUseCase);
    }

    @Test
    @DisplayName("media meta (numeros sin unidad) es 400: la parte medible va entera o no va")
    void metaIncompletaEsRechazada() throws Exception {
        mockMvc.perform(put(RUTA_TRABAJO_MES_2)
                        .session(sesion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Facturar 10 mil\",\"meta\":10000.00,\"avance\":0.00}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(definirUseCase);
    }

    /** El programa tiene tres meses. Un mes 4 en la ruta es un 400, no una fila imposible. */
    @Test
    @DisplayName("un mes fuera de 1..3 es 400")
    void mesFueraDeRangoEsRechazado() throws Exception {
        mockMvc.perform(put("/api/v1/rocks/monthly/TRABAJO/4")
                        .session(sesion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Un cuarto mes que no existe\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(definirUseCase);
    }
}
