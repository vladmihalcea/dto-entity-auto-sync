package com.vladmihalcea.phd.util;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.util.TokenBuffer;

/**
 * @author Vlad Mihalcea
 */
public class JsonUtil {

    public static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    public static <T> T clone(T source) {
        TokenBuffer tb = TokenBuffer.forGeneration();
        JSON_MAPPER.writeValue(tb, source);
        return (T) JSON_MAPPER.readValue(tb.asParser(), source.getClass());
    }

}
