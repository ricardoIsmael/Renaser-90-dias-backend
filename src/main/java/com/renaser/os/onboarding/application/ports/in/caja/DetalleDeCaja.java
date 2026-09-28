package com.renaser.os.onboarding.application.ports.in.caja;

import com.renaser.os.onboarding.domain.model.caja.DatosDelEnvio;
import com.renaser.os.onboarding.domain.model.caja.EstadoCaja;
import com.renaser.os.onboarding.domain.model.caja.FaltaParaEnviar;
import com.renaser.os.onboarding.domain.model.caja.FichaDeEnvio;
import com.renaser.os.onboarding.domain.model.caja.MotivoProblema;
import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * La caja de un aprendiz como la ve el Admin: todo lo que hace falta para armarla y enviarla sin llamar a
 * nadie, y el historial de lo que se hizo.
 *
 * @param fotoArmadaUrl  URL de lectura firmada (vence), {@code null} si no hay foto en este envío
 * @param comprobanteUrl idem, del comprobante
 * @param envioDatos     del envío actual, {@code null} si todavía no salió
 */
public record DetalleDeCaja(UserId aprendizId, String nombre, EstadoCaja estado, int envio,
                            BigDecimal cumplimientoFase1, FichaDeEnvio destino, List<ElementoMarcado> contenido,
                            String fotoArmadaUrl, String comprobanteUrl, DatosDelEnvio envioDatos,
                            List<PasoDelHistorial> historial, List<FaltaParaEnviar> faltaParaEnviar) {

    public record ElementoMarcado(String valor, String etiqueta, boolean marcado) {
    }

    /**
     * @param porNombre quién lo marcó; {@code null} si fue el sistema o se borró su cuenta
     * @param motivo    solo en {@code CON_PROBLEMA}: por qué (D-220); {@code null} en los demás
     * @param nota      solo en {@code CON_PROBLEMA}: lo que escribió el Admin, si escribió algo. Es interna: el
     *                  aprendiz nunca la ve
     */
    public record PasoDelHistorial(int envio, EstadoCaja estado, Instant en, String porNombre, MotivoProblema motivo,
                                   String nota) {
    }
}
