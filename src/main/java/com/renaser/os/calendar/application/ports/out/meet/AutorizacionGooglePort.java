package com.renaser.os.calendar.application.ports.out.meet;

import java.util.Set;

/**
 * El baile de OAuth con Google (spec 040, FR-010): pedir el consentimiento, canjear el código por el permiso permanente y revocarlo. El
 * {@code client_secret} vive solo en el adaptador, nunca en el cliente ni en un registro.
 */
public interface AutorizacionGooglePort {

    /** Lo necesario para mandar a la persona a Google y reconocer su regreso. */
    record Consentimiento(String url, String state, String codeVerifier) {
    }

    /** El permiso permanente y de qué cuenta es; {@code permisosConcedidos} son los que Google de verdad dio. */
    record PermisoConcedido(String refreshToken, Set<String> permisosConcedidos, String correo) {
    }

    /** Los permisos que se piden y que Google tiene que dar todos; si falta alguno, la conexión no sirve (FR-010). */
    Set<String> permisosObligatorios();

    /** Arma la URL de Google con {@code access_type=offline}, {@code prompt=consent} y PKCE; genera el {@code state} y el verificador. */
    Consentimiento pedirConsentimiento();

    /** Canjea el código de regreso; falla con {@link GoogleRechazoException} si Google lo rechaza. */
    PermisoConcedido canjear(String codigo, String codeVerifier);

    /** Le pide a Google que anule el permiso. Si ya estaba anulado, no es un error. */
    void revocar(String refreshToken);

    /**
     * Crea el calendario propio «Renaser 90 días» y devuelve su id (FR-034). Usa el permiso recién concedido, que todavía no está guardado.
     * Falla si el permiso concedido no lo deja.
     */
    String crearCalendarioPropio(String refreshToken, String nombre);
}
