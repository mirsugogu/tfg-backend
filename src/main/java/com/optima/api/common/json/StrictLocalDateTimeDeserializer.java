package com.optima.api.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * StrictLocalDateTimeDeserializer - Rechaza fechas con zona horaria.
 *
 * Los campos {@link LocalDateTime} de los DTOs request (citas, ausencias,
 * bloqueos) representan hora local del negocio, NO un instante en UTC.
 * Si el cliente envia "2027-06-07T10:00:00Z", el Jackson por defecto
 * acepta la cadena y descarta silenciosamente el sufijo "Z" — guardando
 * la hora como local. Eso provoca desalineacion frontend/backend cuando
 * el cliente cree que estaba mandando UTC.
 *
 * Este deserializer falla fast con 400 cuando detecta:
 *   - Sufijo "Z" (UTC).
 *   - Offset explicito tipo "+02:00" o "-05:00" al final.
 *
 * El error se traduce a 400 mediante {@code TimezoneNotAllowedException},
 * subclase de {@link InvalidFormatException}, reconocida por
 * {@code GlobalExceptionHandler.handleUnreadableBody}.
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

    /**
     * Marker que distingue el rechazo por zona horaria de cualquier otro
     * fallo de formato. {@code GlobalExceptionHandler} comprueba este
     * tipo para emitir el mensaje localizado.
     */
    public static class TimezoneNotAllowedException extends InvalidFormatException {
        public TimezoneNotAllowedException(JsonParser p, String value) {
            super(p,
                "Las fechas no deben incluir zona horaria. Usa formato 'YYYY-MM-DDTHH:mm:ss' sin sufijo.",
                value, LocalDateTime.class);
        }
    }
}
