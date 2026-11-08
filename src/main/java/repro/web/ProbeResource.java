package repro.web;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("probe")
@Produces(MediaType.TEXT_PLAIN + ";charset=UTF-8")
public class ProbeResource {
    @Inject
    Cases cases;

    @GET
    public String run() throws Exception {
        List<Cases.Result> results = cases.runAll();
        StringBuilder out = new StringBuilder();
        long failed = results.stream().filter(r -> !r.ok()).count();
        for (Cases.Result r : results) {
            out.append(r.ok() ? "PASS " : "FAIL ").append(r.id()).append("  ").append(r.what()).append('\n');
            out.append("       expected: ").append(r.expected()).append('\n');
            out.append("       actual:   ").append(r.actual()).append('\n');
        }
        out.append("\nSUMMARY: ").append(results.size() - failed).append(" passed, ").append(failed).append(" failed of ")
                .append(results.size()).append('\n');
        return out.toString();
    }
}
