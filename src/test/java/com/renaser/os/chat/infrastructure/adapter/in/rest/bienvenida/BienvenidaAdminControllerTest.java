package com.renaser.os.chat.infrastructure.adapter.in.rest.bienvenida;

import com.renaser.os.chat.application.ports.in.bienvenida.BienvenidaEditable;
import com.renaser.os.chat.application.ports.in.bienvenida.BienvenidaEditable.PortadaEditable;
import com.renaser.os.chat.application.ports.in.bienvenida.BienvenidaEditable.TextoEditable;
import com.renaser.os.chat.application.ports.in.bienvenida.BienvenidaEditable.UltimoCambio;
import com.renaser.os.chat.application.ports.in.bienvenida.CambiarPortadaDeBienvenidaUseCase;
import com.renaser.os.chat.application.ports.in.bienvenida.CambiarPortadaDeBienvenidaUseCase.UrlDeSubida;
import com.renaser.os.chat.application.ports.in.bienvenida.CambiarTextoDeBienvenidaUseCase;
import com.renaser.os.chat.application.ports.in.bienvenida.VerBienvenidaUseCase;
import com.renaser.os.chat.application.ports.in.bienvenida.VerTarjetaDeMuestraUseCase;
import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;
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
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de la bienvenida editable (D-210). Filtros apagados, como {@code FotoDelSoporteControllerTest}:
 * el actor llega por {@code X-Actor-Id} y el interceptor de {@code @RequiresPermission} corre (el de
 * TRAINEE es de verdad). El rechazo de MENTOR y de un ADMIN suspendido lo da el servicio: acá se prueba que
 * su {@code NotAuthorizedException} sale como 403; el servicio, en sus pruebas; la sesión real, en el IT.
 */
@WebMvcTest(BienvenidaAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class BienvenidaAdminControllerTest {

    private static final String BASE = "/api/v1/admin/bienvenida";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 7};
    private static final Instant EN = Instant.parse("2026-09-27T15:04:05Z");
    private static final BienvenidaEditable BIENVENIDA = new BienvenidaEditable(false, 1000, List.of(
            new TextoEditable(PiezaDeBienvenida.SOPORTE_CON_LA_TARJETA, "{nombre}, esta tarjeta", "{nombre}, esta tarjeta",
                    false, List.of("{nombre}"), null),
            new TextoEditable(PiezaDeBienvenida.SOPORTE_FORMAL, "Hola, {nombre}. Nuevo", "Hola, {nombre}", true,
                    List.of("{nombre}"), new UltimoCambio("Kelin Rojas", EN, false)),
            new TextoEditable(PiezaDeBienvenida.GRUPO, "¡{nombre}! {mentor}", "¡{nombre}! {mentor}", false,
                    List.of("{nombre}", "{mentor}"), new UltimoCambio(null, EN, true))),
            new PortadaEditable(false, true, null));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSummaryFinder userSummaryFinder;
    @MockitoBean
    private VerBienvenidaUseCase verUseCase;
    @MockitoBean
    private CambiarTextoDeBienvenidaUseCase textoUseCase;
    @MockitoBean
    private CambiarPortadaDeBienvenidaUseCase portadaUseCase;
    @MockitoBean
    private VerTarjetaDeMuestraUseCase muestraUseCase;

    @Test
    @DisplayName("GET: la bienvenida entera, con los tres mensajes en orden, su original, el último cambio y la portada")
    void laBienvenida() throws Exception {
        UUID actor = cuenta(UserRole.ADMIN, UserStatus.ACTIVE);
        when(verUseCase.ver(UserId.of(actor))).thenReturn(BIENVENIDA);

        mockMvc.perform(get(BASE).header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false))
                .andExpect(jsonPath("$.largoMaximo").value(1000))
                .andExpect(jsonPath("$.textos[0].clave").value("SOPORTE_CON_LA_TARJETA"))
                .andExpect(jsonPath("$.textos[0].ultimoCambio").doesNotExist())
                .andExpect(jsonPath("$.textos[1].clave").value("SOPORTE_FORMAL"))
                .andExpect(jsonPath("$.textos[1].texto").value("Hola, {nombre}. Nuevo"))
                .andExpect(jsonPath("$.textos[1].original").value("Hola, {nombre}"))
                .andExpect(jsonPath("$.textos[1].cambiado").value(true))
                .andExpect(jsonPath("$.textos[1].marcadores[0]").value("{nombre}"))
                .andExpect(jsonPath("$.textos[1].ultimoCambio.por").value("Kelin Rojas"))
                .andExpect(jsonPath("$.textos[1].ultimoCambio.en").value("2026-09-27T15:04:05Z"))
                .andExpect(jsonPath("$.textos[1].ultimoCambio.volvioAlOriginal").value(false))
                .andExpect(jsonPath("$.textos[2].marcadores[1]").value("{mentor}"))
                .andExpect(jsonPath("$.textos[2].ultimoCambio.volvioAlOriginal").value(true))
                .andExpect(jsonPath("$.portada.cambiada").value(false))
                .andExpect(jsonPath("$.portada.sePuedeCambiar").value(true));
    }

    @Test
    @DisplayName("PUT y DELETE de un texto: la clave de la ruta y el texto del cuerpo llegan al caso de uso")
    void cambiarYVolverUnTexto() throws Exception {
        UUID actor = cuenta(UserRole.ALCHEMIST, UserStatus.ACTIVE);
        when(textoUseCase.cambiar(UserId.of(actor), "SOPORTE_FORMAL", "Hola, {nombre}. Nuevo")).thenReturn(BIENVENIDA);
        when(textoUseCase.volverAlOriginal(UserId.of(actor), "GRUPO")).thenReturn(BIENVENIDA);

        mockMvc.perform(put(BASE + "/textos/SOPORTE_FORMAL").header("X-Actor-Id", actor.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Hola, {nombre}. Nuevo\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.textos[1].cambiado").value(true));
        mockMvc.perform(delete(BASE + "/textos/GRUPO").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.textos.length()").value(3));
    }

    @Test
    @DisplayName("400 con el motivo del dominio (p. ej. un texto sin {nombre}), en JSON")
    void textoQueNoSirve() throws Exception {
        UUID actor = cuenta(UserRole.ADMIN, UserStatus.ACTIVE);
        when(textoUseCase.cambiar(eq(UserId.of(actor)), eq("SOPORTE_FORMAL"), any()))
                .thenThrow(new IllegalArgumentException("Al mensaje le falta {nombre}: es donde va el nombre de la persona."));

        mockMvc.perform(put(BASE + "/textos/SOPORTE_FORMAL").header("X-Actor-Id", actor.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Hola\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Al mensaje le falta {nombre}: es donde va el nombre de la persona."));
    }

    @Test
    @DisplayName("portada: URL de subida, confirmar (409 sin almacenamiento, 404 sin imagen) y volver a la original")
    void portada() throws Exception {
        UUID actor = cuenta(UserRole.ADMIN, UserStatus.ACTIVE);
        String ruta = "bienvenida/portadas/abc";
        when(portadaUseCase.solicitarSubida(UserId.of(actor), "image/jpeg"))
                .thenReturn(new UrlDeSubida(URI.create("https://almacen.test/" + ruta), ruta));
        when(portadaUseCase.confirmar(UserId.of(actor), ruta)).thenReturn(BIENVENIDA);
        when(portadaUseCase.confirmar(UserId.of(actor), "bienvenida/portadas/sin-almacen"))
                .thenThrow(new IllegalStateException("Este servidor no tiene dónde guardar imágenes"));
        when(portadaUseCase.confirmar(UserId.of(actor), "bienvenida/portadas/perdida"))
                .thenThrow(new NoSuchElementException("No encontramos la imagen subida: vuelve a elegirla."));
        when(portadaUseCase.volverALaOriginal(UserId.of(actor))).thenReturn(BIENVENIDA);

        mockMvc.perform(post(BASE + "/portada/upload-url").header("X-Actor-Id", actor.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"contentType\":\"image/jpeg\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://almacen.test/" + ruta))
                .andExpect(jsonPath("$.ruta").value(ruta));
        mockMvc.perform(confirmar(actor, ruta)).andExpect(status().isOk()).andExpect(jsonPath("$.portada.sePuedeCambiar").value(true));
        mockMvc.perform(confirmar(actor, "bienvenida/portadas/sin-almacen")).andExpect(status().isConflict());
        mockMvc.perform(confirmar(actor, "bienvenida/portadas/perdida")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No encontramos la imagen subida: vuelve a elegirla."));
        mockMvc.perform(delete(BASE + "/portada").header("X-Actor-Id", actor.toString())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("la tarjeta de muestra: image/jpeg sin caché; con la candidata, la pasa al caso de uso")
    void tarjetaDeMuestra() throws Exception {
        UUID actor = cuenta(UserRole.ADMIN, UserStatus.ACTIVE);
        when(muestraUseCase.muestra(eq(UserId.of(actor)), eq("María"), isNull())).thenReturn(JPEG);
        when(muestraUseCase.muestra(UserId.of(actor), "Ana", "bienvenida/portadas/abc")).thenReturn(JPEG);

        mockMvc.perform(get(BASE + "/tarjeta").param("nombre", "María").header("X-Actor-Id", actor.toString())
                        .accept(MediaType.IMAGE_JPEG, MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(JPEG))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        mockMvc.perform(get(BASE + "/tarjeta").param("nombre", "Ana").param("portada", "bienvenida/portadas/abc")
                        .header("X-Actor-Id", actor.toString()))
                .andExpect(status().isOk());
        verify(muestraUseCase).muestra(UserId.of(actor), "Ana", "bienvenida/portadas/abc");
    }

    @Test
    @DisplayName("una candidata que no sirve: 400 con el motivo en JSON, aunque se haya pedido la imagen")
    void candidataQueNoSirve() throws Exception {
        UUID actor = cuenta(UserRole.ADMIN, UserStatus.ACTIVE);
        when(muestraUseCase.muestra(UserId.of(actor), "Ana", "bienvenida/portadas/oscura"))
                .thenThrow(new IllegalArgumentException("El nombre no se leería"));

        mockMvc.perform(get(BASE + "/tarjeta").param("nombre", "Ana").param("portada", "bienvenida/portadas/oscura")
                        .header("X-Actor-Id", actor.toString()).accept(MediaType.IMAGE_JPEG, MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("El nombre no se leería"));
    }

    @Test
    @DisplayName("autorización negativa: un TRAINEE recibe 403 en todo y ningún caso de uso se llama")
    void aprendizNo() throws Exception {
        UUID actor = cuenta(UserRole.TRAINEE, UserStatus.ACTIVE);

        for (RequestBuilder pedido : todos(actor)) {
            mockMvc.perform(pedido).andExpect(status().isForbidden());
        }
        verifyNoInteractions(verUseCase, textoUseCase, portadaUseCase, muestraUseCase);
    }

    @Test
    @DisplayName("autorización negativa: el 403 del servicio (MENTOR, ADMIN suspendido) sale como 403")
    void rechazoDelServicio() throws Exception {
        UUID actor = cuenta(UserRole.MENTOR, UserStatus.ACTIVE);
        NotAuthorizedException rechazo = new NotAuthorizedException("Solo Administración y Alquimista pueden cambiar la bienvenida");
        when(verUseCase.ver(UserId.of(actor))).thenThrow(rechazo);
        when(textoUseCase.cambiar(any(), any(), any())).thenThrow(rechazo);
        when(textoUseCase.volverAlOriginal(any(), any())).thenThrow(rechazo);
        when(portadaUseCase.solicitarSubida(any(), any())).thenThrow(rechazo);
        when(portadaUseCase.confirmar(any(), any())).thenThrow(rechazo);
        when(portadaUseCase.volverALaOriginal(any())).thenThrow(rechazo);
        when(muestraUseCase.muestra(any(), any(), any())).thenThrow(rechazo);

        for (RequestBuilder pedido : todos(actor)) {
            mockMvc.perform(pedido).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Solo Administración y Alquimista pueden cambiar la bienvenida"));
        }
    }

    @Test
    @DisplayName("autorización negativa: un TRAINEE SUSPENDIDO recibe 403 del interceptor")
    void suspendido() throws Exception {
        UUID actor = cuenta(UserRole.TRAINEE, UserStatus.SUSPENDED);

        mockMvc.perform(get(BASE).header("X-Actor-Id", actor.toString())).andExpect(status().isForbidden());
        verifyNoInteractions(verUseCase);
    }

    private List<RequestBuilder> todos(UUID actor) {
        String id = actor.toString();
        return List.of(
                get(BASE).header("X-Actor-Id", id),
                put(BASE + "/textos/SOPORTE_FORMAL").header("X-Actor-Id", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Hola, {nombre}\"}"),
                delete(BASE + "/textos/SOPORTE_FORMAL").header("X-Actor-Id", id),
                post(BASE + "/portada/upload-url").header("X-Actor-Id", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"contentType\":\"image/jpeg\"}"),
                confirmar(actor, "bienvenida/portadas/abc"),
                delete(BASE + "/portada").header("X-Actor-Id", id),
                get(BASE + "/tarjeta").param("nombre", "Ana").header("X-Actor-Id", id)
                        .accept(MediaType.IMAGE_JPEG, MediaType.APPLICATION_JSON));
    }

    private RequestBuilder confirmar(UUID actor, String ruta) {
        return post(BASE + "/portada/confirm").header("X-Actor-Id", actor.toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"ruta\":\"" + ruta + "\"}");
    }

    private UUID cuenta(UserRole rol, UserStatus estado) {
        UUID id = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(id))).thenReturn(Optional.of(
                new UserSummary(UserId.of(id), "Kelin Rojas", null, rol, estado)));
        return id;
    }
}
