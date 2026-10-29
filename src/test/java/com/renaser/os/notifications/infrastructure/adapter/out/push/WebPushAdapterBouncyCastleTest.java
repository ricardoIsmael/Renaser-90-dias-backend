package com.renaser.os.notifications.infrastructure.adapter.out.push;

import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.client.methods.HttpPost;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E-650 (issue #28, 2026-10-29): {@code web-push} pide sus primitivas criptográficas al proveedor {@code "BC"} y
 * nada en el backend lo registraba, así que con las llaves VAPID puestas todo envío web habría caído en
 * {@code NoSuchProviderException: no such provider: BC} (y, como {@code entregar} lo traga, en un
 * {@code FALLO_TEMPORAL} silencioso).
 *
 * <p>Estas pruebas parten de una JVM SIN BouncyCastle: cada una lo quita en {@code @BeforeEach} y devuelve el
 * estado que había en {@code @AfterEach}. Es lo que las hace reales: si otra prueba de la misma JVM ya lo
 * registró (por ejemplo la del vencimiento, que lo necesita para generar claves de navegador), una versión que
 * no lo quitara pasaría aunque el adaptador no registrara nada. Todo es local: las claves VAPID y las del
 * navegador se generan acá con el JDK, sin red y sin tocar un servicio de push.
 */
class WebPushAdapterBouncyCastleTest {

    private static final String ENDPOINT = "https://push.example.test/sub";
    private static final String SUBJECT = "mailto:soporte@renaser.test";

    private Provider bouncyCastleDeLaJvm;

    @BeforeEach
    void sinBouncyCastle() {
        bouncyCastleDeLaJvm = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
        assertThat(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME))
                .as("la prueba parte sin BC").isNull();
    }

    @AfterEach
    void devolverLaJvmComoEstaba() {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
        if (bouncyCastleDeLaJvm != null) {
            Security.addProvider(bouncyCastleDeLaJvm);
        }
    }

    @Test
    @DisplayName("control: sin BC registrado, PushService no se puede ni construir (el defecto del issue #28)")
    void sinElRegistroLaLibreriaFalla() throws Exception {
        KeyPair vapid = parDeClaves();

        assertThatThrownBy(() -> new PushService(punto(vapid), escalar(vapid), SUBJECT))
                .isInstanceOf(NoSuchProviderException.class)
                .hasMessageContaining("BC");
    }

    @Test
    @DisplayName("al cargarse el adaptador, BC queda registrado y se arma un pedido de Web Push sin red")
    void cargarElAdaptadorRegistraBouncyCastle() throws Exception {
        KeyPair vapid = parDeClaves();
        KeyPair navegador = parDeClaves();

        cargarElAdaptadorComoSiFueraLaPrimeraVez();

        assertThat(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)).isNotNull();
        PushService servicio = new PushService(punto(vapid), escalar(vapid), SUBJECT);
        Notification notificacion = Notification.builder()
                .endpoint(ENDPOINT)
                .userPublicKey(punto(navegador))
                .userAuth(Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]))
                .payload("{\"title\":\"Aviso\"}")
                .build();

        HttpPost pedido = servicio.preparePost(notificacion, Encoding.AES128GCM);

        assertThat(pedido.getURI()).hasToString(ENDPOINT);
        assertThat(pedido.getFirstHeader("Authorization").getValue()).startsWith("vapid t=");
        assertThat(pedido.getEntity()).as("el cuerpo va cifrado").isNotNull();
    }

    @Test
    @DisplayName("registrar es idempotente: dos veces dejan un solo BC y uno que ya estaba no se reemplaza")
    void registrarEsIdempotenteYNoPisa() {
        BouncyCastleProvider yaRegistrado = new BouncyCastleProvider();
        Security.addProvider(yaRegistrado);
        long antes = cuantosBc();

        WebPushAdapter.registrarBouncyCastle();
        WebPushAdapter.registrarBouncyCastle();

        assertThat(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)).isSameAs(yaRegistrado);
        assertThat(cuantosBc()).isEqualTo(antes).isEqualTo(1);
    }

    @Test
    @DisplayName("registrar BC no cambia la prioridad de los proveedores del JDK: va al final")
    void bcVaAlFinalDeLaLista() {
        Provider[] antes = Security.getProviders();

        WebPushAdapter.registrarBouncyCastle();

        Provider[] despues = Security.getProviders();
        assertThat(despues).hasSize(antes.length + 1);
        assertThat(Arrays.copyOf(despues, antes.length)).containsExactly(antes);
        assertThat(despues[despues.length - 1].getName()).isEqualTo(BouncyCastleProvider.PROVIDER_NAME);
    }

    private static long cuantosBc() {
        return Arrays.stream(Security.getProviders())
                .filter(p -> BouncyCastleProvider.PROVIDER_NAME.equals(p.getName())).count();
    }

    /**
     * Fuerza la inicialización de {@link WebPushAdapter} en un cargador de clases propio, que es lo que pasa
     * cuando Spring lo crea por primera vez en una JVM nueva. En la JVM de la prueba la clase ya pudo haberse
     * inicializado (otra prueba, otro contexto) y su {@code static} no volvería a correr. El resto de las clases
     * sale del cargador de siempre, así que {@code Security} (que es global) es el mismo.
     */
    private static void cargarElAdaptadorComoSiFueraLaPrimeraVez() throws Exception {
        String nombre = WebPushAdapter.class.getName();
        URL codigo = WebPushAdapter.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader propio = new URLClassLoader(new URL[]{codigo}, WebPushAdapter.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String clase, boolean resolver) throws ClassNotFoundException {
                if (!clase.equals(nombre)) {
                    return super.loadClass(clase, resolver);
                }
                synchronized (getClassLoadingLock(clase)) {
                    Class<?> cargada = findLoadedClass(clase);
                    return cargada != null ? cargada : findClass(clase);
                }
            }
        }) {
            Class<?> copia = Class.forName(nombre, true, propio);
            assertThat(copia).as("es otra copia de la clase, no la ya inicializada").isNotSameAs(WebPushAdapter.class);
        }
    }

    // ─── Claves de prueba, generadas con el JDK (sin BC: la prueba no puede depender de lo que prueba) ───

    private static KeyPair parDeClaves() throws Exception {
        KeyPairGenerator generador = KeyPairGenerator.getInstance("EC");
        generador.initialize(new ECGenParameterSpec("secp256r1"));
        return generador.generateKeyPair();
    }

    /** La clave pública como la manda un navegador y la espera VAPID: punto sin comprimir, base64url sin relleno. */
    private static String punto(KeyPair par) {
        ECPublicKey publica = (ECPublicKey) par.getPublic();
        byte[] punto = new byte[65];
        punto[0] = 0x04;
        System.arraycopy(a32Bytes(publica.getW().getAffineX()), 0, punto, 1, 32);
        System.arraycopy(a32Bytes(publica.getW().getAffineY()), 0, punto, 33, 32);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(punto);
    }

    /** La llave privada VAPID: el escalar de 32 bytes, base64url sin relleno. */
    private static String escalar(KeyPair par) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(a32Bytes(((ECPrivateKey) par.getPrivate()).getS()));
    }

    private static byte[] a32Bytes(BigInteger numero) {
        byte[] bytes = numero.toByteArray();
        byte[] fijo = new byte[32];
        int largo = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - largo, fijo, 32 - largo, largo);
        return fijo;
    }
}
