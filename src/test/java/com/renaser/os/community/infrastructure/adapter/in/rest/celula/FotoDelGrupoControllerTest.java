package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.celula.CambiarFotoDelGrupoUseCase;
import com.renaser.os.community.application.ports.in.celula.CambiarFotoDelGrupoUseCase.CambiarFotoDelGrupoCommand;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.celula.FotoDelGrupo;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de la foto propia de un grupo (D-212). Filtros apagados, como el resto de los
 * {@code @WebMvcTest}: que sin sesión se rechace, el tope de 2 MB del multipart y el 403 del interceptor
 * a un aprendiz los prueba {@code FotoDelGrupoIT} contra el Tomcat real.
 */
@WebMvcTest(FotoDelGrupoController.class)
@AutoConfigureMockMvc(addFilters = false)
class FotoDelGrupoControllerTest {

    private static final String RUTA = "/api/v1/admin/cells/{id}/photo";
    private static final Instant CAMBIADA = Instant.parse("2026-09-27T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSummaryFinder userSummaryFinder;
    @MockitoBean
    private CambiarFotoDelGrupoUseCase fotoUseCase;

    private final UUID grupo = UUID.randomUUID();

    private UUID actor(UserRole rol, UserStatus estado) {
        UUID actorId = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(actorId))).thenReturn(Optional.of(
                new UserSummary(UserId.of(actorId), "Alguien", null, rol, estado)));
        return actorId;
    }

    @Test
    @DisplayName("PUT multipart: la parte «foto» llega entera al caso de uso, con su tipo, y responde desde cuándo")
    void subirLaFoto() throws Exception {
        UUID admin = actor(UserRole.ADMIN, UserStatus.ACTIVE);
        byte[] bytes = {(byte) 0xFF, (byte) 0xD8, 1, 2};
        when(fotoUseCase.cambiar(any())).thenReturn(new FotoDelGrupo("grupos/x.jpg", CAMBIADA));

        mockMvc.perform(multipart(RUTA, grupo).file(new MockMultipartFile("foto", "foto.jpg", "image/jpeg", bytes))
                        .with(pedido -> {
                            pedido.setMethod("PUT");
                            return pedido;
                        })
                        .header("X-Actor-Id", admin.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cellId").value(grupo.toString()))
                .andExpect(jsonPath("$.photoChangedAt").value(CAMBIADA.toString()))
                .andExpect(jsonPath("$.ruta").doesNotExist());

        ArgumentCaptor<CambiarFotoDelGrupoCommand> comando = ArgumentCaptor.forClass(CambiarFotoDelGrupoCommand.class);
        verify(fotoUseCase).cambiar(comando.capture());
        assertThat(comando.getValue().celulaId()).isEqualTo(CelulaId.of(grupo));
        assertThat(comando.getValue().foto()).containsExactly(bytes);
        assertThat(comando.getValue().tipo()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("sin la parte «foto» es 400, sin llegar al caso de uso")
    void sinLaFotoEs400() throws Exception {
        UUID admin = actor(UserRole.ADMIN, UserStatus.ACTIVE);

        mockMvc.perform(multipart(RUTA, grupo).file(new MockMultipartFile("otra", "x.jpg", "image/jpeg", new byte[] {1}))
                        .with(pedido -> {
                            pedido.setMethod("PUT");
                            return pedido;
                        })
                        .header("X-Actor-Id", admin.toString()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(fotoUseCase);
    }

    @Test
    @DisplayName("lo que rechaza el caso de uso: 403 sin permiso, 404 sin grupo, 400 con una foto que no sirve")
    void rechazos() throws Exception {
        UUID mentor = actor(UserRole.MENTOR, UserStatus.ACTIVE);
        UUID otroGrupo = UUID.randomUUID();
        UUID inexistente = UUID.randomUUID();
        when(fotoUseCase.cambiar(any())).thenAnswer(invocacion -> {
            CelulaId celula = invocacion.<CambiarFotoDelGrupoCommand>getArgument(0).celulaId();
            if (celula.equals(CelulaId.of(otroGrupo))) {
                throw new NotAuthorizedException("Solo el administrador o el mentor de este grupo cambian su foto");
            }
            if (celula.equals(CelulaId.of(inexistente))) {
                throw new NoSuchElementException("Celula no encontrada");
            }
            throw new IllegalArgumentException("La foto tiene que ser JPEG o PNG");
        });

        subir(mentor, otroGrupo).andExpect(status().isForbidden());
        subir(mentor, inexistente).andExpect(status().isNotFound());
        subir(mentor, grupo).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La foto tiene que ser JPEG o PNG"));
    }

    @Test
    @DisplayName("DELETE vuelve a la foto de Renaser: 204; y sin permiso, 403")
    void volverALaDeRenaser() throws Exception {
        UUID admin = actor(UserRole.ADMIN, UserStatus.ACTIVE);
        UUID ajeno = actor(UserRole.MENTOR, UserStatus.ACTIVE);
        doThrow(new NotAuthorizedException("Solo el administrador o el mentor de este grupo cambian su foto"))
                .when(fotoUseCase).volverALaDeRenaser(UserId.of(ajeno), CelulaId.of(grupo));

        mockMvc.perform(delete(RUTA, grupo).header("X-Actor-Id", admin.toString())).andExpect(status().isNoContent());
        mockMvc.perform(delete(RUTA, grupo).header("X-Actor-Id", ajeno.toString())).andExpect(status().isForbidden());
        verify(fotoUseCase).volverALaDeRenaser(UserId.of(admin), CelulaId.of(grupo));
    }

    @Test
    @DisplayName("GET dice si hay foto propia y desde cuándo; sin ella, photoChangedAt en null")
    void laActual() throws Exception {
        UUID admin = actor(UserRole.ADMIN, UserStatus.ACTIVE);
        UUID sinFoto = UUID.randomUUID();
        when(fotoUseCase.actual(UserId.of(admin), CelulaId.of(grupo)))
                .thenReturn(Optional.of(new FotoDelGrupo("grupos/x.jpg", CAMBIADA)));
        when(fotoUseCase.actual(UserId.of(admin), CelulaId.of(sinFoto))).thenReturn(Optional.empty());

        mockMvc.perform(get(RUTA, grupo).header("X-Actor-Id", admin.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoChangedAt").value(CAMBIADA.toString()));
        mockMvc.perform(get(RUTA, sinFoto).header("X-Actor-Id", admin.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoChangedAt").doesNotExist());
    }

    @Test
    @DisplayName("autorización negativa: un APRENDIZ y una cuenta SUSPENDIDA reciben 403 del interceptor, sin llegar al caso de uso")
    void aprendizYSuspendidaSon403() throws Exception {
        UUID aprendiz = actor(UserRole.TRAINEE, UserStatus.ACTIVE);
        UUID suspendida = actor(UserRole.TRAINEE, UserStatus.SUSPENDED);

        subir(aprendiz, grupo).andExpect(status().isForbidden());
        subir(suspendida, grupo).andExpect(status().isForbidden());
        mockMvc.perform(delete(RUTA, grupo).header("X-Actor-Id", aprendiz.toString())).andExpect(status().isForbidden());
        mockMvc.perform(get(RUTA, grupo).header("X-Actor-Id", aprendiz.toString())).andExpect(status().isForbidden());
        verifyNoInteractions(fotoUseCase);
    }

    private org.springframework.test.web.servlet.ResultActions subir(UUID actorId, UUID celula) throws Exception {
        return mockMvc.perform(multipart(RUTA, celula)
                .file(new MockMultipartFile("foto", "foto.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8}))
                .with(pedido -> {
                    pedido.setMethod("PUT");
                    return pedido;
                })
                .header("X-Actor-Id", actorId.toString()));
    }
}
