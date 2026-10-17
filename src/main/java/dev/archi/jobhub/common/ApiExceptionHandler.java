package dev.archi.jobhub.common;

import dev.archi.jobhub.ingest.BoardNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

/**
 * Maps exceptions to RFC 9457 problem+json responses.
 */
@RestControllerAdvice
class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler
	ProblemDetail boardNotFound(BoardNotFoundException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setTitle("Board not found");
		problem.setProperty("atsType", ex.atsType());
		problem.setProperty("boardToken", ex.boardToken());
		return problem;
	}

	/** The ATS itself failed (5xx, timeout, bad payload): not our client's fault. */
	@ExceptionHandler
	ProblemDetail upstreamFailure(RestClientException ex) {
		log.warn("Upstream ATS call failed", ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
				"Upstream job board request failed");
		problem.setTitle("Upstream failure");
		return problem;
	}

}
