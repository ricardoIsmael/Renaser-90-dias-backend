package com.renaser.os.users.infrastructure.adapter.out.email;

import com.renaser.os.shared.domain.EnvioEmailFallidoException;
import com.renaser.os.users.application.ports.out.autenticacion.EnviarEmailPort;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Envio real por SMTP, sobre el {@code JavaMailSender} que Spring Boot autoconfigura a partir de
 * {@code spring.mail.*} (https://docs.spring.io/spring-boot/reference/io/email.html, Boot 4.1).
 * Implementacion real de {@link EnviarEmailPort}; el placeholder es
 * {@link NoOpEnviarEmailAdapter}.
 *
 * <p><b>Como se elige uno u otro:</b> la propiedad {@code renaser.email.proveedor}
 * ({@code smtp} | {@code noop}, por defecto {@code noop}). Se prefirio una propiedad explicita
 * antes que {@code @ConditionalOnBean(JavaMailSender.class)} porque {@code @ConditionalOnBean}
 * depende del orden de registro de beans y Spring solo lo garantiza dentro de autoconfiguracion,
 * no en clases escaneadas como esta. Con dos condiciones simetricas sobre la MISMA propiedad
 * siempre hay exactamente un {@code EnviarEmailPort}, sin ambiguedad ni orden.
 *
 * <p><b>Reutilizacion:</b> los cinco metodos del puerto se reducen a elegir una plantilla
 * ({@link PlantillasEmail}) y delegar en un unico {@link #enviar}, que concentra transporte,
 * UTF-8 y manejo de errores. No hay logica de armado de correo repetida.
 *
 * <p><b>Privacidad (CLAUDE.MD §5.4.9):</b> nunca se loguea el destinatario (PII) ni el
 * token/codigo/contrasena (credenciales) — ni siquiera al fallar. Del fallo se registra la clase
 * de la causa, que es lo unico que sirve para diagnosticar.
 */
@Component
@ConditionalOnProperty(name = "renaser.email.proveedor", havingValue = "smtp")
public class SmtpEnviarEmailAdapter implements EnviarEmailPort {

    private static final Logger log = LoggerFactory.getLogger(SmtpEnviarEmailAdapter.class);

    private final JavaMailSender mailSender;
    private final String remitente;
    private final String resetPasswordUrlBase;
    private final String activateAccountUrlBase;

    public SmtpEnviarEmailAdapter(JavaMailSender mailSender,
                                   @Value("${renaser.email.remitente}") String remitente,
                                   @Value("${renaser.web.reset-password-url}") String resetPasswordUrlBase,
                                   @Value("${renaser.web.activate-account-url}") String activateAccountUrlBase) {
        this.mailSender = mailSender;
        this.remitente = remitente;
        this.resetPasswordUrlBase = resetPasswordUrlBase;
        this.activateAccountUrlBase = activateAccountUrlBase;
    }

    @Override
    public void enviarResetContrasena(String destinatarioEmail, String token) {
        enviar(destinatarioEmail,
                PlantillasEmail.resetContrasena(PlantillasEmail.linkConToken(resetPasswordUrlBase, token)));
    }

    @Override
    public void enviarActivacionCuenta(String destinatarioEmail, String token) {
        enviar(destinatarioEmail,
                PlantillasEmail.activacionCuenta(PlantillasEmail.linkConToken(activateAccountUrlBase, token)));
    }

    @Override
    public void enviarCodigoVerificacionEmail(String destinatarioEmail, String codigo) {
        enviar(destinatarioEmail, PlantillasEmail.codigoVerificacion(codigo));
    }

    @Override
    public void enviarCodigoResetContrasena(String destinatarioEmail, String codigo) {
        enviar(destinatarioEmail, PlantillasEmail.codigoResetContrasena(codigo));
    }

    @Override
    public void enviarInvitacionStaff(String destinatarioEmail, String temporaryPassword) {
        enviar(destinatarioEmail, PlantillasEmail.invitacionStaff(temporaryPassword));
    }

    /**
     * Unico punto de envio. {@code MimeMessageHelper} con UTF-8 explicito: sin el, los acentos
     * del castellano llegan rotos segun el servidor SMTP.
     */
    private void enviar(String destinatarioEmail, MensajeEmail mensaje) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(remitente);
            helper.setTo(destinatarioEmail);
            helper.setSubject(mensaje.asunto());
            helper.setText(mensaje.cuerpoHtml(), true);
            mailSender.send(mimeMessage);
        } catch (Exception e) {
            // Sin destinatario ni contenido en el log: solo que fallo y de que tipo fue.
            log.error("[users.SmtpEnviarEmailAdapter] fallo el envio de un correo transaccional "
                    + "(asunto '{}', causa {})", mensaje.asunto(), e.getClass().getSimpleName());
            throw new EnvioEmailFallidoException(e);
        }
    }
}
