package dev.archi.jobhub.ingest;

import java.util.List;

import dev.archi.jobhub.ingest.greenhouse.GreenhouseClient;
import io.swagger.v3.oas.annotations.Operation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Temporary M1 endpoint: fetch and normalize one Greenhouse board without touching
 * the database. Replaced by real ingest + read APIs in M2/M5.
 */
@RestController
@RequestMapping("/dev/preview")
class DevPreviewController {

	private final GreenhouseClient greenhouseClient;

	DevPreviewController(GreenhouseClient greenhouseClient) {
		this.greenhouseClient = greenhouseClient;
	}

	@Operation(summary = "Fetch and normalize a Greenhouse board (no DB)")
	@GetMapping("/{boardToken}")
	List<NormalizedJob> preview(@PathVariable String boardToken) {
		return this.greenhouseClient.fetchJobs(boardToken);
	}

}
