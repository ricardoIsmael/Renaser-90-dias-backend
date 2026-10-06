package com.renaser.os.phasecontracts.infrastructure.adapter.in.rest.animal;

import com.renaser.os.phasecontracts.application.ports.in.animal.AnimalDeFaseVista;
import com.renaser.os.phasecontracts.application.ports.in.animal.CambiarImagenDeAnimalUseCase;
import com.renaser.os.phasecontracts.application.ports.in.animal.CambiarNombreDeAnimalUseCase;
import com.renaser.os.phasecontracts.application.ports.in.animal.RestaurarImagenDeAnimalUseCase;
import com.renaser.os.phasecontracts.application.ports.in.animal.VerAnimalesDeFaseUseCase;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP de los animales de fase (D-258). Filtros apagados: el actor llega por {@code X-Actor-Id}. */
@WebMvcTest(AnimalesDeFaseController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnimalesDeFaseControllerTest {

    private static final String BASE = "/api/v1/phase-animals";
    private static final List<AnimalDeFaseVista> VISTAS = List.of(
            new AnimalDeFaseVista(1, null, null, null),
            new AnimalDeFaseVista(2, "Gorila", URI.create("https://s3/x"), "fases/animales/2/a"));

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserSummaryFinder userSummaryFinder;
    @MockitoBean
    private VerAnimalesDeFaseUseCase verUseCase;
    @MockitoBean
    private CambiarImagenDeAnimalUseCase imagenUseCase;
    @MockitoBean
    private RestaurarImagenDeAnimalUseCase restaurarUseCase;
    @MockitoBean
    private CambiarNombreDeAnimalUseCase nombreUseCase;

    private UUID cuenta(UserRole rol, UserStatus estado) {
        UUID id = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(id))).thenReturn(Optional.of(
                new UserSummary(UserId.of(id), "Ana", null, rol, estado)));
        return id;
    }

    private List<RequestBuilder> escrituras(UUID actor) {
        String h = actor.toString();
        return List.of(
                post(BASE + "/1/image/upload-url").header("X-Actor-Id", h).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/png\"}"),
                post(BASE + "/1/image/confirm").header("X-Actor-Id", h).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruta\":\"fases/animales/1/a\"}"),
                delete(BASE + "/1/image").header("X-Actor-Id", h),
                put(BASE + "/1/name").header("X-Actor-Id", h).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Mono\"}"));
    }

    @Test
    @DisplayName("GET: cualquier cuenta activa ve las fases, con la imagen propia solo donde la hay")
    void lee() throws Exception {
        UUID aprendiz = cuenta(UserRole.TRAINEE, UserStatus.ACTIVE);
        when(verUseCase.ver(UserId.of(aprendiz))).thenReturn(VISTAS);
        mockMvc.perform(get(BASE).header("X-Actor-Id", aprendiz.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].personalizada").value(false))
                .andExpect(jsonPath("$[0].imagenUrl").doesNotExist())
                .andExpect(jsonPath("$[1].nombre").value("Gorila"))
                .andExpect(jsonPath("$[1].imagenUrl").value("https://s3/x"))
                .andExpect(jsonPath("$[1].personalizada").value(true));
    }

    @Test
    @DisplayName("ADMIN escribe: el número de fase y el cuerpo llegan al caso de uso")
    void escribe() throws Exception {
        UUID admin = cuenta(UserRole.ADMIN, UserStatus.ACTIVE);
        when(nombreUseCase.cambiar(UserId.of(admin), 1, "Mono")).thenReturn(VISTAS);
        when(restaurarUseCase.restaurar(UserId.of(admin), 1)).thenReturn(VISTAS);
        mockMvc.perform(put(BASE + "/1/name").header("X-Actor-Id", admin.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Mono\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(delete(BASE + "/1/image").header("X-Actor-Id", admin.toString()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("autorización negativa: un TRAINEE recibe 403 en todo lo que escribe y no se llama ningún caso de uso")
    void aprendizNoEscribe() throws Exception {
        UUID actor = cuenta(UserRole.TRAINEE, UserStatus.ACTIVE);
        for (RequestBuilder pedido : escrituras(actor)) {
            mockMvc.perform(pedido).andExpect(status().isForbidden());
        }
        verifyNoInteractions(imagenUseCase, restaurarUseCase, nombreUseCase);
    }

    @Test
    @DisplayName("autorización negativa: MENTOR y ADMIN suspendido reciben el 403 del servicio")
    void rechazoDelServicio() throws Exception {
        UUID actor = cuenta(UserRole.MENTOR, UserStatus.ACTIVE);
        doThrow(new NotAuthorizedException("Solo Administración y Alquimista")).when(nombreUseCase).cambiar(any(), anyInt(), any());
        doThrow(new NotAuthorizedException("x")).when(restaurarUseCase).restaurar(any(), anyInt());
        doThrow(new NotAuthorizedException("x")).when(imagenUseCase).solicitarSubida(any(), anyInt(), any());
        doThrow(new NotAuthorizedException("x")).when(imagenUseCase).confirmar(any(), anyInt(), any());
        for (RequestBuilder pedido : escrituras(actor)) {
            mockMvc.perform(pedido).andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("autorización negativa: un TRAINEE SUSPENDIDO recibe 403 aunque el token sea válido, también al leer")
    void suspendido() throws Exception {
        UUID actor = cuenta(UserRole.TRAINEE, UserStatus.SUSPENDED);
        mockMvc.perform(get(BASE).header("X-Actor-Id", actor.toString())).andExpect(status().isForbidden());
        for (RequestBuilder pedido : escrituras(actor)) {
            mockMvc.perform(pedido).andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("400 con el motivo del dominio, en JSON")
    void motivo() throws Exception {
        UUID admin = cuenta(UserRole.ADMIN, UserStatus.ACTIVE);
        doThrow(new IllegalArgumentException("La imagen es muy chica")).when(imagenUseCase).confirmar(any(), anyInt(), any());
        mockMvc.perform(post(BASE + "/1/image/confirm").header("X-Actor-Id", admin.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ruta\":\"fases/animales/1/a\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("La imagen es muy chica"));
    }
}
