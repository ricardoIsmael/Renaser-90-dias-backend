package com.renaser.os.users.infrastructure.adapter.out.email;

import com.renaser.os.shared.domain.EnvioEmailFallidoException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

/**
 * Unit del adaptador SMTP. No levanta Spring ni un servidor de correo: se mockea
 * {@link JavaMailSender} y se inspecciona el {@link MimeMessage} que se le entrega, que es
 * justamente el contrato de este adaptador (armar bien el mensaje y delegar el transporte).
 */
@ExtendWith(MockitoExtension.class)
class SmtpEnviarEmailAdapterTest {

    private static final String REMITENTE = "Renaser <no-reply@ejemplo.test>";
    private static final String URL_RESET = "https://app.ejemplo.test/reset-password";
    private static final String URL_ACTIVACION = "https://app.ejemplo.test/activate-account";
    private static final String DESTINATARIO = "aprendiz@ejemplo.test";

    @Mock
    private JavaMailSender mailSender;

    private SmtpEnviarEmailAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SmtpEnviarEmailAdapter(mailSender, REMITENTE, URL_RESET, URL_ACTIVACION);
    }

    /** {@code JavaMailSender.createMimeMessage()} es lo unico que el mock no puede inventar. */
    private MimeMessage prepararMimeMessage() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        given(mailSender.createMimeMessage()).willReturn(mimeMessage);
        return mimeMessage;
    }

    private MimeMessage capturarEnviado() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("el codigo de verificacion viaja en el cuerpo, con asunto y destinatario correctos")
    void enviaElCodigoDeVerificacion() throws Exception {
        prepararMimeMessage();

        adapter.enviarCodigoVerificacionEmail(DESTINATARIO, "483920");

        MimeMessage enviado = capturarEnviado();
        assertThat(enviado.getSubject()).isEqualTo("Tu codigo de verificacion de Renaser");
        assertThat(enviado.getAllRecipients()[0]).hasToString(DESTINATARIO);
        assertThat(contenido(enviado)).contains("483920");
    }

    @Test
    @DisplayName("el correo de activacion lleva el link con el token sobre la URL configurada")
    void enviaLaActivacionConSuLink() throws Exception {
        prepararMimeMessage();

        adapter.enviarActivacionCuenta(DESTINATARIO, "tok-activacion");

        assertThat(contenido(capturarEnviado())).contains(URL_ACTIVACION + "?token=tok-activacion");
    }

    @Test
    @DisplayName("el correo de reseteo lleva el link con el token sobre SU propia URL, no la de activacion")
    void enviaElResetConSuLink() throws Exception {
        prepararMimeMessage();

        adapter.enviarResetContrasena(DESTINATARIO, "tok-reset");

        String cuerpo = contenido(capturarEnviado());
        assertThat(cuerpo).contains(URL_RESET + "?token=tok-reset");
        assertThat(cuerpo).doesNotContain(URL_ACTIVACION);
    }

    @Test
    @DisplayName("la invitacion de staff lleva la contrasena temporal")
    void enviaLaInvitacionDeStaff() throws Exception {
        prepararMimeMessage();

        adapter.enviarInvitacionStaff(DESTINATARIO, "Temp-1234");

        assertThat(contenido(capturarEnviado())).contains("Temp-1234");
    }

    @Test
    @DisplayName("el cuerpo se manda como HTML en UTF-8, para que los acentos no lleguen rotos")
    void mandaHtmlEnUtf8() throws Exception {
        prepararMimeMessage();

        adapter.enviarCodigoVerificacionEmail(DESTINATARIO, "483920");

        MimeMessage enviado = capturarEnviado();
        // JavaMail solo vuelca las cabeceras (Content-Type entre ellas) al llamar saveChanges();
        // en un envio real lo hace el propio transporte, que aca esta mockeado.
        enviado.saveChanges();
        assertThat(enviado.getContentType()).contains("text/html").contains("UTF-8");
    }

    @Test
    @DisplayName("si el proveedor falla se traduce a EnvioEmailFallidoException, no se traga el error")
    void traduceElFalloDelProveedor() {
        prepararMimeMessage();
        willThrow(new MailSendException("smtp caido")).given(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> adapter.enviarCodigoVerificacionEmail(DESTINATARIO, "483920"))
                .isInstanceOf(EnvioEmailFallidoException.class)
                // El mensaje que llega al cliente no puede filtrar nada del proveedor.
                .hasMessageNotContainingAny("smtp", "caido");
    }

    private static String contenido(MimeMessage mensaje) throws Exception {
        return mensaje.getContent().toString();
    }
}
