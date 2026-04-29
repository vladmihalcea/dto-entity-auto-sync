package com.vladmihalcea.phd.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import java.io.IOException;

/**
 * @author Vlad Mihalcea
 */
public class JsonUtil {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static <T> T clone(T source) {
        try {
            TokenBuffer tokenBuffer = new TokenBuffer(OBJECT_MAPPER, false);
            OBJECT_MAPPER.writeValue(tokenBuffer, source);
            return (T) OBJECT_MAPPER.readValue(tokenBuffer.asParser(), source.getClass());
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

}
