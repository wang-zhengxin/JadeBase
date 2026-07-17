package ai.jadebase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:jadebase-user-admin-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
class UserAdminIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void managesInvitationsRegistrationRolesAndUserStatus() throws Exception {
        Cookie ownerSession = register("owner@jadebase.local", "owner-password-2026", null)
                .getResponse().getCookie("JADEBASE_SESSION");
        assertThat(ownerSession).isNotNull();

        mockMvc.perform(put("/api/v1/admin/users/registration-policy")
                        .cookie(ownerSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restrictOpenSignup\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restrictOpenSignup").value(true));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"outsider@jadebase.local\",\"password\":\"password-2026\"}"))
                .andExpect(status().isForbidden());

        MvcResult invitationResult = mockMvc.perform(post("/api/v1/admin/users/invitations")
                        .cookie(ownerSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@jadebase.local\",\"role\":\"member\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitation.email").value("member@jadebase.local"))
                .andReturn();
        JsonNode invitation = objectMapper.readTree(invitationResult.getResponse().getContentAsString());
        String token = invitation.path("token").asText();
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/v1/auth/registration-policy").param("inviteToken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitationValid").value(true))
                .andExpect(jsonPath("$.invitationEmail").value("member@jadebase.local"));

        Cookie memberSession = register("member@jadebase.local", "member-password-2026", token)
                .getResponse().getCookie("JADEBASE_SESSION");
        assertThat(memberSession).isNotNull();

        MvcResult usersResult = mockMvc.perform(get("/api/v1/admin/users")
                        .cookie(ownerSession).param("query", "member"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeUsers").value(2))
                .andExpect(jsonPath("$.pendingInvites").value(0))
                .andExpect(jsonPath("$.users[0].role").value("member"))
                .andReturn();
        String memberId = objectMapper.readTree(usersResult.getResponse().getContentAsString())
                .path("users").get(0).path("id").asText();

        mockMvc.perform(get("/api/v1/admin/users").cookie(memberSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/admin/users/{userId}", memberId)
                        .cookie(ownerSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"suspended\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("suspended"));

        mockMvc.perform(get("/api/v1/auth/me").cookie(memberSession))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@jadebase.local\",\"password\":\"member-password-2026\"}"))
                .andExpect(status().isUnauthorized());

        MvcResult ownerList = mockMvc.perform(get("/api/v1/admin/users")
                        .cookie(ownerSession).param("query", "owner"))
                .andExpect(status().isOk()).andReturn();
        String ownerId = objectMapper.readTree(ownerList.getResponse().getContentAsString())
                .path("users").get(0).path("id").asText();
        mockMvc.perform(patch("/api/v1/admin/users/{userId}", ownerId)
                        .cookie(ownerSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"member\"}"))
                .andExpect(status().isBadRequest());

        MvcResult secondInvite = mockMvc.perform(post("/api/v1/admin/users/invitations")
                        .cookie(ownerSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"later@jadebase.local\",\"role\":\"member\"}"))
                .andExpect(status().isCreated()).andReturn();
        String invitationId = objectMapper.readTree(secondInvite.getResponse().getContentAsString())
                .path("invitation").path("id").asText();
        mockMvc.perform(delete("/api/v1/admin/users/invitations/{invitationId}", invitationId)
                        .cookie(ownerSession))
                .andExpect(status().isNoContent());
    }

    private MvcResult register(String email, String password, String inviteToken) throws Exception {
        String tokenProperty = inviteToken == null ? "" : ",\"inviteToken\":\"" + inviteToken + "\"";
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"" + tokenProperty + "}"))
                .andExpect(status().isCreated())
                .andReturn();
    }
}
