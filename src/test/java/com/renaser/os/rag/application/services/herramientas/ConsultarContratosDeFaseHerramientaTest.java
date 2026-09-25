package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.contratos.ConsultarContratosDeFasePort;
import com.renaser.os.rag.application.ports.out.contratos.ConsultarContratosDeFasePort.ContratosDeFase;
import com.renaser.os.rag.application.ports.out.contratos.ConsultarContratosDeFasePort.Fase;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code consultar_contratos_de_fase}: que fase toca firmar lo decide {@code phasecontracts}
 * ({@code ContratosDeFaseDelParticipanteServiceTest}). Aca se prueba el texto y, sobre todo, que
 * siempre diga que firmar es solo de la persona, en la app.
 */
class ConsultarContratosDeFaseHerramientaTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final InvocacionHerramienta INVOCACION =
            InvocacionHerramienta.sinArgumentos(ConsultarContratosDeFaseHerramienta.NOMBRE);

    private final ConsultarContratosDeFasePort puerto = mock(ConsultarContratosDeFasePort.class);
    private final ConsultarContratosDeFaseHerramienta herramienta = new ConsultarContratosDeFaseHerramienta(puerto);

    private static String texto(ResultadoHerramienta resultado) {
        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Exito.class);
        return ((ResultadoHerramienta.Exito) resultado).contenido();
    }

    @Test
    @DisplayName("la descripcion le prohibe al modelo firmar, y no pide argumentos")
    void definicion() {
        assertThat(herramienta.definicion().nombre()).isEqualTo("consultar_contratos_de_fase");
        assertThat(herramienta.definicion().parametros()).isEmpty();
        assertThat(herramienta.definicion().descripcion()).contains("NUNCA firmas").contains("en la app");
    }

    @Test
    @DisplayName("firmados y pendiente de hoy, con el aviso de que se firma solo en la app")
    void pendienteHoy() {
        when(puerto.delAprendiz(APRENDIZ)).thenReturn(new ContratosDeFase(
                List.of(new Fase(2, "Fase II · El Desarrollo")), new Fase(3, "Fase III · El Guerrero Alquimista")));

        String texto = texto(herramienta.ejecutar(APRENDIZ, INVOCACION));

        assertThat(texto).contains("Contratos de fase firmados: Fase II · El Desarrollo.")
                .contains("Pendiente de firma hoy: Fase III · El Guerrero Alquimista.")
                .contains(ConsultarContratosDeFaseHerramienta.SOLO_EN_LA_APP);
    }

    @Test
    @DisplayName("sin firmados ni pendiente lo dice, y el aviso de consentimiento va igual")
    void nada() {
        when(puerto.delAprendiz(APRENDIZ)).thenReturn(new ContratosDeFase(List.of(), null));

        String texto = texto(herramienta.ejecutar(APRENDIZ, INVOCACION));

        assertThat(texto).contains("Contratos de fase firmados: ninguno todavia.")
                .contains("Hoy no tiene ningun contrato de fase pendiente de firma.")
                .contains(ConsultarContratosDeFaseHerramienta.SOLO_EN_LA_APP);
    }

    @Test
    @DisplayName("cuenta suspendida, sin programa o error inesperado: Fallo legible, nunca excepcion")
    void fallos() {
        when(puerto.delAprendiz(APRENDIZ)).thenThrow(new NotAuthorizedException("Cuenta suspendida"));
        assertThat(herramienta.ejecutar(APRENDIZ, INVOCACION)).isInstanceOf(ResultadoHerramienta.Fallo.class);

        ConsultarContratosDeFasePort sinPrograma = mock(ConsultarContratosDeFasePort.class);
        when(sinPrograma.delAprendiz(APRENDIZ)).thenThrow(new NoSuchElementException("no existe"));
        assertThat(new ConsultarContratosDeFaseHerramienta(sinPrograma).ejecutar(APRENDIZ, INVOCACION))
                .isEqualTo(ResultadoHerramienta.fallo("No encontre un programa activo para esta cuenta."));

        ConsultarContratosDeFasePort roto = mock(ConsultarContratosDeFasePort.class);
        when(roto.delAprendiz(APRENDIZ)).thenThrow(new IllegalStateException("base caida"));
        assertThat(new ConsultarContratosDeFaseHerramienta(roto).ejecutar(APRENDIZ, INVOCACION))
                .isEqualTo(ResultadoHerramienta.fallo("No pude consultar sus contratos de fase en este momento."));
    }
}
