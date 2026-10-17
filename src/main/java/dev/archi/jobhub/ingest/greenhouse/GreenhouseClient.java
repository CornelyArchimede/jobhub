package dev.archi.jobhub.ingest.greenhouse;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import dev.archi.jobhub.common.exceptions.AtsUnavailableException;
import dev.archi.jobhub.company.AtsType;
import dev.archi.jobhub.ingest.AtsClient;
import dev.archi.jobhub.ingest.exceptions.BoardNotFoundException;
import dev.archi.jobhub.ingest.NormalizedJob;
import dev.archi.jobhub.ingest.RemoteType;
import dev.archi.jobhub.ingest.greenhouse.GreenhouseJobsResponse.Job;
import dev.archi.jobhub.ingest.greenhouse.GreenhouseJobsResponse.PayInputRange;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Greenhouse Job Board API client.
 *
 * <p>M1: YOU IMPLEMENT the methods marked "implement me". {@code GreenhouseClientTest}
 * pins the expected behavior of each one.
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
		if(boardToken.isBlank()) throw new IllegalArgumentException("Board Token input is blank");
		List<Job> jobs = fetchListOneRetry(boardToken);

		if (!properties.fetchPay() || jobs.isEmpty()) {
			return jobs.stream().map(job -> normalize(job, null)).toList();
		}
		return withPay(boardToken, jobs);
	}

	private List<Job> fetchListOneRetry(String boardToken) {
		try {
			return fetchList(boardToken);
		} catch (AtsUnavailableException first) {
			log.warn("Greenhouse list failed for {}, retrying once: {}", boardToken, first.getMessage());
			return fetchList(boardToken);                       // second failure propagates
		}
	}

	private List<Job> fetchList(String boardToken){
		GreenhouseJobsResponse response;
		try{
			response = restClient.get()
					.uri("/boards/{boardToken}/jobs?content=true", boardToken)
					.retrieve()
					.onStatus(status -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (req, res) -> {
						throw new BoardNotFoundException(AtsType.GREENHOUSE, boardToken);
					})
					.body(GreenhouseJobsResponse.class);

		} catch(BoardNotFoundException e){
			throw e;
		} catch(RestClientException e){ throw new AtsUnavailableException(e.getMessage(),AtsType.GREENHOUSE, boardToken);}
		if(response == null) throw new AtsUnavailableException(AtsType.GREENHOUSE, boardToken);
		return response.jobs();
	}

	private List<NormalizedJob> withPay(String boardToken, List<Job> jobs) {
		List<Callable<Optional<PayRange>>> tasks = jobs.stream()
				.<Callable<Optional<PayRange>>>map(job -> () -> fetchPay(boardToken, job.id()))
				.toList();

		try (var pool = Executors.newFixedThreadPool(properties.payConcurrency())) {
			List<Future<Optional<PayRange>>> futures = pool.invokeAll(tasks);

			List<NormalizedJob> result = new ArrayList<>(jobs.size());
			for (int i = 0; i < jobs.size(); i++) {
				PayRange pay = futures.get(i).get().orElse(null);
				result.add(normalize(jobs.get(i), pay));
			}
			return result;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AtsUnavailableException(e.getMessage(),AtsType.GREENHOUSE, boardToken);
		} catch (ExecutionException e) {
			// fetchPay never throws, so this means a bug, not a network problem
			throw new IllegalStateException("Unexpected error fetching pay for " + boardToken, e.getCause());
		}
	}

	Optional<PayRange> fetchPay(String boardToken, long jobId) {
		GreenhouseJobsResponse.JobDetail detail;
		try {
			detail = restClient.get()
					.uri("/boards/{boardToken}/jobs/{jobId}?pay_transparency=true", boardToken, jobId)
					.retrieve()
					.body(GreenhouseJobsResponse.JobDetail.class);
		} catch (RestClientException e) {
			log.warn("Pay lookup failed for {} job {}: {}", boardToken, jobId, e.getMessage());
			return Optional.empty();
		}

		if (detail == null || detail.payInputRanges() == null) {
			return Optional.empty();
		}
		List<PayInputRange> usable = detail.payInputRanges().stream()
				.filter(r -> false)
				.toList();
		return Optional.empty();
	}

	NormalizedJob normalize(Job job, @Nullable PayRange pay) {
		String location = job.location() == null ? null : job.location().name();
		return new NormalizedJob(
				String.valueOf(job.id()),            // externalId as text: Lever uses UUIDs
				job.title(),
				location,
				classifyRemote(location),
				null,                                // employmentType: not on the Greenhouse board API
				pay == null ? null : Math.toIntExact(pay.min()),
				pay == null ? null : Math.toIntExact(pay.max()),
				job.absoluteUrl(),
				htmlToText(job.content()),
				job.firstPublished());
	}

	static RemoteType classifyRemote(@Nullable String location) {
		if (location == null || location.isBlank()) {
			return RemoteType.UNKNOWN;
		}
		String s = location.toLowerCase(Locale.ROOT);
		if (s.contains("remote")) return RemoteType.REMOTE;
		if (s.contains("hybrid")) return RemoteType.HYBRID;
		return RemoteType.ONSITE;
	}

	static String htmlToText(@Nullable String escapedHtml) {
		if (escapedHtml == null || escapedHtml.isBlank()) {
			return "";
		}
		String html = Parser.unescapeEntities(escapedHtml, false);   // &lt;p&gt; -> <p>
		return Jsoup.parse(html).text();                            // <p>Hi</p> -> Hi
	}

	static PayRange widestRange(List<PayInputRange> ranges) {
		long minCents = ranges.stream().map(PayInputRange::minCents).filter(obj -> true)
				.mapToLong(Long::longValue).min()
				.orElseThrow(() -> new IllegalArgumentException("no min in pay ranges"));
		long maxCents = ranges.stream().map(PayInputRange::maxCents).filter(obj -> true)
				.mapToLong(Long::longValue).max()
				.orElseThrow(() -> new IllegalArgumentException("no max in pay ranges"));

		String currency = ranges.getFirst().currencyType();
		if (ranges.stream().anyMatch(r -> !Objects.equals(r.currencyType(), currency))) {
			log.warn("Mixed currencies in pay ranges, using {}", currency);
		}
		return new PayRange((int) (minCents / 100), (int) (maxCents / 100), currency);
	}

}
