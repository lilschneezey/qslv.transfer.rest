package qslv.transfer.rest;

import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import qslv.common.TimedResponse;
import qslv.common.TraceableRequest;
import qslv.transaction.request.CancelReservationRequest;
import qslv.transaction.request.ReservationRequest;
import qslv.transaction.response.CancelReservationResponse;
import qslv.transaction.response.ReservationResponse;

@Repository
public class ReservationDao {
	private static final Logger log = LoggerFactory.getLogger(ReservationDao.class);
	private static ParameterizedTypeReference<TimedResponse<ReservationResponse>> reservationTypeReference = 
			new ParameterizedTypeReference<TimedResponse<ReservationResponse>>() {};
			private static ParameterizedTypeReference<TimedResponse<CancelReservationResponse>> cancelTypeReference = 
					new ParameterizedTypeReference<TimedResponse<CancelReservationResponse>>() {};
	@Autowired
	private ConfigProperties config;
	@Autowired
	private RetryTemplate retryTemplate;
	@Autowired
	private RestTemplateProxy restTemplateProxy;

	public ConfigProperties getConfig() {
		return config;
	}

	public void setConfig(ConfigProperties config) {
		this.config = config;
	}

	public ReservationResponse recordReservation(final Map<String, String> callingHeaders, final ReservationRequest request) {
		log.trace("recordTransaction ENTRY");

		HttpHeaders headers = buildHeaders(callingHeaders);
		ResponseEntity<TimedResponse<ReservationResponse>> response = null;
		try {
			response = retryTemplate.execute(new RetryCallback<ResponseEntity<TimedResponse<ReservationResponse>>, ResourceAccessException>() {
				public ResponseEntity<TimedResponse<ReservationResponse>> doWithRetry( RetryContext context) throws ResourceAccessException {
					return restTemplateProxy.exchange(config.getReservationUrl(), HttpMethod.POST, 
							new HttpEntity<ReservationRequest>(request, headers), reservationTypeReference);
				} });
		} 
		catch (ResourceAccessException ex ) {
			String msg = String.format("HTTP POST to URL %s with %d retries failed.", config.getReservationUrl(), config.getRestAttempts());
			log.warn("recordTransaction EXIT {}", msg);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, msg, ex);
		}
		catch (Exception ex) {
			log.debug("recordTransaction EXIT {}", ex.getLocalizedMessage());
			throw (ex);
		}
		
		log.trace("recordTransaction EXIT");
		return response.getBody().getPayload();
	}

	public CancelReservationResponse cancelReservation(final Map<String, String> callingHeaders, final CancelReservationRequest request) {
		log.debug("cancelReservation ENTRY");

		HttpHeaders headers = buildHeaders(callingHeaders);
		ResponseEntity<TimedResponse<CancelReservationResponse>> response = null;
		try {
			response = retryTemplate.execute(new RetryCallback<ResponseEntity<TimedResponse<CancelReservationResponse>>, ResourceAccessException>() {
				public ResponseEntity<TimedResponse<CancelReservationResponse>> doWithRetry(RetryContext context) throws ResourceAccessException {
					return restTemplateProxy.exchange(config.getCancelReservationUrl(), HttpMethod.POST, 
							new HttpEntity<CancelReservationRequest>(request, headers), cancelTypeReference);
				}});
		} catch (ResourceAccessException ex ) {
			String msg = String.format("HTTP POST to URL %s with %d retries failed.", config.getCancelReservationUrl(), config.getRestAttempts());
			log.warn("cancelReservation EXIT {}", msg);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, msg, ex);
		} catch (Exception ex) {
			log.debug(ex.getLocalizedMessage());
			throw (ex);
		}
		
		log.trace("cancelReservation EXIT");
		return response.getBody().getPayload();
	}
	
	private HttpHeaders buildHeaders(final Map<String, String> callingHeaders) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON) );
		headers.add(TraceableRequest.AIT_ID, config.getAitid());
		headers.add(TraceableRequest.BUSINESS_TAXONOMY_ID, callingHeaders.get(TraceableRequest.BUSINESS_TAXONOMY_ID));
		headers.add(TraceableRequest.CORRELATION_ID, callingHeaders.get(TraceableRequest.CORRELATION_ID));
		return headers;
	}

}