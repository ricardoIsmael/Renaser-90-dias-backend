package com.renaser.os.chat.infrastructure.adapter.in.rest.ranking;

import com.renaser.os.chat.application.ports.in.ranking.PublicarPodioDeLaSemanaUseCase;
import com.renaser.os.chat.application.ports.in.ranking.PublicarPodioDeLaSemanaUseCase.Estado;
import com.renaser.os.chat.application.ports.in.ranking.PublicarPodioDeLaSemanaUseCase.ResultadoDelPodio;
import com.renaser.os.chat.application.ports.in.ranking.VerPodioDeLaSemanaUseCase;
import com.renaser.os.chat.application.ports.in.ranking.VerPodioDeLaSemanaUseCase.VistaPreviaDelPodio;
import com.renaser.os.chat.domain.model.ranking.PodioDeLaSemana.Puesto;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP del podio semanal (D-262). Filtros apagados, como {@code BienvenidaAdminControllerTest}: el actor
 * llega por {@code X-Actor-Id} y el interceptor de {@code @RequiresPermission} corre (el 403 de TRAINEE y el de
 * cualquier cuenta suspendida son suyos). El de MENTOR lo da el servicio ({@code PodioDeLaSemanaServiceTest}); acá,
 * que su {@code NotAuthorizedException} sale como 403.
 */
@WebMvcTest(PodioDeLaSemanaAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class PodioDeLaSemanaAdminControllerTest {

    private static final String BASE = "/api/v1/admin/ranking-semanal";
    private static final LocalDate LUNES = LocalDate.of(2026, 9, 28);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 10, 4);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSummaryFinder userSummaryFinder;
    @MockitoBean
    private VerPodioDeLaSemanaUseCase verUseCase;
    @MockitoBean
    private PublicarPodioDeLaSemanaUseCase publicarUseCase;

    @Test
    @DisplayName("vista previa: la semana, los puestos, el texto y la imagen como data URL, sin publicar")
    void vistaPrevia() throws Exception {
        UUID actor = cuenta(UserRole.ADMIN, UserStatus.ACTIVE);
        when(verUseCase.vistaPrevia(UserId.of(actor))).thenReturn(new VistaPreviaDelPodio(LUNES, DOMINGO,
                List.of(new Puesto(1, "Liz M.", new BigDecimal("96.4"))), "🏆 ¡Cerramos la semana!",
                new byte[]{1, 2, 3}, "image/png", "A", false));

        mockMvc.perform(get(BASE + "/vista-previa").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2026-09-28"))
                .andExpect(jsonPath("$.weekEnd").value("2026-10-04"))
                .andExpect(jsonPath("$.template").value("A"))
                .andExpect(jsonPath("$.alreadyPublished").value(false))
                .andExpect(jsonPath("$.entries[0].place").value(1))
                .andExpect(jsonPath("$.entries[0].name").value("Liz M."))
                .andExpect(jsonPath("$.entries[0].score").value(96.4))
                .andExpect(jsonPath("$.text").value("🏆 ¡Cerramos la semana!"))
                .andExpect(jsonPath("$.image").value("data:image/png;base64,AQID"));
        verifyNoInteractions(publicarUseCase);
    }

    @Test
    @DisplayName("publicar: devuelve la semana, el estado y cuántos mensajes salieron")
    void publicar() throws Exception {
        UUID actor = cuenta(UserRole.ALCHEMIST, UserStatus.ACTIVE);
        when(publicarUseCase.publicarAhora(UserId.of(actor)))
                .thenReturn(new ResultadoDelPodio(LUNES, DOMINGO, Estado.PUBLICADO, 2));

        mockMvc.perform(post(BASE + "/publicar").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2026-09-28"))
                .andExpect(jsonPath("$.status").value("PUBLICADO"))
                .andExpect(jsonPath("$.messages").value(2));
    }

    @Test
    @DisplayName("autorización negativa: un TRAINEE recibe 403 en los dos y ningún caso de uso se llama")
    void aprendizNo() throws Exception {
        UUID actor = cuenta(UserRole.TRAINEE, UserStatus.ACTIVE);

        for (RequestBuilder pedido : los2(actor)) {
            mockMvc.perform(pedido).andExpect(status().isForbidden());
        }
        verifyNoInteractions(verUseCase, publicarUseCase);
    }

    @Test
    @DisplayName("autorización negativa: un TRAINEE SUSPENDIDO recibe 403 del interceptor")
    void aprendizSuspendido() throws Exception {
        UUID actor = cuenta(UserRole.TRAINEE, UserStatus.SUSPENDED);

        for (RequestBuilder pedido : los2(actor)) {
            mockMvc.perform(pedido).andExpect(status().isForbidden());
        }
        verifyNoInteractions(verUseCase, publicarUseCase);
    }

    @Test
    @DisplayName("autorización negativa: un ADMIN SUSPENDIDO recibe 403 del interceptor aunque su token sea válido")
    void adminSuspendido() throws Exception {
        UUID actor = cuenta(UserRole.ADMIN, UserStatus.SUSPENDED);

        for (RequestBuilder pedido : los2(actor)) {
            mockMvc.perform(pedido).andExpect(status().isForbidden());
        }
        verifyNoInteractions(verUseCase, publicarUseCase);
    }

    @Test
    @DisplayName("autorización negativa: el 403 del servicio (un MENTOR, que el interceptor todavía deja pasar) sale como 403")
    void rechazoDelServicio() throws Exception {
        UUID actor = cuenta(UserRole.MENTOR, UserStatus.ACTIVE);
        NotAuthorizedException rechazo = new NotAuthorizedException("Solo Administración y Alquimista pueden publicar el podio de la semana");
        when(verUseCase.vistaPrevia(any())).thenThrow(rechazo);
        when(publicarUseCase.publicarAhora(any())).thenThrow(rechazo);

        for (RequestBuilder pedido : los2(actor)) {
            mockMvc.perform(pedido).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Solo Administración y Alquimista pueden publicar el podio de la semana"));
        }
    }

    private static List<RequestBuilder> los2(UUID actor) {
        return List.of(get(BASE + "/vista-previa").header("X-Actor-Id", actor.toString()),
                post(BASE + "/publicar").header("X-Actor-Id", actor.toString()));
    }

    private UUID cuenta(UserRole rol, UserStatus estado) {
        UUID id = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(id))).thenReturn(Optional.of(
                new UserSummary(UserId.of(id), "Kelin Rojas", null, rol, estado)));
        return id;
    }
}
