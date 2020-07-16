package qslv.transfer.rest;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import qslv.common.TimedResponse;
import qslv.common.TraceableRequest;
import qslv.transfer.request.TransferFundsRequest;
import qslv.transfer.response.TransferFundsResponse;
import qslv.util.ServiceElapsedTimeSLI;
import qslv.util.LogRequestTracingData;

@RestController
public class TransferController {
	private static final Logger log = LoggerFactory.getLogger(TransferController.class);

	@Autowired
	public ConfigProperties config;
	@Autowired
	private TransferService transferService;

	public ConfigProperties getConfig() {
		return config;
	}
	public void setConfig(ConfigProperties config) {
		this.config = config;
	}
	public TransferService getTransferService() {
		return transferService;
	}
	public void setTransferService(TransferService transferService) {
		this.transferService = transferService;
	}
	
	@PostMapping("/TransferFunds")
	@LogRequestTracingData(value="POST/TransferFunds", ait = "33333")
	@ServiceElapsedTimeSLI(value="POST/TransferFunds", injectResponse = true, ait = "44444")
	public ResponseEntity<TimedResponse<TransferFundsResponse>> postTransferFunds(final @RequestHeader Map<String, String> headers,
			final @RequestBody TransferFundsRequest request) {
		
		validateHeaders(headers);
		validateTransferFundsRequest(request);
		if ( false == headers.get(TraceableRequest.ACCEPT_VERSION).equals(TransferFundsRequest.Version1_0)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid service version.");
		}

		HttpStatus responseStatus = HttpStatus.OK; 
		TransferFundsResponse response = null;
		try {
			response = transferService.transferFunds(headers, request);
		} catch (DisruptedProcessingException ex) {
			response = ex.getResponse();
			responseStatus = ex.getStatus();
		} catch (ResponseStatusException ex) {
			throw ex;
		} catch (RuntimeException ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "", ex);
		}
		
		return ResponseEntity
				.status(responseStatus)
				.body(new TimedResponse<TransferFundsResponse>(0, response));
	}
	
	private void validateTransferFundsRequest(TransferFundsRequest request) {
		log.debug("validateTransferFundsRequest ENTRY");
		if (request.getRequestUuid() == null) {
			log.error("controller.validateTransactionRequest, Malformed Request. Missing requestUuid");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing requestUuid");
		}
		if (request.getFromAccountNumber() == null || request.getFromAccountNumber().length() == 0) {
			log.error("controller.validateTransactionRequest, Malformed Request. Missing FromAccountNumber");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing FromAccountNumber");
		}
		if (request.getToAccountNumber() == null || request.getToAccountNumber().length() == 0) {
			log.error("controller.validateTransactionRequest, Malformed Request. Missing ToAccountNumber");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing ToAccountNumber");
		}
		if (request.getTransactionJsonMetaData() == null || request.getTransactionJsonMetaData().length() <= 1) {
			log.error("controller.validateTransactionRequest Malformed Request. Missing TransactionJsonMetaData");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing TransactionJsonMetaData");
		}	
		if (request.getTransactionAmount() <= 0) {
			log.error("controller.validateTransactionRequest Malformed Request. Transaction Amount must be greater than zero(0).");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction Amount must be greater than zero(0).");
		}
	}

	private void validateHeaders(Map<String, String> headerMap) {
		log.debug("validateHeaders ENTRY");

		if (headerMap.get(TraceableRequest.AIT_ID) == null) {
			log.error("controller.validateHeaders, Malformed Request. Missing header variable ait-id");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing header variable ait-id");
		}
		if (headerMap.get(TraceableRequest.BUSINESS_TAXONOMY_ID) == null) {
			log.error("controller.validateHeaders, Malformed Request. Missing header variable business-taxonomy-id");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing header variable business-taxonomy-id");
		}
		if (headerMap.get(TraceableRequest.CORRELATION_ID) == null) {
			log.error("controller.validateHeaders, Malformed Request. Missing header variable correlation-id");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing header variable correlation-id");
		}
		if (headerMap.get(TraceableRequest.ACCEPT_VERSION) == null) {
			log.error("controller.validateHeaders, Malformed Request. Missing header variable accept-version");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing header variable accept-version");
		}	
	}

}
