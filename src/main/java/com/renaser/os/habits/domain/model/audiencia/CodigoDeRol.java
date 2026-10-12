package com.renaser.os.habits.domain.model.audiencia;

import java.util.regex.Pattern;

/**
 * Código de un rol del sistema tal como viaja en la API ({@code TRAINEE}, {@code MENTOR}…). Es opaco a propósito: la
 * audiencia de un hábito no conoce los roles concretos, así un rol nuevo funciona sin tocar estas reglas (spec 001,
 * FR-004). Qué códigos existen lo decide {@code users.api.UserRole}, y lo valida el caso de uso que guarda.
 */
public record CodigoDeRol(String valor) {

    private static final Pattern FORMATO = Pattern.compile("[A-Z][A-Z_]*");

    public CodigoDeRol {
        if (valor == null || !FORMATO.matcher(valor).matches()) {
            throw new IllegalArgumentException("Código de rol inválido: " + valor);
        }
    }

    public static CodigoDeRol of(String valor) {
        return new CodigoDeRol(valor);
    }

    @Override
    public String toString() {
        return valor;
    }
}
