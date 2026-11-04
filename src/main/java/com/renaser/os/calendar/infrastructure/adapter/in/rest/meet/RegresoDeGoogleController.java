package com.renaser.os.calendar.infrastructure.adapter.in.rest.meet;

import com.renaser.os.calendar.application.ports.in.meet.CuentaDeGoogleUseCase;
import com.renaser.os.calendar.application.ports.in.meet.CuentaDeGoogleUseCase.ResultadoDeLaConexion;
import com.renaser.os.shared.web.security.PublicEndpoint;
import com.renaser.os.shared.web.security.SinInterruptor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * Adonde Google devuelve al navegador al terminar de conectar la cuenta (spec 040, FR-010). Lo abre Google, sin sesión: la identidad sale del
 * {@code state}, de un solo uso y de 15 minutos, que ata la respuesta a quien empezó la conexión. Responde una página simple que dice cómo
 * terminó; la app, al volver al frente, relee el estado de la cuenta.
 *
 * <p>Nunca se escribe en un registro ni en la página el código, el {@code state} ni nada que Google haya devuelto: solo la frase de
 * {@link ResultadoDeLaConexion#mensaje()}.
 */
@RestController
@RequestMapping("/api/v1/integraciones/google")
@SinInterruptor(motivo = "Es el regreso de Google al conectar la cuenta: lo abre el navegador sin sesión, y apagar la función no puede dejar la conexión a medias")
class RegresoDeGoogleController {

    private final CuentaDeGoogleUseCase cuentaUseCase;

    RegresoDeGoogleController(CuentaDeGoogleUseCase cuentaUseCase) {
        this.cuentaUseCase = cuentaUseCase;
    }

    @PublicEndpoint("Google redirige acá al navegador al terminar la conexión; no hay sesión. Se protege con el state de un solo uso y 15 minutos que ata la respuesta a quien la empezó.")
    @GetMapping(value = "/callback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> regreso(@RequestParam(value = "code", required = false) String code,
                                          @RequestParam(value = "state", required = false) String state,
                                          @RequestParam(value = "error", required = false) String error) {
        ResultadoDeLaConexion resultado = cuentaUseCase.completarConexion(code, state, error);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(pagina(resultado));
    }

    private static String pagina(ResultadoDeLaConexion resultado) {
        String titulo = resultado.conectada() ? "Cuenta conectada" : "No se pudo conectar";
        return """
                <!doctype html>
                <html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="robots" content="noindex"><title>%s</title>
                <style>body{font-family:system-ui,sans-serif;margin:0;padding:32px 24px;background:#faf8f4;color:#1e1b18}
                main{max-width:420px;margin:0 auto}h1{font-size:22px}p{font-size:17px;line-height:1.5}</style></head>
                <body><main><h1>%s</h1><p>%s</p></main></body></html>
                """.formatted(HtmlUtils.htmlEscape(titulo), HtmlUtils.htmlEscape(titulo), HtmlUtils.htmlEscape(resultado.mensaje()));
    }
}
