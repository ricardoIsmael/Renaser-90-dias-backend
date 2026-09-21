package com.renaser.os.users.domain.model.user;

import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Las claves de objeto del bucket que el SERVIDOR arma para una cuenta, y el unico juez de si
 * una ruta cualquiera cae dentro de ellas.
 *
 * <p><b>Para que existe.</b> La purga de cuenta tiene que borrar los archivos del ex usuario
 * (evidencia, firmas del Pacto, avatar, audios de onboarding, fotos del Muro): hasta ahora
 * borraba la fila y dejaba los objetos en el bucket para siempre. Pero la lista de rutas que se
 * saca de la base NO alcanza como autorizacion para borrar, por dos razones distintas y las dos
 * reales en este repositorio:
 *
 * <ol>
 *   <li><b>Hay columnas de ruta que llena el cliente sin que nadie las mire.</b>
 *       {@code BitacoraNocturnaService.escribir} guarda {@code audioBucket}/{@code audioRuta}
 *       tal como vienen en el cuerpo, y {@code SesionBloqueo} hace lo mismo con la evidencia de
 *       salida. Si la purga borrara "las rutas que tiene la cuenta", cualquiera podria haber
 *       dejado escrito {@code avatares/<otroId>} en su propia bitacora y convertir su baja en un
 *       borrado de los archivos de otro. El filtro de abajo lo impide: esa ruta no esta bajo
 *       ningun prefijo del que se purga, asi que no se toca.</li>
 *   <li><b>Una misma clave puede tener mas de un dueno.</b> Compartir una publicacion al chat
 *       ({@code CompartirPublicacionService}) NO copia el archivo: manda el mismo
 *       {@code muro/<carpeta>/<autorId>/<uuid>} como media del mensaje, y por eso
 *       {@code MensajeService} admite a proposito el prefijo {@code muro/}. Un mensaje del
 *       purgado que comparta la foto de OTRO trae una clave ajena; este filtro la descarta
 *       porque el id del dueno que lleva adentro no es el suyo.</li>
 * </ol>
 *
 * <p><b>Fail-closed.</b> Lo que no se reconoce, no se borra. Es deliberado que queden afuera las
 * claves {@code chat/<conversacionId>/...}: no llevan id de usuario, cualquier participante de
 * esa conversacion puede referenciarlas y el borrado de un mensaje es un tombstone
 * ({@code mensajes.eliminado_en}) que no libera nada — no hay forma de decidir aca que son
 * exclusivas, asi que se dejan.
 *
 * <p>Esta clase mira la FORMA de la clave. Que ademas ninguna fila que sobreviva a la purga la
 * siga referenciando lo responde la base (ver {@code RutasDeAlmacenamientoDeCuentaPort}); son
 * dos filtros distintos y hacen falta los dos.
 */
public final class ClavesDeCuenta {

    /**
     * Prefijos cuya clave es {@code <prefijo>/<usuarioId>/...} — el id va SEGUNDO. Salen de los
     * servicios que firman la subida, que son los que arman la clave del lado del servidor:
     * {@code ContratoFase.rutaFirma}, {@code EvidenciaRegistroService},
     * {@code RocaDiariaService}, {@code RachaService}, {@code MediaOnboarding} y
     * {@code TicketSoporteService}.
     */
    private static final List<String> PREFIJOS_CON_ID_SEGUNDO = List.of(
            "firmas",
            "evidencia-habitos",
            "rocas",
            "dia-sin-celular",
            "onboarding",
            "soporte");

    /** {@code muro/<carpeta>/<autorId>/<uuid>} ({@code PublicacionMuroService.rutaDeMedia}):
     * aca el id va TERCERO, detras de la carpeta (fotos/videos). */
    private static final String PREFIJO_MURO = "muro";

    /** {@code avatares/<usuarioId>}, sin nada detras: la clave del avatar es deterministica y se
     * reescribe en cada cambio de foto ({@code AvatarService.rutaDe}). */
    private static final String PREFIJO_AVATAR = "avatares";

    private final UserId usuarioId;
    /** El id normalizado una sola vez; comparar en minuscula no debilita nada (un UUID es hex) y
     * evita que una diferencia de mayusculas deje sin borrar un objeto que si era del usuario. */
    private final String idNormalizado;

    private ClavesDeCuenta(UserId usuarioId) {
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
        this.idNormalizado = usuarioId.toString().toLowerCase(Locale.ROOT);
    }

    public static ClavesDeCuenta de(UserId usuarioId) {
        return new ClavesDeCuenta(usuarioId);
    }

    /** La clave del avatar de esta cuenta. Deterministica: se recalcula con solo el id. */
    public String avatar() {
        return PREFIJO_AVATAR + "/" + usuarioId;
    }

    /**
     * {@code true} solo si la ruta tiene la forma de una clave que el servidor arma PARA ESTA
     * cuenta. Todo lo demas —null, vacio, prefijo desconocido, id de otro, {@code chat/...}—
     * devuelve {@code false} y por lo tanto no se borra.
     */
    public boolean contiene(String ruta) {
        if (ruta == null || ruta.isBlank()) {
            return false;
        }
        String limpia = ruta.trim();
        // Una clave con travesia, barra inicial o barra doble no se parece a ninguna que arme el
        // servidor. No vale la pena razonar sobre lo que significaria: no se toca.
        if (limpia.startsWith("/") || limpia.contains("//") || limpia.contains("..")) {
            return false;
        }
        String comparable = limpia.toLowerCase(Locale.ROOT);
        if (comparable.equals(PREFIJO_AVATAR + "/" + idNormalizado)) {
            return true;
        }
        for (String prefijo : PREFIJOS_CON_ID_SEGUNDO) {
            if (comparable.startsWith(prefijo + "/" + idNormalizado + "/")) {
                return true;
            }
        }
        if (comparable.startsWith(PREFIJO_MURO + "/")) {
            // muro/<carpeta>/<autorId>/<uuid>: sin las cuatro partes no hay id de autor que mirar.
            String[] partes = comparable.split("/");
            return partes.length >= 4 && idNormalizado.equals(partes[2]);
        }
        return false;
    }
}
