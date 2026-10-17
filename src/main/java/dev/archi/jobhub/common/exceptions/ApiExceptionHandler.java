package dev.archi.jobhub.common.exceptions;

import dev.archi.jobhub.ingest.exceptions.BoardNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps exceptions to RFC 9457 problem+json responses.
 *
 * <p>Extends ResponseEntityExceptionHandler so Spring MVC's own exceptions (unknown
 * path, wrong method, bad parameter) keep their 404/405/400 instead of hitting the
 * catch-all below.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler
	ProblemDetail boardNotFound(BoardNotFoundException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setTitle("Board not found");
		problem.setProperty("atsType", ex.atsType());
		problem.setProperty("boardToken", ex.boardToken());
		return problem;
	}

	/** The ATS failed (5xx, timeout, bad payload): their problem, not the caller's. */
	@ExceptionHandler
	ProblemDetail upstreamFailure(AtsUnavailableException ex) {
		log.warn("Upstream ATS call failed: {}", ex.getMessage(), ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
		problem.setTitle("Upstream failure");
		problem.setProperty("atsType", ex.atsType());
		problem.setProperty("boardToken", ex.boardToken());
		return problem;
	}

	/** Our own error with no specific mapping yet. Message is ours, so it's safe to show. */
	@ExceptionHandler
	ProblemDetail jobHubError(JobHubException ex) {
		log.error("Unmapped JobHubException", ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
		problem.setTitle("Job Hub error");
		return problem;
	}

	/** Anything else is a bug. Log everything, return nothing internal. */
	@ExceptionHandler
	ProblemDetail unexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
		problem.setTitle("Internal error");
		return problem;
	}

}
