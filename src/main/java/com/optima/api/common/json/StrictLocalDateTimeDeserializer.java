package com.optima.api.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Deserializador que rechaza fechas con zona horaria.
 *
 * El proyecto trabaja con horas locales para citas y ausencias. Por eso
 * solo se aceptan valores como 2027-06-07T10:00:00.
 */
public class StrictLocalDateTimeDeserializer extends LocalDateTimeDeserializer {

    private static final Pattern OFFSET_PATTERN = Pattern.compile(".*[+-]\\d{2}:\\d{2}$");

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getText();
        if (text != null && (text.endsWith("Z") || OFFSET_PATTERN.matcher(text).matches())) {
            throw new TimezoneNotAllowedException(p, text);
        }
        return super.deserialize(p, ctxt);
    }

    /** Error especifico para fechas recibidas con zona horaria. */
    public static class TimezoneNotAllowedException extends InvalidFormatException {
        public TimezoneNotAllowedException(JsonParser p, String value) {
            super(p,
                "Las fechas no deben incluir zona horaria. Usa formato 'YYYY-MM-DDTHH:mm:ss' sin sufijo.",
                value, LocalDateTime.class);
        }
    }
}
