package com.optima.api.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Deserializador custom para fechas, rechaza las que traigan zona horaria
 * Nosotros trabajamos con LocalDateTime, o sea sin zona, y si el frontend manda una fecha con "Z"
 * o con "+02:00" al final la rechazamos para evitar problemas
 */
public class StrictLocalDateTimeDeserializer extends LocalDateTimeDeserializer {

    // este patron detecta fechas que terminan en algo como +02:00 o -05:00
    private static final Pattern OFFSET_PATTERN = Pattern.compile(".*[+-]\\d{2}:\\d{2}$");

    /**
     * Antes de parsear la fecha, comprobamos que no traiga zona horaria
     */
    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getText();
        // si termina en Z o en un offset tipo +02:00, la rechazamos
        if (text != null && (text.endsWith("Z") || OFFSET_PATTERN.matcher(text).matches())) {
            throw new TimezoneNotAllowedException(p, text);
        }
        return super.deserialize(p, ctxt);
    }

    /**
     * Excepcion que lanzamos cuando nos mandan una fecha con zona horaria
     */
    public static class TimezoneNotAllowedException extends InvalidFormatException {
        public TimezoneNotAllowedException(JsonParser p, String value) {
            super(p,
                "Las fechas no deben incluir zona horaria. Usa formato 'YYYY-MM-DDTHH:mm:ss' sin sufijo.",
                value, LocalDateTime.class);
        }
    }
}
