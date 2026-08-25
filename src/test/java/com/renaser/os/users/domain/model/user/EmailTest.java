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

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> new Email("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
