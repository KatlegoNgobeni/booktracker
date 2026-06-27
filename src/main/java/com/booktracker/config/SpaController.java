package com.booktracker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * SPA catch-all: serves any non-API, non-asset request with {@code index.html}
 * so React Router's {@code BrowserRouter} can handle client-side routing in
 * production.
 *
 * <h2>Why WebMvcConfigurer, not @Controller</h2>
 * <p>Spring Framework 6 uses {@code PathPatternParser} by default. That parser
 * enforces a structural rule: {@code **} must be the terminal element of any
 * pattern. The classic catch-all pattern {@code /**&#47;{path:[^\\.]*}} places
 * a named capture group <em>after</em> {@code **}, which the parser rejects at
 * context startup with "No more pattern data allowed after {*...} or ** pattern
 * element". Using {@code WebMvcConfigurer.addResourceHandlers()} side-steps
 * this entirely: the resource-resolver chain runs at request time, not at
 * context initialisation, so PathPatternParser never validates the fallback
 * logic.
 *
 * <h2>How it works</h2>
 * <p>A custom {@code PathResourceResolver} is added to the resource chain for
 * {@code /**}. For every request:
 * <ol>
 *   <li>If the requested path resolves to a real file in {@code classpath:/static/}
 *       (e.g., {@code /assets/index-abc.js}, {@code /favicon.svg}), that file
 *       is served directly.</li>
 *   <li>Otherwise, if the path has <strong>no file extension</strong> (i.e., it
 *       looks like a React Router route such as {@code /shelf} or
 *       {@code /books/OL123W}), {@code index.html} is returned.</li>
 *   <li>If the path has a file extension but the file is missing (a real 404 for
 *       a non-existent asset), {@code null} is returned, allowing Spring Boot to
 *       emit its standard 404 response.</li>
 * </ol>
 *
 * <h2>Priority</h2>
 * <p>Spring Boot registers its own {@code /**} resource handler via
 * {@code WebMvcAutoConfiguration}. Because custom {@link WebMvcConfigurer}
 * callbacks run after the auto-configuration, this registration is inserted
 * last into the internal {@code LinkedHashMap} and therefore takes precedence,
 * effectively replacing the default handler with this SPA-aware variant.
 *
 * <h2>Security</h2>
 * <p>Only files under {@code classpath:/static/} are served. The fallback
 * {@code index.html} is also read from that location; if it does not exist
 * (i.e., no frontend build has been copied in), the handler returns {@code null}
 * and Spring Boot emits a 404 — the application never leaks classpath content
 * from outside {@code /static/}.
 */
@Configuration
public class SpaController implements WebMvcConfigurer {

    /**
     * Register a {@code /**} resource handler that serves static assets from
     * {@code classpath:/static/} and falls back to {@code index.html} for any
     * path that does not contain a dot (i.e., a SPA client-side route).
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {

                    @Override
                    protected Resource getResource(String resourcePath, Resource location)
                            throws IOException {

                        // 1. Try to serve the exact file (e.g., /assets/main.js → main.js exists).
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }

                        // 2. SPA route: serve index.html for paths without a file extension
                        //    (e.g., /shelf, /books/OL123W, /stats/2024).
                        if (!resourcePath.contains(".")) {
                            Resource index = new ClassPathResource("/static/index.html");
                            return (index.exists() && index.isReadable()) ? index : null;
                        }

                        // 3. Real 404 — a dotted path that does not exist (e.g., /missing.png).
                        return null;
                    }
                });
    }
}
