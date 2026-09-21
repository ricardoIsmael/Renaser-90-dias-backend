package com.renaser.os.users.domain.model.user;

import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El primero de los dos filtros que autorizan un borrado en el bucket: la FORMA de la clave.
 *
 * <p>Las pruebas de abajo estan escritas en las dos direcciones a proposito — que lo propio se
 * reconozca vale tanto como que lo ajeno no. Un filtro que solo se prueba por el lado positivo
 * pasa igual de verde siendo un {@code return true}.
 */
class ClavesDeCuentaTest {

    private static final UserId PURGADO = UserId.of(UUID.fromString("11111111-1111-4111-8111-111111111111"));
    private static final UserId OTRO = UserId.of(UUID.fromString("22222222-2222-4222-8222-222222222222"));

    private final ClavesDeCuenta claves = ClavesDeCuenta.de(PURGADO);

    @Nested
    @DisplayName("reconoce lo que el servidor armo para esta cuenta")
    class LoPropio {

        @Test
        @DisplayName("el avatar es la clave exacta avatares/<id>, sin nada detras")
        void avatar() {
            assertThat(claves.avatar()).isEqualTo("avatares/" + PURGADO);
            assertThat(claves.contiene("avatares/" + PURGADO)).isTrue();
        }

        @Test
        @DisplayName("los prefijos con el id segundo: firmas, evidencia, rocas, racha, onboarding, soporte")
        void prefijosConIdSegundo() {
            assertThat(claves.contiene("firmas/" + PURGADO + "/fase_2.svg")).isTrue();
            assertThat(claves.contiene("evidencia-habitos/" + PURGADO + "/reg-1/abc")).isTrue();
            assertThat(claves.contiene("rocas/" + PURGADO + "/roca-1")).isTrue();
            assertThat(claves.contiene("dia-sin-celular/" + PURGADO + "/racha-1")).isTrue();
            assertThat(claves.contiene("onboarding/" + PURGADO + "/AUDIO/abc")).isTrue();
            assertThat(claves.contiene("soporte/" + PURGADO + "/1699999999-foto.jpg")).isTrue();
        }

        @Test
        @DisplayName("en el Muro el id va TERCERO, detras de la carpeta")
        void muroLlevaElIdTercero() {
            assertThat(claves.contiene("muro/fotos/" + PURGADO + "/abc")).isTrue();
            assertThat(claves.contiene("muro/videos/" + PURGADO + "/abc")).isTrue();
        }

        @Test
        @DisplayName("el UUID en mayusculas es el mismo UUID: no deja el objeto sin borrar")
        void comparaSinDistinguirMayusculas() {
            assertThat(claves.contiene("firmas/" + PURGADO.toString().toUpperCase() + "/fase_1.svg")).isTrue();
        }
    }

    @Nested
    @DisplayName("NO reconoce nada que pueda ser de otro")
    class LoAjeno {

        @Test
        @DisplayName("la misma forma con el id de otra persona")
        void claveDeOtroUsuario() {
            assertThat(claves.contiene("avatares/" + OTRO)).isFalse();
            assertThat(claves.contiene("firmas/" + OTRO + "/fase_2.svg")).isFalse();
            assertThat(claves.contiene("evidencia-habitos/" + OTRO + "/reg-1/abc")).isFalse();
            assertThat(claves.contiene("muro/fotos/" + OTRO + "/abc")).isFalse();
        }

        @Test
        @DisplayName("el chat no lleva id de usuario: no hay forma de saber de quien es, no se toca")
        void chatNuncaEsExclusivo() {
            assertThat(claves.contiene("chat/una-conversacion/fotos/abc")).isFalse();
            // Ni siquiera si el id del purgado aparece en algun segmento: la clave del chat la
            // puede referenciar cualquier participante de esa conversacion.
            assertThat(claves.contiene("chat/" + PURGADO + "/fotos/abc")).isFalse();
        }

        @Test
        @DisplayName("prefijos de catalogo y de staff, que son de todos")
        void catalogoYStaff() {
            assertThat(claves.contiene("calendar/portadas/evento-1")).isFalse();
            assertThat(claves.contiene("audioterapias/semana-1.mp3")).isFalse();
            assertThat(claves.contiene("guias/habito-1/imagen.png")).isFalse();
        }

        @Test
        @DisplayName("un id que solo sea prefijo de otro no cuenta como propio")
        void idQueEsPrefijoDeOtro() {
            // Sin la barra final, "firmas/<id>" seria prefijo de "firmas/<id>abc/...".
            assertThat(claves.contiene("firmas/" + PURGADO + "abc/fase_1.svg")).isFalse();
            // Y el avatar es exacto: nada puede colgar detras.
            assertThat(claves.contiene("avatares/" + PURGADO + "/extra")).isFalse();
            assertThat(claves.contiene("avatares/" + PURGADO + "-copia")).isFalse();
        }

        @Test
        @DisplayName("muro sin las cuatro partes no tiene id de autor que mirar")
        void muroIncompleto() {
            assertThat(claves.contiene("muro/" + PURGADO)).isFalse();
            assertThat(claves.contiene("muro/fotos/" + PURGADO)).isFalse();
        }

        @Test
        @DisplayName("travesia, barra inicial y barra doble: formas que el servidor no arma")
        void formasRaras() {
            assertThat(claves.contiene("/firmas/" + PURGADO + "/fase_1.svg")).isFalse();
            assertThat(claves.contiene("firmas/" + PURGADO + "/../../avatares/" + OTRO)).isFalse();
            assertThat(claves.contiene("firmas//" + PURGADO + "/fase_1.svg")).isFalse();
        }

        @Test
        @DisplayName("null y vacio")
        void nullYVacio() {
            assertThat(claves.contiene(null)).isFalse();
            assertThat(claves.contiene("")).isFalse();
            assertThat(claves.contiene("   ")).isFalse();
        }
    }
}
