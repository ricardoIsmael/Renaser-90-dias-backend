package com.renaser.os.notifications.infrastructure.adapter.out.push;

import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Que el pedido de Web Push que arma {@code web-push} sea correcto de verdad, no solo que se arme: la firma VAPID
 * es válida y el cuerpo lo puede abrir el navegador.
 *
 * <p><b>Por qué existe (2026-11-02).</b> Dos librerías de este camino se suben solas con Dependabot:
 * {@code jose4j}, que firma el JWT de VAPID, y {@code bcprov-jdk18on} (BouncyCastle), que cifra el cuerpo. Las
 * pruebas de al lado solo miran que {@code Authorization} empiece por {@code "vapid t="} y que haya un cuerpo; una
 * versión que firmara con la clave equivocada o cifrara algo ilegible pasaría igual y el primer aviso real caería en
 * el servicio de push como un 400/401. Acá la firma se comprueba con el JDK y el cuerpo se descifra con una
 * implementación de referencia de RFC 8291 escrita con {@code javax.crypto}, sin BouncyCastle.
 *
 * <p>No hay red ni servicio de push: se mira lo que {@link PushService#preparePost} dejaría en el {@code POST}.
 * Las claves se generan acá con el JDK.
 */
class WebPushAdapterFirmaYCifradoTest {

    private static final String ENDPOINT = "https://push.example.test/sub";
    private static final String SUBJECT = "mailto:soporte@renaser.test";
    private static final String PAYLOAD = "{\"title\":\"Tu hábito está por empezar\"}";

    private static KeyPair vapid;
    private static KeyPair navegador;
    private static byte[] authDelNavegador;
    private static HttpPost pedido;

    @BeforeAll
    static void armarUnPedido() throws Exception {
        // La misma ruta que sigue el backend: el adaptador registra BC al cargarse, antes de tocar web-push.
        WebPushAdapter.registrarBouncyCastle();

        KeyPairGenerator generador = KeyPairGenerator.getInstance("EC");
        generador.initialize(new ECGenParameterSpec("secp256r1"));
        vapid = generador.generateKeyPair();
        navegador = generador.generateKeyPair();
        authDelNavegador = new byte[16];
        Arrays.fill(authDelNavegador, (byte) 7);

        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        PushService servicio = new PushService(
                base64.encodeToString(punto((ECPublicKey) vapid.getPublic())),
                base64.encodeToString(a32Bytes(((ECPrivateKey) vapid.getPrivate()).getS())),
                SUBJECT);
        Notification notificacion = Notification.builder()
                .endpoint(ENDPOINT)
                .userPublicKey(base64.encodeToString(punto((ECPublicKey) navegador.getPublic())))
                .userAuth(base64.encodeToString(authDelNavegador))
                .payload(PAYLOAD)
                .build();
        pedido = servicio.preparePost(notificacion, Encoding.AES128GCM);
    }

    @Test
    @DisplayName("el JWT de VAPID trae ES256, el origen del servicio de push y el contacto, y su firma es válida con la clave pública VAPID")
    void laFirmaVapidEsValida() throws Exception {
        String autorizacion = pedido.getFirstHeader("Authorization").getValue();
        String jwt = autorizacion.substring(autorizacion.indexOf("t=") + 2, autorizacion.indexOf(','));
        String[] partes = jwt.split("\\.");

        assertThat(partes).hasSize(3);
        assertThat(decodificar(partes[0])).contains("\"alg\":\"ES256\"");
        assertThat(decodificar(partes[1]))
                .contains("\"aud\":\"https://push.example.test\"")
                .contains("\"sub\":\"" + SUBJECT + "\"");

        // ES256 de JWS es r||s de 64 bytes (RFC 7518 §3.4), el formato P1363 del JDK.
        Signature firma = Signature.getInstance("SHA256withECDSAinP1363Format");
        firma.initVerify(vapid.getPublic());
        firma.update((partes[0] + "." + partes[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(firma.verify(Base64.getUrlDecoder().decode(partes[2])))
                .as("el servicio de push rechazaría un JWT firmado con otra clave").isTrue();
    }

    @Test
    @DisplayName("el cuerpo cifrado en aes128gcm lo descifra el navegador con su clave privada y su secreto de autenticación")
    void elNavegadorPuedeAbrirElCuerpo() throws Exception {
        byte[] cuerpo = EntityUtils.toByteArray(pedido.getEntity());

        // Cabecera de RFC 8188: sal (16) | tamaño de registro (4) | largo del id (1) | id (la clave efímera del servidor).
        byte[] sal = Arrays.copyOfRange(cuerpo, 0, 16);
        int largoDelId = cuerpo[20] & 0xff;
        byte[] claveDelServidor = Arrays.copyOfRange(cuerpo, 21, 21 + largoDelId);
        byte[] registro = Arrays.copyOfRange(cuerpo, 21 + largoDelId, cuerpo.length);
        assertThat(largoDelId).as("clave pública P-256 sin comprimir").isEqualTo(65);

        ECParameterSpec curva = ((ECPublicKey) navegador.getPublic()).getParams();
        ECPublicKey publicaDelServidor = (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(
                new ECPoint(new BigInteger(1, Arrays.copyOfRange(claveDelServidor, 1, 33)),
                        new BigInteger(1, Arrays.copyOfRange(claveDelServidor, 33, 65))), curva));
        KeyAgreement acuerdo = KeyAgreement.getInstance("ECDH");
        acuerdo.init(navegador.getPrivate());
        acuerdo.doPhase(publicaDelServidor, true);
        byte[] secretoEcdh = acuerdo.generateSecret();

        // RFC 8291 §3.4: HKDF con el secreto de autenticación, y después con la sal del registro.
        byte[] claveInfo = concatenar("WebPush: info\0".getBytes(StandardCharsets.US_ASCII),
                punto((ECPublicKey) navegador.getPublic()), claveDelServidor);
        byte[] materialDeClave = Arrays.copyOf(hmac(hmac(authDelNavegador, secretoEcdh),
                concatenar(claveInfo, new byte[]{1})), 32);
        byte[] prk = hmac(sal, materialDeClave);
        byte[] clave = Arrays.copyOf(hmac(prk, concatenar(
                "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concatenar(
                "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 12);

        Cipher cifrado = Cipher.getInstance("AES/GCM/NoPadding");
        cifrado.init(Cipher.DECRYPT_MODE, new SecretKeySpec(clave, "AES"), new GCMParameterSpec(128, nonce));
        byte[] texto = cifrado.doFinal(registro);

        // Al final del registro va el delimitador 0x02 (último registro) y, antes, el relleno en ceros.
        int fin = texto.length - 1;
        while (texto[fin] == 0) {
            fin--;
        }
        assertThat(texto[fin]).as("delimitador del último registro").isEqualTo((byte) 2);
        assertThat(new String(texto, 0, fin, StandardCharsets.UTF_8)).isEqualTo(PAYLOAD);
    }

    private static String decodificar(String parteBase64Url) {
        return new String(Base64.getUrlDecoder().decode(parteBase64Url), StandardCharsets.UTF_8);
    }

    /** La clave pública como la manda un navegador y la espera VAPID: punto sin comprimir de 65 bytes. */
    private static byte[] punto(ECPublicKey publica) {
        byte[] punto = new byte[65];
        punto[0] = 0x04;
        System.arraycopy(a32Bytes(publica.getW().getAffineX()), 0, punto, 1, 32);
        System.arraycopy(a32Bytes(publica.getW().getAffineY()), 0, punto, 33, 32);
        return punto;
    }

    private static byte[] a32Bytes(BigInteger numero) {
        byte[] bytes = numero.toByteArray();
        byte[] fijo = new byte[32];
        int largo = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - largo, fijo, 32 - largo, largo);
        return fijo;
    }

    private static byte[] hmac(byte[] clave, byte[] datos) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(clave, "HmacSHA256"));
        return mac.doFinal(datos);
    }

    private static byte[] concatenar(byte[]... partes) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        for (byte[] parte : partes) {
            salida.write(parte, 0, parte.length);
        }
        return salida.toByteArray();
    }
}
