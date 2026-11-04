package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.out.respuesta.LeerRespuestasPorClavePort;
import com.renaser.os.onboarding.domain.model.caja.FichaDeEnvio;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * El WhatsApp de la Ficha Inicial para Administración (D-320, issue #78): lee la MISMA respuesta que la Caja Renaser
 * ({@code whatsapp}), en una sola consulta, y un valor vacío cuenta como «no lo tiene».
 */
class WhatsappDeLaFichaServiceTest {

    private final LeerRespuestasPorClavePort respuestas = mock(LeerRespuestasPorClavePort.class);
    private final WhatsappDeLaFichaService servicio = new WhatsappDeLaFichaService(respuestas);

    private static UserId persona() {
        return UserId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("lee la clave «whatsapp» (la misma de la Caja Renaser) de todas las personas en UNA consulta y devuelve el valor tal como se guardó")
    void unaConsultaParaTodasConLaClaveDeLaCaja() {
        UserId ana = persona();
        UserId luis = persona();
        List<UserId> personas = List.of(ana, luis, persona());
        when(respuestas.textosDeVarios(any(), eq("whatsapp"))).thenReturn(Map.of(ana, "+51 987654321", luis, "+34 600111222"));

        Map<UserId, String> whatsapps = servicio.de(personas);

        assertThat(FichaDeEnvio.CLAVE_CELULAR).isEqualTo("whatsapp");
        verify(respuestas, times(1)).textosDeVarios(personas, FichaDeEnvio.CLAVE_CELULAR);
        verifyNoMoreInteractions(respuestas);
        assertThat(whatsapps).containsOnly(Map.entry(ana, "+51 987654321"), Map.entry(luis, "+34 600111222"));
    }

    @Test
    @DisplayName("quien no tiene respuesta no aparece (null al pedirlo); uno vacío o con solo espacios tampoco; a los demás solo se les quitan los espacios de los bordes")
    void sinRespuestaOVacioNoCuentaYSoloSeLimpianLosBordes() {
        UserId conEspacios = persona();
        UserId vacio = persona();
        UserId soloEspacios = persona();
        UserId sinFicha = persona();
        Map<UserId, String> guardado = new HashMap<>();
        guardado.put(conEspacios, "  +51 987654321 ");
        guardado.put(vacio, "");
        guardado.put(soloEspacios, "   ");
        guardado.put(persona(), null);
        when(respuestas.textosDeVarios(any(), eq("whatsapp"))).thenReturn(guardado);

        Map<UserId, String> whatsapps = servicio.de(List.of(conEspacios, vacio, soloEspacios, sinFicha));

        assertThat(whatsapps).containsOnly(Map.entry(conEspacios, "+51 987654321"));
        assertThat(whatsapps.get(sinFicha)).isNull();
        assertThat(whatsapps.get(vacio)).isNull();
    }

    @Test
    @DisplayName("sin personas devuelve vacío")
    void sinPersonas() {
        when(respuestas.textosDeVarios(any(), eq("whatsapp"))).thenReturn(Map.of());

        assertThat(servicio.de(List.of())).isEmpty();
    }
}
