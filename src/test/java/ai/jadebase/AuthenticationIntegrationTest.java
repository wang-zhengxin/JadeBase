package ai.jadebase;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:jadebase-auth-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
class AuthenticationIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void registersAuthenticatesUpdatesAndRevokesUserSession() throws Exception {
        mockMvc.perform(get("/api/v1/settings"))
                .andExpect(status().isUnauthorized());

        MvcResult registration = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"LEWIS@example.com","password":"initial-pass-2026"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(cookie().httpOnly("JADEBASE_SESSION", true))
                .andExpect(jsonPath("$.email").value("lewis@example.com"))
                .andExpect(jsonPath("$.role").value("owner"))
                .andReturn();

        Cookie session = registration.getResponse().getCookie("JADEBASE_SESSION");
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/v1/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value(""));

        mockMvc.perform(patch("/api/v1/auth/me")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Lewis\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Lewis"));

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"initial-pass-2026","newPassword":"updated-pass-2026"}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/logout").cookie(session))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("JADEBASE_SESSION", 0));

        mockMvc.perform(get("/api/v1/auth/me").cookie(session))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"lewis@example.com","password":"updated-pass-2026"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly("JADEBASE_SESSION", true))
                .andExpect(jsonPath("$.displayName").value("Lewis"));
    }
}
