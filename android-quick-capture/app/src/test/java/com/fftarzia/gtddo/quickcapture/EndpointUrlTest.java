package com.fftarzia.gtddo.quickcapture;

import org.junit.Test;
import static org.junit.Assert.*;

public class EndpointUrlTest {
    @Test public void trimsTrailingSlash() {
        assertEquals("https://tasks.example.com", EndpointUrl.normalize("  https://tasks.example.com/  "));
    }

    @Test public void acceptsHttpsWithPort() {
        assertEquals("https://tasks.example.com:8443/api/v1/inbox",
                EndpointUrl.inbox("https://tasks.example.com:8443"));
    }

    @Test public void rejectsHttp() {
        assertInvalid("http://tasks.example.com");
    }

    @Test public void rejectsOtherOriginPaths() {
        assertInvalid("https://tasks.example.com/app");
    }

    @Test public void rejectsQueryOrFragment() {
        assertInvalid("https://tasks.example.com/?key=secret");
        assertInvalid("https://tasks.example.com/#fragment");
    }

    @Test public void rejectsCredentialsInUrl() {
        assertInvalid("https://user:password@tasks.example.com/");
    }

    private void assertInvalid(String value) {
        try {
            EndpointUrl.normalize(value);
            fail("URL should have been rejected: " + value);
        } catch (IllegalArgumentException expected) {
            // Correct.
        }
    }
}
