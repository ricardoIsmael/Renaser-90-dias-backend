package com.renaser.os.notifications.application.services;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.renaser.os.notifications.domain.model.whatsapp.CausaDeFalla;
import com.renaser.os.notifications.domain.model.whatsapp.ResultadoDelEnvio;
import com.renaser.os.users.api.CodigoPorWhatsApp.NumeroParaCodigo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/** El codigo por WhatsApp del lado de {@code notifications} (spec 043): normaliza el numero, lo enmascara y nunca deja el numero ni el codigo en el registro. */
class CodigoPorWhatsAppServiceTest {

    private final EnvioDeWhatsAppFalso envio = new EnvioDeWhatsAppFalso();
    private final CodigoPorWhatsAppService service = new CodigoPorWhatsAppService(envio);
    private ListAppender<ILoggingEvent> registro;
    private Logger log;

    @BeforeEach
    void escuchar() {
        registro = new ListAppender<>();
        registro.start();
        log = (Logger) LoggerFactory.getLogger(CodigoPorWhatsAppService.class);
        log.addAppender(registro);
    }

    @AfterEach
    void soltar() {
        log.detachAppender(registro);
    }

    // spec 043 FR-011, SC-003
    @Test
    @DisplayName("el numero de la ficha se normaliza y se enmascara (+51 9•• ••• •21); lo que no tiene forma de numero no sirve")
    void normalizaYEnmascara() {
        NumeroParaCodigo numero = service.numero("+51 987 654 321").orElseThrow();
        assertThat(numero.valor()).isEqualTo("+51987654321");
        assertThat(numero.enmascarado()).isEqualTo("+51 9•• ••• •21");
        assertThat(service.numero("987654")).isEmpty();
        assertThat(service.numero(null)).isEmpty();
    }

    // spec 043 FR-004, FR-011, SC-003
    @Test
    @DisplayName("enviar pasa el codigo al proveedor y en el registro solo queda el numero enmascarado, nunca el completo ni el codigo")
    void enviarNoDejaNadaSensibleEnElRegistro() {
        NumeroParaCodigo numero = service.numero("+51 987654321").orElseThrow();

        assertThat(service.enviar(numero, "482913")).isTrue();

        assertThat(envio.codigos).hasSize(1);
        assertThat(envio.codigos.get(0)).containsExactly("+51987654321", "482913");
        String todo = String.join("\n", registro.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
        assertThat(todo).contains("+51 9•• ••• •21").doesNotContain("987654321").doesNotContain("482913");
    }

    // spec 043 FR-005
    @Test
    @DisplayName("un rechazo del proveedor es false; un corte (no se supo) cuenta como enviado y no se reintenta")
    void rechazoYNoSeSupo() {
        NumeroParaCodigo numero = service.numero("+51987654321").orElseThrow();
        envio.respuesta = () -> ResultadoDelEnvio.fallido(CausaDeFalla.OTRA, "plantilla no aprobada");
        assertThat(service.enviar(numero, "111111")).isFalse();
        envio.respuesta = () -> ResultadoDelEnvio.noSeSupo("YCloud no contestó a tiempo");
        assertThat(service.enviar(numero, "111111")).isTrue();
        assertThat(envio.codigos).hasSize(2);
    }

    // spec 043 FR-002
    @Test
    @DisplayName("configurado depende de la configuracion para codigos del proveedor")
    void configurado() {
        assertThat(service.configurado()).isTrue();
        envio.configurado = false;
        assertThat(service.configurado()).isFalse();
    }
}
