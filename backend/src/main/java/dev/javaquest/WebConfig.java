package dev.javaquest;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.*;
import java.io.IOException;
import java.util.Set;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String origin;
    public WebConfig(@Value("${quest.origin}") String origin) { this.origin=origin; }
    @Override public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(origin).allowedMethods("GET","POST","PUT","OPTIONS")
            .allowedHeaders("Content-Type","X-Quest-Client").allowCredentials(true);
    }
    @Component
    static class RequestGuard extends OncePerRequestFilter {
        @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            res.setHeader("X-Content-Type-Options","nosniff");
            res.setHeader("Cache-Control","no-store");
            if (req.getRequestURI().startsWith("/api/") && !Set.of("GET","HEAD","OPTIONS").contains(req.getMethod())
                    && !"1".equals(req.getHeader("X-Quest-Client"))) {
                res.setStatus(403); res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"message\":\"缺少请求校验头\"}"); return;
            }
            if(req.getContentLengthLong()>200000) {
                res.setStatus(413); res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"message\":\"请求过大\"}"); return;
            }
            chain.doFilter(req,res);
        }
    }
}
