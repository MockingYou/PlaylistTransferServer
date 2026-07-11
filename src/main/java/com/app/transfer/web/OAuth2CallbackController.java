package com.app.transfer.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OAuth2CallbackController {

    @GetMapping(value = "/login/oauth2/code/{registrationId}", produces = MediaType.TEXT_HTML_VALUE)
    public String callbackLanding(@PathVariable String registrationId) {
        return """
            <!DOCTYPE html>
            <html>
              <head><title>Connected</title></head>
              <body style="font-family: sans-serif; background: #0D1012; color: #ECEEEC;
                           display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0;">
                <p>%s connected. This window will close automatically…</p>
                <script>
                  if (window.opener) {
                    window.opener.postMessage({ type: 'oauth-connected', provider: '%s' }, '*');
                  }
                  window.close();
                </script>
              </body>
            </html>
            """.formatted(capitalize(registrationId), registrationId);
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}