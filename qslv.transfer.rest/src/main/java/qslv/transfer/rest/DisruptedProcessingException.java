package qslv.transfer.rest;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import qslv.transfer.response.TransferFundsResponse;

public class DisruptedProcessingException extends ResponseStatusException {
	private static final long serialVersionUID = 1L;
	private TransferFundsResponse response;
	
	public DisruptedProcessingException(TransferFundsResponse response, HttpStatus status, String reason) {
		super(status, reason);
		this.response = response;
	}
	
	public DisruptedProcessingException(TransferFundsResponse response, HttpStatus status, String reason, Throwable cause) {
		super(status, reason, cause);
		this.response = response;
	}
	
	public TransferFundsResponse getResponse() {
		return response;
	}
}
