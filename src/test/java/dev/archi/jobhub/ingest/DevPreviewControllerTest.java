package dev.archi.jobhub.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.util.List;

import dev.archi.jobhub.company.AtsType;
import dev.archi.jobhub.ingest.exceptions.BoardNotFoundException;
import dev.archi.jobhub.ingest.greenhouse.GreenhouseClient;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(DevPreviewController.class)
class DevPreviewControllerTest {

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	GreenhouseClient greenhouseClient;

	@Test
	void returnsNormalizedJobsAsJson() {
		NormalizedJob job = new NormalizedJob("8172503", "Abuse Research Engineer", "Remote from the US",
				RemoteType.REMOTE, null, null, null, null, "https://stripe.com/jobs/search?gh_jid=8172503",
				"Who we are", Instant.parse("2026-09-09T14:52:09Z"), Instant.parse("2026-10-07T23:02:27Z"));
		given(this.greenhouseClient.fetchJobs("stripe")).willReturn(List.of(job));

		assertThat(this.mvc.get().uri("/dev/preview/stripe")).hasStatusOk()
			.bodyJson()
			.extractingPath("$[0].externalId")
			.isEqualTo("8172503");
	}

	@Test
	void unknownBoardIsProblem404() {
		given(this.greenhouseClient.fetchJobs("nope"))
			.willThrow(new BoardNotFoundException(AtsType.GREENHOUSE, "nope"));

		assertThat(this.mvc.get().uri("/dev/preview/nope")).hasStatus(HttpStatus.NOT_FOUND)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.extractingPath("$.boardToken")
			.isEqualTo("nope");
	}

}
