package com.renaser.os.shared.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Le pasa a {@link DireccionIpDelCliente} (que es estatica, porque la usan nueve controllers) la unica propiedad que
 * necesita: si se confia en la cabecera {@code CloudFront-Viewer-Address}. Apagada por defecto (E-678, D-342).
 */
@Configuration(proxyBeanMethods = false)
class DireccionIpDelClienteConfig {

    DireccionIpDelClienteConfig(
            @Value("${renaser.security.ip-del-cliente.confiar-en-cloudfront-viewer-address:false}") boolean confiar) {
        DireccionIpDelCliente.confiarEnLaCabeceraDelVisitante(confiar);
    }
}
