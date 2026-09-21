package com.renaser.os.users.domain.model.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTest {

    @Test
    void normalizesToLowercaseAndTrims() {
        assertThat(new Email("  Ana.Perez@Renaser.COM ").value())
                .isEqualTo("ana.perez@renaser.com");
    }

    @Test
    void sameEmailWithDifferentCasingIsEqual() {
        assertThat(new Email("ANA@renaser.com")).isEqualTo(new Email("ana@renaser.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sin-arroba", "@renaser.com", "ana@", "ana@renaser", "ana @renaser.com"})
    void rejectsInvalidFormats(String invalid) {
        assertThatThrownBy(() -> new Email(invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Regresion del hallazgo del nombre JNDI: la parte de dominio de estos correos bajaba entera
     * hasta {@code getAttributes}, que ante un esquema de URL encamina la llamada al proveedor
     * JNDI de ese esquema en vez de al DNS — o sea, el backend se conectaba al host y al puerto
     * que eligio quien escribio el correo. Con el formato viejo
     * ({@code ^[^@\s]+@[^@\s]+\.[^@\s]{2,}$}) los cinco pasaban.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "a@ldap://baliza.ejemplo-del-atacante.tld:1389/x",
            "a@ldaps://baliza.ejemplo-del-atacante.tld:8443/x",
            "a@dns://baliza.ejemplo-del-atacante.tld:5353/x.y",
            "a@ldap://127.0.0.1:6379/aa",
            "a@ldap://169.254.169.254:80/aa"
    })
    void rejectsDomainsThatLookLikeAUrl(String conFormaDeUrl) {
        assertThatThrownBy(() -> new Email(conFormaDeUrl))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Ni siquiera hace falta la barra: dos puntos solos ya alcanzan para que haya un esquema. */
    @ParameterizedTest
    @ValueSource(strings = {"a@renaser.com:1389", "a@renaser.com/x", "a@ldap:renaser.com"})
    void rejectsDomainsWithColonOrSlash(String invalid) {
        assertThatThrownBy(() -> new Email(invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** La reja nueva es solo para el dominio: la parte local sigue siendo ancha. */
    @ParameterizedTest
    @ValueSource(strings = {
            "ana.perez@renaser.com",
            "ana+etiqueta@sub.renaser.com",
            "ana_perez@renaser.dev",
            "ana-perez@con-guion.com.pe"
    })
    void stillAcceptsOrdinaryAddresses(String valido) {
        assertThat(new Email(valido).value()).isEqualTo(valido);
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> new Email("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
