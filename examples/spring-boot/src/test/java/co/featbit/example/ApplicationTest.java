package co.featbit.example;

import co.featbit.commons.model.FBUser;
import co.featbit.server.integrations.FBUserContextHolder;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "featbit.offline=true")
@AutoConfigureMockMvc
class ApplicationTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserContextFilter filter;

    @AfterEach
    void clearUser() {
        FBUserContextHolder.remove();
    }

    @Test
    void routesIndependentFlagsAndIsolatesRequests() throws Exception {
        assertName(get("/name"), "albert", "einstein");
        assertName(get("/name").header("X-User-Key", "formatted"), "Albert", "E.");
        assertName(get("/name"), "albert", "einstein");
        assertName(get("/name").header("X-User-Key", "first-only"), "Albert", "einstein");
        assertName(get("/name").header("X-User-Key", "last-only"), "albert", "E.");
        assertName(get("/name").header("X-User-Key", "unknown"), "albert", "einstein");
    }

    @Test
    void replacesStaleContextAndClearsItWhenTheRequestFails() {
        FBUserContextHolder.setCurrentUser(new FBUser.Builder("stale").userName("stale").build(), true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Key", "formatted");
        ServletException failure = new ServletException("Request failed");

        assertSame(failure, assertThrows(ServletException.class, () -> filter.doFilter(request,
                new MockHttpServletResponse(), (req, response) -> {
                    assertEquals("formatted", FBUserContextHolder.getCurrentUser().getKey());
                    throw failure;
                })));
        assertNull(FBUserContextHolder.getCurrentUser());
    }

    private void assertName(MockHttpServletRequestBuilder request, String firstName, String lastName) throws Exception {
        mvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value(firstName))
                .andExpect(jsonPath("$.lastName").value(lastName));
        assertNull(FBUserContextHolder.getCurrentUser());
    }
}
