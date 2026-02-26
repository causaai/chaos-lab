package ai.causa.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Simple endpoint representing application work.
 *
 * This must stay lightweight so that
 * throughput changes reflect GC impact,
 * not business logic slowness.
 */
@Path("/work")
public class WorkResource {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public java.util.Map<String, String> work() {

        // small CPU task to simulate real request processing
        double x = 0;
        for (int i = 0; i < 10_000; i++) {
            x += Math.sqrt(i);
        }

        return java.util.Map.of("status", "ok");
    }
}
