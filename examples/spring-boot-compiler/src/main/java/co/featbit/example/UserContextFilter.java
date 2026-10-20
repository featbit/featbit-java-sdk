package co.featbit.example;

import co.featbit.commons.model.FBUser;
import co.featbit.server.integrations.FBUserContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class UserContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        FBUserContextHolder.remove();
        try {
            String userKey = request.getHeader("X-User-Key");
            if (StringUtils.hasText(userKey)) {
                FBUserContextHolder.setCurrentUser(new FBUser.Builder(userKey).userName(userKey).build(), false);
            }
            chain.doFilter(request, response);
        } finally {
            FBUserContextHolder.remove();
        }
    }
}
