package dev.archi.jobhub.ingest.greenhouse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import dev.archi.jobhub.common.exceptions.AtsUnavailableException;
import dev.archi.jobhub.company.AtsType;
import dev.archi.jobhub.ingest.AtsClient;
import dev.archi.jobhub.ingest.NormalizedJob;
import dev.archi.jobhub.ingest.RemoteType;
import dev.archi.jobhub.ingest.exceptions.BoardNotFoundException;
import dev.archi.jobhub.ingest.greenhouse.GreenhouseJobsResponse.Job;
import dev.archi.jobhub.ingest.greenhouse.GreenhouseJobsResponse.JobDetail;
import dev.archi.jobhub.ingest.greenhouse.GreenhouseJobsResponse.PayInputRange;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Greenhouse Job Board API client.
 *
 * <p>Requests, relative to {@link GreenhouseProperties#baseUrl()}:
 * <ul>
 * <li>list: {@code GET /boards/{boardToken}/jobs?content=true}</li>
 * <li>detail: {@code GET /boards/{boardToken}/jobs/{jobId}?pay_transparency=true}</li>
 * </ul>
 * Rules from CLAUDE.md: timeouts are set on the RestClient (see GreenhouseConfig);
 * at most one retry; a failing detail call is logged and skipped, never fails the board.
 */
public class GreenhouseClient implements AtsClient {

	private static final Logger log = LoggerFactory.getLogger(GreenhouseClient.class);

	private final RestClient restClient;

	private final GreenhouseProperties properties;

	public GreenhouseClient(RestClient restClient, GreenhouseProperties properties) {
		this.restClient = restClient;
		this.properties = properties;
	}

	@Override
	public AtsType type() {
		return AtsType.GREENHOUSE;
	}

	@Override
	public List<NormalizedJob> fetchJobs(String boardToken) {
		if (boardToken == null || boardToken.isBlank()) {
			throw new IllegalArgumentException("Board token must not be blank");
		}
		List<Job> jobs = fetchListOneRetry(boardToken);

		if (!this.properties.fetchPay() || jobs.isEmpty()) {
			return jobs.stream().map((job) -> normalize(job, null)).toList();
		}
		return withPay(boardToken, jobs);
	}

	/**
	 * Retries once, and only on transient failures (5xx, timeouts, connection errors).
	 * A 4xx or an unreadable body would fail the same way twice, so it isn't retried.
	 */
	private List<Job> fetchListOneRetry(String boardToken) {
		try {
			return fetchList(boardToken);
		}
		catch (HttpServerErrorException | ResourceAccessException first) {
			log.warn("Greenhouse list failed for {}, retrying once: {}", boardToken, first.getMessage());
			try {
				return fetchList(boardToken);
			}
			catch (RestClientException second) {
				throw new AtsUnavailableException(AtsType.GREENHOUSE, boardToken, second);
			}
		}
		catch (RestClientException ex) {
			throw new AtsUnavailableException(AtsType.GREENHOUSE, boardToken, ex);
		}
	}

	/**
	 * @throws BoardNotFoundException on 404
	 * @throws RestClientException on any other HTTP or I/O failure
	 */
	private List<Job> fetchList(String boardToken) {
		GreenhouseJobsResponse response = this.restClient.get()
			.uri("/boards/{boardToken}/jobs?content=true", boardToken)
			.retrieve()
			.onStatus((status) -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (request, res) -> {
				throw new BoardNotFoundException(AtsType.GREENHOUSE, boardToken);
			})
			.body(GreenhouseJobsResponse.class);
		if (response == null || response.jobs() == null) {
			throw new AtsUnavailableException(AtsType.GREENHOUSE, boardToken);
		}
		return response.jobs();
	}

	private List<NormalizedJob> withPay(String boardToken, List<Job> jobs) {
		List<Callable<Optional<PayRange>>> tasks = jobs.stream()
			.<Callable<Optional<PayRange>>>map((job) -> () -> fetchPay(boardToken, job.id()))
			.toList();

		try (var pool = Executors.newFixedThreadPool(this.properties.payConcurrency())) {
			List<Future<Optional<PayRange>>> futures = pool.invokeAll(tasks);

			List<NormalizedJob> result = new ArrayList<>(jobs.size());
			for (int i = 0; i < jobs.size(); i++) {
				PayRange pay = futures.get(i).get().orElse(null);
				result.add(normalize(jobs.get(i), pay));
			}
			return result;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new AtsUnavailableException(AtsType.GREENHOUSE, boardToken);
		}
		catch (ExecutionException ex) {
			// fetchPay never throws, so this means a bug, not a network problem
			throw new IllegalStateException("Unexpected error fetching pay for " + boardToken, ex.getCause());
		}
	}

	/**
	 * Detail call for one job's pay. Empty when the job has no usable ranges, or when
	 * the call fails (logged, never thrown).
	 */
	Optional<PayRange> fetchPay(String boardToken, long jobId) {
		JobDetail detail;
		try {
			detail = this.restClient.get()
				.uri("/boards/{boardToken}/jobs/{jobId}?pay_transparency=true", boardToken, jobId)
				.retrieve()
				.body(JobDetail.class);
		}
		catch (RestClientException ex) {
			log.warn("Pay lookup failed for {} job {}: {}", boardToken, jobId, ex.getMessage());
			return Optional.empty();
		}
		if (detail == null || detail.payInputRanges() == null) {
			return Optional.empty();
		}

		List<PayInputRange> valid = detail.payInputRanges()
			.stream()
			.filter((range) -> range.currencyType() != null && range.minCents() >= 0
					&& range.maxCents() >= range.minCents())
			.toList();
		if (valid.isEmpty()) {
			return Optional.empty();
		}
		// widestRange needs one currency; keep the bands that match the first one.
		String currency = valid.getFirst().currencyType();
		List<PayInputRange> sameCurrency = valid.stream()
			.filter((range) -> currency.equals(range.currencyType()))
			.toList();
		if (sameCurrency.size() < valid.size()) {
			log.warn("Mixed currencies for {} job {}, using {} bands only", boardToken, jobId, currency);
		}
		return Optional.of(widestRange(sameCurrency));
	}

	NormalizedJob normalize(Job job, @Nullable PayRange pay) {
		String location = (job.location() != null) ? job.location().name() : null;
		return new NormalizedJob(
				String.valueOf(job.id()), // externalId as text: Lever uses UUIDs
				job.title(),
				location,
				classifyRemote(location),
				null, // employmentType: not on the Greenhouse board API
				(pay != null) ? pay.min() : null,
				(pay != null) ? pay.max() : null,
				(pay != null) ? pay.currency() : null,
				job.absoluteUrl(),
				htmlToText(job.content()),
				job.firstPublished(),
				job.updatedAt());
	}

	static RemoteType classifyRemote(@Nullable String location) {
		if (location == null || location.isBlank()) {
			return RemoteType.UNKNOWN;
		}
		String lower = location.toLowerCase(Locale.ROOT);
		if (lower.contains("remote")) {
			return RemoteType.REMOTE;
		}
		if (lower.contains("hybrid")) {
			return RemoteType.HYBRID;
		}
		return RemoteType.ONSITE;
	}

	static String htmlToText(@Nullable String escapedHtml) {
		if (escapedHtml == null || escapedHtml.isBlank()) {
			return "";
		}
		String html = Parser.unescapeEntities(escapedHtml, false); // &lt;p&gt; -> <p>
		return Jsoup.parse(html).text(); // <p>Hi</p> -> Hi
	}

	/**
	 * @param ranges non-empty, all in the same currency
	 * @throws IllegalArgumentException if empty, mixed-currency, or too large for an int
	 */
	static PayRange widestRange(List<PayInputRange> ranges) {
		if (ranges.isEmpty()) {
			throw new IllegalArgumentException("No pay ranges");
		}
		String currency = ranges.getFirst().currencyType();
		if (ranges.stream().anyMatch((range) -> !Objects.equals(range.currencyType(), currency))) {
			throw new IllegalArgumentException("Pay ranges mix currencies");
		}
		long minCents = ranges.stream().mapToLong(PayInputRange::minCents).min().getAsLong();
		long maxCents = ranges.stream().mapToLong(PayInputRange::maxCents).max().getAsLong();
		return new PayRange(Math.toIntExact(minCents / 100), Math.toIntExact(maxCents / 100), currency);
	}

}
