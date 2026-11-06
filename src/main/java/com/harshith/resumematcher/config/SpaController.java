package com.harshith.resumematcher.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the React app (built into static/ by the Dockerfile) for its page URLs, so reloading
 * or opening a link like /results/12 works. Keep the list in sync with frontend/src/App.tsx.
 * Without a frontend build (e.g. running only the API locally) these paths return 404.
 */
@Controller
public class SpaController {

    /** Also used by SecurityConfig to make these pages public. */
    static final String[] PAGES = {
            "/", "/login", "/register", "/privacy",
            "/resumes", "/analyze", "/history", "/account", "/results/{id:\\d+}",
    };

    // Same list as PAGES (annotations can't refer to an array constant).
    @GetMapping({"/", "/login", "/register", "/privacy",
            "/resumes", "/analyze", "/history", "/account", "/results/{id:\\d+}"})
    public String index() {
        return "forward:/index.html";
    }
}
