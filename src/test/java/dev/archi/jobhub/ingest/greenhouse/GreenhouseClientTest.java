package dev.archi.jobhub.ingest.greenhouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.between;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import dev.archi.jobhub.ingest.BoardNotFoundException;
import dev.archi.jobhub.ingest.NormalizedJob;
import dev.archi.jobhub.ingest.RemoteType;
import dev.archi.jobhub.ingest.greenhouse.GreenhouseJobsResponse.PayInputRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Pins the behavior of {@link GreenhouseClient}. No real network: MockRestServiceServer
 * intercepts the RestClient and replays fixtures from src/test/resources/greenhouse/.
 */
class GreenhouseClientTest {

	private static final String BASE = "https://boards-api.greenhouse.io/v1";

	private static final String LIST_URL = BASE + "/boards/stripe/jobs?content=true";

	private static final long REMOTE_JOB = 8172503L;

	private static final long HYBRID_JOB = 8241288L;

	private static final long ONSITE_JOB = 8172508L;

	private MockRestServiceServer server;

	private GreenhouseClient client(boolean fetchPay) {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
		// Pay calls run concurrently, so their arrival order isn't deterministic.
		this.server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
		GreenhouseProperties properties = new GreenhouseProperties(URI.create(BASE), Duration.ofSeconds(2),
				Duration.ofSeconds(10), fetchPay, 4);
		return new GreenhouseClient(builder.build(), properties);
	}

	private void expectList() {
		this.server.expect(once(), requestTo(LIST_URL))
			.andExpect(method(GET))
			.andRespond(withSuccess(new ClassPathResource("greenhouse/stripe-jobs.json"), APPLICATION_JSON));
	}

	private void expectDetailWithPay(long jobId) {
		this.server.expect(once(), requestTo(detailUrl(jobId)))
			.andRespond(withSuccess(new ClassPathResource("greenhouse/job-detail-with-pay.json"), APPLICATION_JSON));
	}

	private void expectDetailWithoutPay(long jobId) {
		this.server.expect(once(), requestTo(detailUrl(jobId)))
			.andRespond(withSuccess("{\"id\":" + jobId + ",\"pay_input_ranges\":[]}", APPLICATION_JSON));
	}

	private static String detailUrl(long jobId) {
		return BASE + "/boards/stripe/jobs/" + jobId + "?pay_transparency=true";
	}

	// --- fetchJobs: list call + normalize -------------------------------------------

	@Test
	void fetchJobsMapsEveryJobInListOrder() {
		GreenhouseClient client = client(false);
		expectList();

		List<NormalizedJob> jobs = client.fetchJobs("stripe");

		assertThat(jobs).extracting(NormalizedJob::externalId)
			.containsExactly(String.valueOf(REMOTE_JOB), String.valueOf(HYBRID_JOB), String.valueOf(ONSITE_JOB));
		NormalizedJob first = jobs.getFirst();
		assertThat(first.title()).isEqualTo("Abuse Research Engineer");
		assertThat(first.location()).isEqualTo("Remote from the US");
		assertThat(first.url()).isEqualTo("https://stripe.com/jobs/search?gh_jid=8172503");
		assertThat(first.postedAt()).isEqualTo(Instant.parse("2026-09-09T14:52:09Z"));
		assertThat(first.updatedAt()).isEqualTo(Instant.parse("2026-10-07T23:02:27Z"));
		assertThat(first.employmentType()).isNull();
		assertThat(jobs).extracting(NormalizedJob::remoteType)
			.containsExactly(RemoteType.REMOTE, RemoteType.HYBRID, RemoteType.ONSITE);
		this.server.verify();
	}

	@Test
	void fetchJobsReturnsPlainTextDescriptions() {
		GreenhouseClient client = client(false);
		expectList();

		String description = client.fetchJobs("stripe").getFirst().descriptionText();

		assertThat(description).contains("Who we are", "financial infrastructure platform", "R&D",
				"Ship production models");
		assertThat(description).doesNotContain("<", "&lt;", "&amp;", "&quot;");
	}

	// --- pay fan-out ----------------------------------------------------------------

	@Test
	void fetchJobsWithPayOnAttachesWidestRangeInWholeUnits() {
		GreenhouseClient client = client(true);
		expectList();
		expectDetailWithPay(REMOTE_JOB);
		expectDetailWithoutPay(HYBRID_JOB);
		expectDetailWithoutPay(ONSITE_JOB);

		List<NormalizedJob> jobs = client.fetchJobs("stripe");

		NormalizedJob withPay = jobs.getFirst();
		assertThat(withPay.payMin()).isEqualTo(150_000);
		assertThat(withPay.payMax()).isEqualTo(260_000);
		assertThat(withPay.payCurrency()).isEqualTo("USD");
		assertThat(jobs.subList(1, 3)).allSatisfy((job) -> {
			assertThat(job.payMin()).isNull();
			assertThat(job.payMax()).isNull();
			assertThat(job.payCurrency()).isNull();
		});
		this.server.verify();
	}

	@Test
	void fetchJobsWithPayOffMakesNoDetailCalls() {
		GreenhouseClient client = client(false);
		expectList();

		List<NormalizedJob> jobs = client.fetchJobs("stripe");

		assertThat(jobs).hasSize(3).allSatisfy((job) -> assertThat(job.payMin()).isNull());
		// Any unexpected detail request would already have failed the call above.
		this.server.verify();
	}

	@Test
	void failingDetailCallLeavesThatJobsPayNullAndKeepsTheRest() {
		GreenhouseClient client = client(true);
		expectList();
		// between(1, 2): you may retry once, but never more.
		this.server.expect(between(1, 2), requestTo(detailUrl(REMOTE_JOB))).andRespond(withServerError());
		expectDetailWithoutPay(HYBRID_JOB);
		expectDetailWithoutPay(ONSITE_JOB);

		List<NormalizedJob> jobs = client.fetchJobs("stripe");

		assertThat(jobs).hasSize(3);
		assertThat(jobs.getFirst().payMin()).isNull();
		this.server.verify();
	}

	// --- errors ---------------------------------------------------------------------

	@Test
	void unknownBoardThrowsBoardNotFoundWithoutRetrying() {
		GreenhouseClient client = client(true);
		// once(): a 404 is a definitive answer, so it must not be retried.
		this.server.expect(once(), requestTo(BASE + "/boards/nope/jobs?content=true"))
			.andRespond(withResourceNotFound().body("{\"status\":404,\"error\":\"Job not found\"}")
				.contentType(APPLICATION_JSON));

		assertThatThrownBy(() -> client.fetchJobs("nope")).isInstanceOf(BoardNotFoundException.class)
			.hasMessageContaining("nope");
		this.server.verify();
	}

	// --- pure helpers ---------------------------------------------------------------

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			Remote from the US              | REMOTE
			Atlanta, Chicago, US-Remote     | REMOTE
			REMOTE - Canada                 | REMOTE
			US - Hybrid                     | HYBRID
			Hybrid - New York               | HYBRID
			Dublin                          | ONSITE
			CHI, SF,NYC, SEA                | ONSITE
			""")
	void classifyRemoteUsesKeywords(String location, RemoteType expected) {
		assertThat(GreenhouseClient.classifyRemote(location)).isEqualTo(expected);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   " })
	void classifyRemoteIsUnknownWithoutLocation(String location) {
		assertThat(GreenhouseClient.classifyRemote(location)).isEqualTo(RemoteType.UNKNOWN);
	}

	@Test
	void htmlToTextHandlesNull() {
		assertThat(GreenhouseClient.htmlToText(null)).isEmpty();
	}

	@Test
	void widestRangeTakesLowestMinAndHighestMax() {
		List<PayInputRange> ranges = List.of(new PayInputRange(18_000_000, 26_000_000, "USD", "SF / NYC"),
				new PayInputRange(15_000_000, 22_000_000, "USD", "US Remote"));

		assertThat(GreenhouseClient.widestRange(ranges)).isEqualTo(new PayRange(150_000, 260_000, "USD"));
	}

}
