package com.renaser.os.users.application.services;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.domain.WhatsappDeLaCuentaException;
import com.renaser.os.users.application.ports.in.autenticacion.PedirCodigoPorWhatsAppUseCase.NumeroDelRegistroCommand;
import com.renaser.os.users.api.CodigoPorWhatsApp;
import com.renaser.os.users.api.WhatsappDeLaFichaFinder;
import com.renaser.os.users.application.ports.in.autenticacion.PedirCodigoPorWhatsAppUseCase.CodigoDeRecuperacionCommand;
import com.renaser.os.users.application.ports.in.autenticacion.PedirCodigoPorWhatsAppUseCase.CodigoDelRegistroCommand;
import com.renaser.os.users.application.ports.out.autenticacion.CodigoResetContrasenaPort;
import com.renaser.os.users.application.ports.out.autenticacion.CodigoVerificacionEmailPort;
import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort.CredencialParaLogin;
import com.renaser.os.users.application.ports.out.whatsapp.WhatsappConfirmadoPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El codigo por WhatsApp del alta y de la recuperacion (spec 043), con dobles en memoria: nada sale a la red. */
class CodigoPorWhatsAppDeAccesoServiceTest {

    private static final String IP = "190.12.34.56";
    private static final String CORREO = "ana@renaser.dev";
    private static final String NUMERO_ESCRITO = "+51 987654321";

    private final CodigosEnMemoria registro = new CodigosEnMemoria();
    private final CodigosEnMemoria recuperacion = new CodigosEnMemoria();
    /** El WhatsApp confirmado en Yo (spec 051), por cuenta. */
    private final Map<UserId, String> confirmados = new HashMap<>();
    private final LimitesEnMemoria limites = new LimitesEnMemoria();
    private final Map<String, CredencialParaLogin> cuentas = new HashMap<>();
    private final Map<UserId, String> fichas = new HashMap<>();
    private final WhatsAppFalso whatsapp = new WhatsAppFalso();
    private CodigoPorWhatsAppDeAccesoService service;
    private ListAppender<ILoggingEvent> lineas;
    private Logger log;

    private final java.util.Set<String> numerosEnOtraCuenta = new java.util.HashSet<>();

    @BeforeEach
    void armar() {
        LoadCredencialPort credenciales = email -> Optional.ofNullable(cuentas.get(email));
        WhatsappDeLaFichaFinder deLaFicha = (Collection<UserId> personas) -> {
            Map<UserId, String> mapa = new HashMap<>();
            personas.forEach(p -> { if (fichas.containsKey(p)) mapa.put(p, fichas.get(p)); });
            return mapa;
        };
        WhatsappConfirmadoPort confirmadosFalsos = new WhatsappConfirmadoPort() {
                    @Override
                    public Optional<String> de(UserId cuenta) {
                        return Optional.ofNullable(confirmados.get(cuenta));
                    }

                    @Override
                    public boolean guardar(UserId cuenta, String numero) {
                        return false;
                    }

                    @Override
                    public boolean confirmadoPorOtra(String numero, UserId excepto) {
                        return numerosEnOtraCuenta.contains(numero);
                    }
                };
        service = new CodigoPorWhatsAppDeAccesoService(registro, recuperacion, limites, credenciales, deLaFicha, whatsapp,
                confirmadosFalsos, new NumeroUnicoDeWhatsApp(whatsapp, confirmadosFalsos, (numero, excepto) -> false,
                        (numero, excepto) -> false));
    }

    @BeforeEach
    void escucharElRegistro() {
        lineas = new ListAppender<>();
        lineas.start();
        log = (Logger) LoggerFactory.getLogger(CodigoPorWhatsAppDeAccesoService.class);
        log.addAppender(lineas);
    }

    @AfterEach
    void soltarElRegistro() {
        log.detachAppender(lineas);
    }

    private UserId cuentaConContrasena(String email, String whatsappDeLaFicha) {
        UserId id = UserId.of(UUID.randomUUID());
        cuentas.put(email, new CredencialParaLogin(id, "$2a$hash", true));
        if (whatsappDeLaFicha != null) {
            fichas.put(id, whatsappDeLaFicha);
        }
        return id;
    }

    // spec 043 FR-002
    @Test
    @DisplayName("disponible solo si el canal esta configurado (clave, emisor y plantilla del codigo)")
    void disponibleSegunLaConfiguracion() {
        assertThat(service.disponible()).isTrue();
        whatsapp.configurado = false;
        assertThat(service.disponible()).isFalse();
    }

    // spec 043 FR-005, FR-007, FR-011
    @Test
    @DisplayName("el alta manda EL MISMO codigo del correo al numero escrito y devuelve el numero enmascarado")
    void elAltaMandaElMismoCodigoDelCorreo() {
        String delCorreo = registro.generarCodigo(CORREO, Duration.ofMinutes(10));

        String enmascarado = service.paraElRegistro(new CodigoDelRegistroCommand("Ana@Renaser.dev", NUMERO_ESCRITO, IP));

        assertThat(whatsapp.enviados).containsExactly("+51987654321=" + delCorreo);
        assertThat(enmascarado).isEqualTo("+51 9•• ••• •21").doesNotContain("987654321");
        assertThat(registro.generados).as("no se genero otro: no se reinicia el contador de intentos").isEqualTo(1);
    }

    // spec 043 FR-007
    @Test
    @DisplayName("si el codigo del correo ya vencio, el alta genera uno nuevo con la misma vigencia de 10 minutos")
    void sinCodigoVigenteGeneraUnoNuevo() {
        service.paraElRegistro(new CodigoDelRegistroCommand(CORREO, NUMERO_ESCRITO, IP));

        assertThat(registro.generados).isEqualTo(1);
        assertThat(registro.ultimaVigencia).isEqualTo(VerificacionEmailService.VIGENCIA_CODIGO);
        assertThat(whatsapp.enviados).containsExactly("+51987654321=" + registro.codigos.get(CORREO));
    }

    // spec 043 FR-005
    @Test
    @DisplayName("un numero sin forma de WhatsApp es un 400 y no sale nada; sin configurar o si no salio, un 409")
    void numeroInvalidoONoConfigurado() {
        assertThatThrownBy(() -> service.paraElRegistro(new CodigoDelRegistroCommand(CORREO, "+51 98", IP)))
                .isInstanceOf(IllegalArgumentException.class);
        whatsapp.configurado = false;
        assertThatThrownBy(() -> service.paraElRegistro(new CodigoDelRegistroCommand(CORREO, NUMERO_ESCRITO, IP)))
                .isInstanceOf(IllegalStateException.class);
        whatsapp.configurado = true;
        whatsapp.sale = false;
        assertThatThrownBy(() -> service.paraElRegistro(new CodigoDelRegistroCommand("otra@renaser.dev", NUMERO_ESCRITO, IP)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("No pudimos enviar");
        assertThat(whatsapp.enviados).isEmpty();
    }

    // spec 043 FR-010
    @Test
    @DisplayName("el alta espera un minuto entre pedidos del mismo correo y corta a 3 por hora para un mismo numero")
    void limitesDelAlta() {
        service.paraElRegistro(new CodigoDelRegistroCommand(CORREO, NUMERO_ESCRITO, IP));
        assertThatThrownBy(() -> service.paraElRegistro(new CodigoDelRegistroCommand(CORREO, NUMERO_ESCRITO, IP)))
                .isInstanceOf(RateLimitExceededException.class).hasMessageContaining("minuto");

        service.paraElRegistro(new CodigoDelRegistroCommand("b@renaser.dev", NUMERO_ESCRITO, "190.1.1.2"));
        service.paraElRegistro(new CodigoDelRegistroCommand("c@renaser.dev", NUMERO_ESCRITO, "190.1.1.3"));
        assertThatThrownBy(() -> service.paraElRegistro(new CodigoDelRegistroCommand("d@renaser.dev", NUMERO_ESCRITO, "190.1.1.4")))
                .as("el cuarto envio a un mismo numero en la hora").isInstanceOf(RateLimitExceededException.class);
        assertThat(whatsapp.enviados).hasSize(3);
    }

    // spec 043 FR-010
    @Test
    @DisplayName("un mismo origen no manda mas de 10 codigos por hora, sumando alta y recuperacion")
    void limitePorOrigen() {
        for (int i = 0; i < CodigoPorWhatsAppDeAccesoService.LIMITE_POR_ORIGEN; i++) {
            service.paraElRegistro(new CodigoDelRegistroCommand("p" + i + "@renaser.dev", "+51 98765430" + i, IP));
        }
        assertThatThrownBy(() -> service.paraRecuperar(new CodigoDeRecuperacionCommand(CORREO, IP)))
                .isInstanceOf(RateLimitExceededException.class);
    }

    // spec 043 FR-008
    @Test
    @DisplayName("la recuperacion manda el codigo al WhatsApp GUARDADO de la cuenta, el mismo que acepta /verify-code")
    void laRecuperacionVaAlNumeroGuardado() {
        cuentaConContrasena(CORREO, "+51 912345678");
        String delCorreo = recuperacion.generarCodigo(CORREO, Duration.ofMinutes(10));

        service.paraRecuperar(new CodigoDeRecuperacionCommand(CORREO, IP));

        assertThat(whatsapp.enviadosSinEsperar).containsExactly("+51912345678=" + delCorreo);
        assertThat(whatsapp.enviados).as("en la recuperacion nunca se espera al proveedor").isEmpty();
    }

    // spec 043 FR-008
    // spec 051 FR-005
    @Test
    @DisplayName("la recuperacion prefiere el WhatsApp CONFIRMADO en Yo al de la ficha, y una cuenta sin ficha (el personal) lo recibe ahi")
    void recuperacionPrefiereElConfirmado() {
        UserId conFicha = cuentaConContrasena(CORREO, "+51 912345678");
        confirmados.put(conFicha, "+51955555555");

        service.paraRecuperar(new CodigoDeRecuperacionCommand(CORREO, "9.9.9.9"));

        assertThat(whatsapp.enviadosSinEsperar).singleElement().asString().startsWith("+51955555555=");
        whatsapp.enviadosSinEsperar.clear();

        UserId personal = cuentaConContrasena("personal@renaser.test", null);
        confirmados.put(personal, "+51944444444");
        service.paraRecuperar(new CodigoDeRecuperacionCommand("personal@renaser.test", "9.9.9.9"));
        assertThat(whatsapp.enviadosSinEsperar).singleElement().asString().startsWith("+51944444444=");
    }

    @Test
    @DisplayName("el comando de la recuperacion no tiene donde poner un numero: no se puede desviar el codigo a otro telefono")
    void laRecuperacionNoAceptaUnNumero() {
        assertThat(CodigoDeRecuperacionCommand.class.getRecordComponents()).extracting(c -> c.getName())
                .containsExactly("email", "requestIp");
    }

    // spec 043 FR-009
    @Test
    @DisplayName("sin cuenta, sin contraseña o sin WhatsApp guardado la recuperacion termina igual, sin lanzar y sin mandar nada")
    void laRecuperacionNoRevelaNada() {
        cuentaConContrasena("sin-whatsapp@renaser.dev", null);
        cuentas.put("solo-google@renaser.dev", new CredencialParaLogin(UserId.of(UUID.randomUUID()), null, true));

        service.paraRecuperar(new CodigoDeRecuperacionCommand("nadie@renaser.dev", IP));
        service.paraRecuperar(new CodigoDeRecuperacionCommand("sin-whatsapp@renaser.dev", "190.1.1.9"));
        service.paraRecuperar(new CodigoDeRecuperacionCommand("solo-google@renaser.dev", "190.1.1.8"));

        assertThat(whatsapp.enviadosSinEsperar).isEmpty();
        assertThat(recuperacion.generados).as("no se crea un codigo para una cuenta que no lo recibiria").isZero();
    }

    // spec 043 FR-009, FR-010
    @Test
    @DisplayName("agotado el cupo del numero guardado, la recuperacion se salta el envio en silencio (no responde distinto)")
    void cupoDelNumeroEnSilencio() {
        cuentaConContrasena(CORREO, "+51 912345678");
        for (int i = 0; i < 4; i++) {
            service.paraRecuperar(new CodigoDeRecuperacionCommand(CORREO, "190.1.2." + i));
        }
        assertThat(whatsapp.enviadosSinEsperar).hasSize(CodigoPorWhatsAppDeAccesoService.LIMITE_POR_NUMERO);
    }

    // spec 043 FR-010
    @Test
    @DisplayName("un mismo origen no pide mas de 3 recuperaciones por WhatsApp por hora para un mismo correo (429, exista o no la cuenta)")
    void cupoDeOrigenYCorreo() {
        for (int i = 0; i < CodigoPorWhatsAppDeAccesoService.LIMITE_POR_CORREO; i++) {
            service.paraRecuperar(new CodigoDeRecuperacionCommand("nadie@renaser.dev", IP));
        }
        assertThatThrownBy(() -> service.paraRecuperar(new CodigoDeRecuperacionCommand("nadie@renaser.dev", IP)))
                .isInstanceOf(RateLimitExceededException.class);
    }

    // spec 043 FR-011
    @Test
    @DisplayName("el comando del alta no imprime el numero")
    void elComandoNoImprimeElNumero() {
        assertThat(new CodigoDelRegistroCommand(CORREO, NUMERO_ESCRITO, IP).toString()).doesNotContain("987654321");
        assertThat(new CodigoPorWhatsApp.NumeroParaCodigo("+51987654321", "+51 9•• ••• •21").toString()).doesNotContain("987654321");
    }

    // spec 043 FR-009, FR-011
    @Test
    @DisplayName("cada recuperacion deja UNA linea INFO con el desenlace, en los siete casos, y nunca el correo ni el numero (#145)")
    void laRecuperacionDiceEnElRegistroPorQueNoMando() {
        UserId conNumero = cuentaConContrasena("con-numero@renaser.dev", "+51 912345678");
        UserId sinNumero = cuentaConContrasena("sin-numero@renaser.dev", null);
        UserId numeroRoto = cuentaConContrasena("roto@renaser.dev", "987");
        UserId soloGoogle = UserId.of(UUID.randomUUID());
        cuentas.put("solo-google@renaser.dev", new CredencialParaLogin(soloGoogle, null, true));

        whatsapp.configurado = false;
        pedir("con-numero@renaser.dev", "190.0.0.1");
        whatsapp.configurado = true;
        pedir("nadie@renaser.dev", "190.0.0.2");
        pedir("solo-google@renaser.dev", "190.0.0.3");
        pedir("sin-numero@renaser.dev", "190.0.0.4");
        pedir("roto@renaser.dev", "190.0.0.5");
        pedir("con-numero@renaser.dev", "190.0.0.6");
        for (int i = 0; i < 3; i++) {
            pedir("con-numero@renaser.dev", "190.0.1." + i);
        }

        assertThat(lineasDelRegistro()).containsExactly(
                "[users.codigo-whatsapp] recuperacion: canal-no-configurado (cuenta -)",
                "[users.codigo-whatsapp] recuperacion: sin-cuenta-o-sin-contrasena (cuenta -)",
                "[users.codigo-whatsapp] recuperacion: sin-cuenta-o-sin-contrasena (cuenta " + soloGoogle.value() + ")",
                "[users.codigo-whatsapp] recuperacion: sin-numero (cuenta " + sinNumero.value() + ")",
                "[users.codigo-whatsapp] recuperacion: numero-invalido (cuenta " + numeroRoto.value() + ")",
                "[users.codigo-whatsapp] recuperacion: enviado (cuenta " + conNumero.value() + ")",
                "[users.codigo-whatsapp] recuperacion: enviado (cuenta " + conNumero.value() + ")",
                "[users.codigo-whatsapp] recuperacion: enviado (cuenta " + conNumero.value() + ")",
                "[users.codigo-whatsapp] recuperacion: tope-numero (cuenta " + conNumero.value() + ")");
        assertThat(lineas.list).allMatch(e -> e.getLevel() == Level.INFO);
        String todo = String.join("\n", lineasDelRegistro());
        assertThat(todo).doesNotContain("@").doesNotContain("renaser.dev").doesNotContain("912345678").doesNotContain("987")
                .doesNotContain("+51").doesNotContain("•").doesNotContain(recuperacion.codigos.getOrDefault("con-numero@renaser.dev", "sin-codigo"));
    }

    @Test
    @DisplayName("agotado el tope global de la hora, la recuperacion lo dice en el registro y no manda")
    void elTopeGlobalSeEscribeEnElRegistro() {
        UserId id = cuentaConContrasena(CORREO, "+51 912345678");
        for (int i = 0; i < CodigoPorWhatsAppDeAccesoService.LIMITE_GLOBAL_POR_HORA; i++) {
            limites.registrarIntento("codigo-whatsapp:global", Duration.ofHours(1), CodigoPorWhatsAppDeAccesoService.LIMITE_GLOBAL_POR_HORA);
        }

        pedir(CORREO, IP);

        assertThat(lineasDelRegistro()).containsExactly("[users.codigo-whatsapp] recuperacion: tope-global (cuenta " + id.value() + ")");
        assertThat(whatsapp.enviadosSinEsperar).isEmpty();
    }

    @Test
    @DisplayName("un 429 de origen o de (origen, correo) sigue siendo 429 y no escribe la linea del desenlace: no hubo desenlace")
    void elCupoDelOrigenNoEscribeDesenlace() {
        for (int i = 0; i < CodigoPorWhatsAppDeAccesoService.LIMITE_POR_CORREO; i++) {
            pedir("nadie@renaser.dev", IP);
        }
        lineas.list.clear();

        assertThatThrownBy(() -> pedir("nadie@renaser.dev", IP)).isInstanceOf(RateLimitExceededException.class);
        assertThat(lineas.list).isEmpty();
    }

    private void pedir(String email, String ip) {
        service.paraRecuperar(new CodigoDeRecuperacionCommand(email, ip));
    }

    private List<String> lineasDelRegistro() {
        return lineas.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static final class CodigosEnMemoria implements CodigoVerificacionEmailPort, CodigoResetContrasenaPort {
        final Map<String, String> codigos = new HashMap<>();
        int generados;
        Duration ultimaVigencia;
        private int siguiente = 123456;

        @Override
        public String generarCodigo(String email, Duration vigencia) {
            generados++;
            ultimaVigencia = vigencia;
            String codigo = String.valueOf(siguiente++);
            codigos.put(email, codigo);
            return codigo;
        }

        @Override
        public boolean verificarCodigo(String email, String codigo, int maxIntentos) {
            return codigo.equals(codigos.remove(email));
        }

        @Override
        public Optional<String> vigente(String email) {
            return Optional.ofNullable(codigos.get(email));
        }
    }

    private static final class LimitesEnMemoria implements LimitarSolicitudesResetPort {
        private final Map<String, Integer> cuentas = new HashMap<>();

        @Override
        public boolean registrarIntento(String clave, Duration ventana, int maximo) {
            int ahora = cuentas.merge(clave, 1, Integer::sum);
            return ahora <= maximo;
        }
    }

    private static final class WhatsAppFalso implements CodigoPorWhatsApp {
        boolean configurado = true;
        boolean sale = true;
        final List<String> enviados = new ArrayList<>();
        final List<String> enviadosSinEsperar = new ArrayList<>();

        @Override
        public boolean configurado() {
            return configurado;
        }

        @Override
        public Optional<NumeroParaCodigo> numero(String texto) {
            String compacto = texto == null ? "" : texto.replaceAll("\\s", "");
            return compacto.matches("^\\+[1-9]\\d{7,14}$")
                    ? Optional.of(new NumeroParaCodigo(compacto, "+51 9•• ••• •" + compacto.substring(compacto.length() - 2)))
                    : Optional.empty();
        }

        @Override
        public boolean enviar(NumeroParaCodigo numero, String codigo) {
            if (sale) {
                enviados.add(numero.valor() + "=" + codigo);
            }
            return sale;
        }

        @Override
        public void enviarSinEsperar(NumeroParaCodigo numero, String codigo) {
            enviadosSinEsperar.add(numero.valor() + "=" + codigo);
        }
    }

    // spec 051 FR-014
    @Test
    @DisplayName("el paso del WhatsApp: un numero libre pasa y uno de otra cuenta es 409 NUMERO_EN_USO, escrito como se escriba")
    void comprobarElNumeroDelRegistro() {
        service.comprobarNumeroDelRegistro(new NumeroDelRegistroCommand(NUMERO_ESCRITO, IP));

        numerosEnOtraCuenta.add("+51987654321");

        assertThatThrownBy(() -> service.comprobarNumeroDelRegistro(new NumeroDelRegistroCommand("+51 987 654 321", IP)))
                .isInstanceOfSatisfying(WhatsappDeLaCuentaException.class,
                        e -> assertThat(e.motivo()).isEqualTo(WhatsappDeLaCuentaException.Motivo.NUMERO_EN_USO));
    }

    // spec 051 FR-014
    @Test
    @DisplayName("el paso del WhatsApp: lo que no tiene forma de numero es 422 NUMERO_INVALIDO")
    void comprobarUnNumeroInvalido() {
        assertThatThrownBy(() -> service.comprobarNumeroDelRegistro(new NumeroDelRegistroCommand("+51 98", IP)))
                .isInstanceOfSatisfying(WhatsappDeLaCuentaException.class,
                        e -> assertThat(e.motivo()).isEqualTo(WhatsappDeLaCuentaException.Motivo.NUMERO_INVALIDO));
    }

    // spec 051 FR-015
    @Test
    @DisplayName("la consulta tiene tope por origen: pasadas 20 por hora, 429 con texto en tuteo")
    void laConsultaTieneTopePorOrigen() {
        for (int i = 0; i < CodigoPorWhatsAppDeAccesoService.LIMITE_DE_CONSULTAS_POR_ORIGEN; i++) {
            service.comprobarNumeroDelRegistro(new NumeroDelRegistroCommand(NUMERO_ESCRITO, IP));
        }
        assertThatThrownBy(() -> service.comprobarNumeroDelRegistro(new NumeroDelRegistroCommand(NUMERO_ESCRITO, IP)))
                .isInstanceOf(RateLimitExceededException.class).hasMessage(CodigoPorWhatsAppDeAccesoService.DEMASIADAS_CONSULTAS);
        assertThat(whatsapp.enviados).as("comprobar no manda ningun codigo").isEmpty();
    }

    // spec 051 FR-016
    @Test
    @DisplayName("pedir el codigo al WhatsApp de otra cuenta tambien es 409 NUMERO_EN_USO y no sale nada")
    void elCodigoNoSaleAlNumeroDeOtraCuenta() {
        numerosEnOtraCuenta.add("+51987654321");

        assertThatThrownBy(() -> service.paraElRegistro(new CodigoDelRegistroCommand(CORREO, NUMERO_ESCRITO, IP)))
                .isInstanceOf(WhatsappDeLaCuentaException.class);
        assertThat(whatsapp.enviados).isEmpty();
    }
}
