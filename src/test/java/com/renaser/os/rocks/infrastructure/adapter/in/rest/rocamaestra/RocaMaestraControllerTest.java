package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocamaestra;

import com.renaser.os.rocks.application.ports.in.rocamaestra.ConsultarRocasMaestrasUseCase;
import com.renaser.os.rocks.application.ports.in.rocamaestra.DefinirRocaMaestraUseCase;
import com.renaser.os.rocks.application.ports.in.rocamaestra.DefinirRocaMaestraUseCase.DefinirRocaMaestraCommand;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.MetaCuantitativa;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de {@code PUT /api/v1/rocks/master/{eje}}, el endpoint con el que el aprendiz
 * define o corrige su objetivo de 90 dias.
 *
 * <p><b>Por que con la cadena de seguridad ENCENDIDA y con sesion real.</b> {@code /api/v1/rocks/**}
 * exige {@code authenticated()} desde 2026-09-06, y lo que mas importa verificar de este endpoint
 * es justamente de donde salen sus dos datos de identidad: el eje viene de la RUTA y el dueno de la
 * SESION — ninguno de los dos se acepta desde el cuerpo. Probarlo con los filtros apagados y el
 * header {@code X-Actor-Id} verificaria el respaldo, no el camino real. La sesion se arma como en
 * {@code ActorHandshakeInterceptorTest}: un {@code SecurityContext} guardado en el atributo que lee
 * {@code HttpSessionSecurityContextRepository}, que es exactamente lo que deja el login.
 *
 * <p>El resto de los casos son los tres cuerpos que un cliente puede mandar mal, y el que parece
 * incompleto pero es valido: un objetivo sin numeros. La meta medible va entera o no va (V35), y
 * media meta tiene que ser un 400 que lo explique, no un 500 al llegar a la base.
 */
@WebMvcTest(RocaMaestraController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "renaser.web.cors.origenes=http://localhost:8081")
class RocaMaestraControllerTest {

    private static final Instant AHORA = Instant.parse("2026-09-06T15:00:00Z");
    private static final String RUTA_TRABAJO = "/api/v1/rocks/master/TRABAJO";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultarRocasMaestrasUseCase consultarUseCase;
    @MockitoBean
    private DefinirRocaMaestraUseCase definirUseCase;
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
            DefinirRocaMaestraCommand comando = invocacion.getArgument(0);
            MetaCuantitativa meta = comando.tieneMeta()
                    ? new MetaCuantitativa(comando.meta(), comando.avance(), comando.unidad())
                    : null;
            return RocaMaestra.definir(RocaMaestraId.of(UUID.randomUUID()), comando.actorId(), comando.eje(),
                    comando.objetivo(), meta, AHORA);
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

    private DefinirRocaMaestraCommand comandoRecibido() {
        ArgumentCaptor<DefinirRocaMaestraCommand> captor =
                ArgumentCaptor.forClass(DefinirRocaMaestraCommand.class);
        verify(definirUseCase).definir(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("el eje sale de la ruta y el dueno de la sesion, nunca del cuerpo")
    void definirConMetaCompleta() throws Exception {
        mockMvc.perform(put(RUTA_TRABAJO)
                        .session(sesion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"objetivo":"Facturar 50 mil","meta":50000.00,"avance":12500.00,
                                 "unidad":"USD"}
                                """))
                .andExpect(status().isOk());

        DefinirRocaMaestraCommand comando = comandoRecibido();
        assertThat(comando.eje()).isEqualTo(EjeObjetivo.TRABAJO);
        assertThat(comando.actorId()).isEqualTo(UserId.of(actorId));
        assertThat(comando.objetivo()).isEqualTo("Facturar 50 mil");
        assertThat(comando.meta()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(comando.avance()).isEqualByComparingTo(new BigDecimal("12500.00"));
        assertThat(comando.unidad()).isEqualTo("USD");
    }

    @Test
    @DisplayName("un objetivo sin numeros es valido: hay metas que no se cuentan")
    void definirSinMetaEsValido() throws Exception {
        mockMvc.perform(put(RUTA_TRABAJO)
                        .session(sesion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objetivo\":\"Recuperar la relacion con mi hermano\"}"))
                .andExpect(status().isOk());

        DefinirRocaMaestraCommand comando = comandoRecibido();
        assertThat(comando.tieneMeta()).isFalse();
        assertThat(comando.objetivo()).isEqualTo("Recuperar la relacion con mi hermano");
    }

    @Test
    @DisplayName("un objetivo en blanco es 400, no una roca vacia guardada")
    void objetivoEnBlancoEsRechazado() throws Exception {
        mockMvc.perform(put(RUTA_TRABAJO)
                        .session(sesion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objetivo\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(definirUseCase);
    }

    /**
     * La columna es {@code text} y no lo limita: sin el {@code @Size} del DTO, un cliente puede
     * mandar megabytes y quedan guardados.
     */
    @Test
    @DisplayName("un objetivo de mas de 500 caracteres es 400")
    void objetivoDemasiadoLargoEsRechazado() throws Exception {
        mockMvc.perform(put(RUTA_TRABAJO)
                        .session(sesion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objetivo\":\"" + "a".repeat(RocaMaestra.MAX_OBJETIVO + 1) + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(definirUseCase);
    }

    @Test
    @DisplayName("media meta (numeros sin unidad) es 400: la parte medible va entera o no va")
    void metaIncompletaEsRechazada() throws Exception {
        mockMvc.perform(put(RUTA_TRABAJO)
                        .session(sesion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objetivo\":\"Facturar 50 mil\",\"meta\":50000.00,\"avance\":0.00}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(definirUseCase);
    }
}
