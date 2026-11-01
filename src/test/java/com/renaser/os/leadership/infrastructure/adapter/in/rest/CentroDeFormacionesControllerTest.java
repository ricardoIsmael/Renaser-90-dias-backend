package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.formacion.ConsultarCentroDeFormacionesUseCase;
import com.renaser.os.leadership.application.ports.in.formacion.ConsultarCentroDeFormacionesUseCase.DetalleDeFormacion;
import com.renaser.os.leadership.application.ports.in.formacion.ConsultarCentroDeFormacionesUseCase.Grupos;
import com.renaser.os.leadership.application.ports.in.formacion.ConsultarCentroDeFormacionesUseCase.Personas;
import com.renaser.os.leadership.application.ports.in.formacion.ConsultarCentroDeFormacionesUseCase.PorAtender;
import com.renaser.os.leadership.application.ports.in.formacion.ConsultarCentroDeFormacionesUseCase.Resumen;
import com.renaser.os.leadership.application.ports.in.formacion.ConsultarCentroDeFormacionesUseCase.TarjetaDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El centro «Formaciones» de Administración por REST (spec 022): la forma de lo que sale y quién lo ve. */
@WebMvcTest(CentroDeFormacionesController.class)
@AutoConfigureMockMvc(addFilters = false)
class CentroDeFormacionesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultarCentroDeFormacionesUseCase centro;

    private final String actor = UserId.of(UUID.randomUUID()).toString();

    private static Resumen resumen() {
        return new Resumen(Formacion.DIPLOMADO, "Diplomado Renaser", "Tres meses o un año", true, List.of(90, 365), List.of("3 meses", "1 año"), 3);
    }

    @Test
    @DisplayName("la lista trae las tres con su resumen y sus cifras")
    void laLista() throws Exception {
        when(centro.tarjetas(any())).thenReturn(List.of(new TarjetaDeFormacion(resumen(), 12, 3, 2, 5)));

        mockMvc.perform(get("/api/v1/admin/formaciones").header("X-Actor-Id", actor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].resumen.codigo").value("DIPLOMADO"))
                .andExpect(jsonPath("$[0].resumen.etiquetasDeDuracion[1]").value("1 año"))
                .andExpect(jsonPath("$[0].resumen.version").value(3))
                .andExpect(jsonPath("$[0].aprendicesActivos").value(12))
                .andExpect(jsonPath("$[0].porAtender").value(5));
    }

    @Test
    @DisplayName("el detalle trae personas, grupos y lo por atender")
    void elDetalle() throws Exception {
        when(centro.detalle(any(), any())).thenReturn(new DetalleDeFormacion(resumen(), new Personas(12, 2, 8, 2, 3), new Grupos(2, 1, 1, 1),
                new PorAtender(1, 4, 0)));

        mockMvc.perform(get("/api/v1/admin/formaciones/diplomado").header("X-Actor-Id", actor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personas.terminaron").value(2))
                .andExpect(jsonPath("$.grupos.recepcion").value(1))
                .andExpect(jsonPath("$.porAtender.aprendicesSinGrupo").value(4));
    }

    @Test
    @DisplayName("una formación que no existe es 404 (contrato 022: el recurso del detalle no existe) y no llega al caso de uso")
    void unaFormacionQueNoExiste() throws Exception {
        mockMvc.perform(get("/api/v1/admin/formaciones/MAESTRIA").header("X-Actor-Id", actor)).andExpect(status().isNotFound());

        verifyNoInteractions(centro);
    }

    @Test
    @DisplayName("403 a quien no es Administración o Alquimista, en la lista y en el detalle")
    void soloAdministracion() throws Exception {
        when(centro.tarjetas(any())).thenThrow(new NotAuthorizedException("Solo ADMIN/ALCHEMIST ven las formaciones"));
        when(centro.detalle(any(), any())).thenThrow(new NotAuthorizedException("Solo ADMIN/ALCHEMIST ven las formaciones"));

        mockMvc.perform(get("/api/v1/admin/formaciones").header("X-Actor-Id", actor)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/formaciones/ESCUELA").header("X-Actor-Id", actor)).andExpect(status().isForbidden());
    }
}
