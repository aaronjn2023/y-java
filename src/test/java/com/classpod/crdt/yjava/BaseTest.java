package com.classpod.crdt.yjava;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.MockitoAnnotations;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public abstract class BaseTest extends PowerMockTestCase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    protected Map<String, Object> toMap(Object obj) {

        try {
            return MAPPER.readValue(MAPPER.writeValueAsString(obj), new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException e) {
            return Collections.emptyMap();
        }
    }
}