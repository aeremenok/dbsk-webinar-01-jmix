package com.debugskills.equipment.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void pingIsOpenAndCountsSeededEmployees() throws Exception {
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.employees").value(5));
    }

    @Test
    void apiChainDoesNotRequireCsrfToken() throws Exception {
        // /api/ping has no POST mapping: 405 means the request got past security (no 403 from CSRF).
        mockMvc.perform(post("/api/ping"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void uiChainKeepsItsCsrfProtection() throws Exception {
        // Everything outside /api/** still goes through the standard Jmix UI chain.
        mockMvc.perform(post("/"))
                .andExpect(status().isForbidden());
    }
}
