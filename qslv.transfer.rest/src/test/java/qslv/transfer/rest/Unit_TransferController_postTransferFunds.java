package qslv.transfer.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import qslv.common.TimedResponse;
import qslv.common.TraceableRequest;
import qslv.transaction.resource.TransactionResource;
import qslv.transfer.request.TransferFundsRequest;
import qslv.transfer.response.TransferFundsResponse;


@ExtendWith(MockitoExtension.class)
class Unit_TransferController_postTransferFunds {
	@Mock
	public TransferService transferService;
	public ConfigProperties config = new ConfigProperties();

	TransferController controller = new TransferController();

	@BeforeEach
	public void setup() {
		controller.setTransferService(transferService);
		controller.setConfig(config);
		config.setAitid("234234");
	}

	@Test
	void test_postTransferFunds_success() {
		//-- Prepare ------------------
		HashMap<String, String> headers = new HashMap<String, String>();
		headers.put(TraceableRequest.AIT_ID, "12345");
		headers.put(TraceableRequest.BUSINESS_TAXONOMY_ID, "7483495");
		headers.put(TraceableRequest.CORRELATION_ID, "273849273498273498");
		headers.put(TraceableRequest.ACCEPT_VERSION, TransferFundsRequest.Version1_0);

		//-------------------
		TransferFundsRequest request = new TransferFundsRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setFromAccountNumber("1234HHHH1234");
		request.setToAccountNumber("2738492734982");
		request.setTransactionAmount(27384L);
		request.setTransactionJsonMetaData("{}");

		//------------------
		TransferFundsResponse setupResponse = new TransferFundsResponse();
		setupResponse.setStatus(TransferFundsResponse.SUCCESS);
		TransactionResource trans = new TransactionResource();
		trans.setTransactionUuid(UUID.randomUUID());
		trans.setAccountNumber("123781923123");
		trans.setDebitCardNumber("126743812673981623");
		trans.setInsertTimestamp(new Timestamp(Instant.now().toEpochMilli()));
		trans.setReservationUuid(UUID.randomUUID());
		trans.setRequestUuid(UUID.randomUUID());
		trans.setRunningBalanceAmount(99999L);
		trans.setTransactionAmount(-2323L);
		trans.setTransactionMetaDataJson("{etc, etc}");
		trans.setTransactionTypeCode(TransactionResource.RESERVATION);
		setupResponse.setReservations(Collections.singletonList(trans));

		when(transferService.transferFunds(any(), any())).thenReturn(setupResponse);
		
		//- Execute --------------------------
		ResponseEntity<TimedResponse<TransferFundsResponse>> httpResponse = controller.postTransferFunds(headers, request);

		//- Verify --------------------------
		verify(transferService).transferFunds(any(), any());
		assertEquals (HttpStatus.OK, httpResponse.getStatusCode());
		assertTrue(httpResponse.hasBody());
		
		TimedResponse<TransferFundsResponse> ttr = httpResponse.getBody();
		assertEquals( TransferFundsResponse.SUCCESS, ttr.getPayload().getStatus());
		
		TransferFundsResponse tfr = ttr.getPayload();
		assertSame(setupResponse, tfr);
	}

	@Test
	void test_postTransferFunds_failure() {
		
		//-- Prepare ------------------
		HashMap<String, String> headers = new HashMap<String, String>();
		headers.put(TraceableRequest.AIT_ID, "12345");
		headers.put(TraceableRequest.BUSINESS_TAXONOMY_ID, "7483495");
		headers.put(TraceableRequest.CORRELATION_ID, "273849273498273498");
		headers.put(TraceableRequest.ACCEPT_VERSION, TransferFundsRequest.Version1_0);

		//-------------------
		TransferFundsRequest request = new TransferFundsRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setFromAccountNumber("1234HHHH1234");
		request.setToAccountNumber("2738492734982");
		request.setTransactionAmount(27384L);
		request.setTransactionJsonMetaData("{}");
		
		when(transferService.transferFunds(any(), any()))
			.thenThrow(new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "garbage"));

		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferFunds(headers, request);
		});
		assertEquals (HttpStatus.NOT_ACCEPTABLE, ex.getStatus());
	}
	
	@Test
	void test_postTransferFunds_kafkaFailes() {
		//-- Prepare ------------------
		HashMap<String, String> headers = new HashMap<String, String>();
		headers.put(TraceableRequest.AIT_ID, "12345");
		headers.put(TraceableRequest.BUSINESS_TAXONOMY_ID, "7483495");
		headers.put(TraceableRequest.CORRELATION_ID, "273849273498273498");
		headers.put(TraceableRequest.ACCEPT_VERSION, TransferFundsRequest.Version1_0);

		//-------------------
		TransferFundsRequest request = new TransferFundsRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setFromAccountNumber("1234HHHH1234");
		request.setToAccountNumber("2738492734982");
		request.setTransactionAmount(27384L);
		request.setTransactionJsonMetaData("{}");

		//------------------
		TransferFundsResponse setupResponse = new TransferFundsResponse();
		setupResponse.setStatus(TransferFundsResponse.SUCCESS);
		TransactionResource trans = new TransactionResource();
		trans.setTransactionUuid(UUID.randomUUID());
		trans.setAccountNumber("123781923123");
		trans.setDebitCardNumber("126743812673981623");
		trans.setInsertTimestamp(new Timestamp(Instant.now().toEpochMilli()));
		trans.setReservationUuid(UUID.randomUUID());
		trans.setRequestUuid(UUID.randomUUID());
		trans.setRunningBalanceAmount(99999L);
		trans.setTransactionAmount(-2323L);
		trans.setTransactionMetaDataJson("{etc, etc}");
		trans.setTransactionTypeCode(TransactionResource.RESERVATION);
		setupResponse.setReservations(Collections.singletonList(trans));

		DisruptedProcessingException tfpe = new DisruptedProcessingException(setupResponse, HttpStatus.INTERNAL_SERVER_ERROR, "msg");
		
		when(transferService.transferFunds(any(), any())).thenThrow(tfpe);
		
		//- Execute --------------------------
		ResponseEntity<TimedResponse<TransferFundsResponse>> httpResponse = controller.postTransferFunds(headers, request);

		//- Verify --------------------------
		verify(transferService).transferFunds(any(), any());
		assertEquals (HttpStatus.INTERNAL_SERVER_ERROR, httpResponse.getStatusCode());
		assertTrue(httpResponse.hasBody());
		assertSame( setupResponse, httpResponse.getBody().getPayload());
		
		TimedResponse<TransferFundsResponse> ttr = httpResponse.getBody();
		assertEquals( TransferFundsResponse.SUCCESS, ttr.getPayload().getStatus());
		
		TransferFundsResponse tfr = ttr.getPayload();
		assertSame(setupResponse, tfr);
	}
	
	@Test
	void testPostReserveFunds_validateInput() {

		HashMap<String, String> headers = new HashMap<String, String>();
		TransferFundsRequest request = new TransferFundsRequest();

		// --- No headers, no data
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferFunds(headers, request);
		});
		assertTrue (ex.getStatus() == HttpStatus.BAD_REQUEST);
		assertTrue (ex.getLocalizedMessage().contains("ait-id"));

		// --- add AIT_ID
		headers.put(TraceableRequest.AIT_ID, "12345");
		ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferFunds(headers, request);
		});
		assertTrue (ex.getStatus() == HttpStatus.BAD_REQUEST);
		assertTrue (ex.getLocalizedMessage().contains("business-taxonomy-id"));

		// --- add BUSINESS_TAXONOMY_ID
		headers.put(TraceableRequest.BUSINESS_TAXONOMY_ID, "7483495");
		ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferFunds(headers, request);
		});
		assertTrue (ex.getStatus() == HttpStatus.BAD_REQUEST);
		assertTrue (ex.getLocalizedMessage().contains("correlation-id"));

		// --- add CORRELATION_ID
		headers.put(TraceableRequest.CORRELATION_ID, "273849273498273498");
		ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferFunds(headers, request);
		});
		assertTrue (ex.getStatus() == HttpStatus.BAD_REQUEST);
		assertTrue (ex.getLocalizedMessage().contains("accept-version"));

		// --- add Version
		headers.put(TraceableRequest.ACCEPT_VERSION, "blah blah");
		ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferFunds(headers, request);
		});
		assertTrue (ex.getStatus() == HttpStatus.BAD_REQUEST);
		assertTrue (ex.getLocalizedMessage().contains("requestUuid"));

		// --- add Request UUID Number
		request.setRequestUuid(UUID.randomUUID());
		ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferFunds(headers, request);
		});
		assertTrue (ex.getStatus() == HttpStatus.BAD_REQUEST);
		assertTrue (ex.getLocalizedMessage().contains("FromAccountNumber"));

		// --- add From Account Card Number
		request.setFromAccountNumber("178239123");
		ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferFunds(headers, request);
		});
		assertTrue (ex.getStatus() == HttpStatus.BAD_REQUEST);
		assertTrue (ex.getLocalizedMessage().contains("ToAccountNumber"));

		// --- add To Account Card Number
		request.setToAccountNumber("178239123");
		ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferFunds(headers, request);
		});
		assertTrue (ex.getStatus() == HttpStatus.BAD_REQUEST);
		assertTrue (ex.getLocalizedMessage().contains("TransactionJsonMetaData"));

		// --- add JSON
		request.setTransactionJsonMetaData("{}");
		request.setTransactionAmount(0);
		ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferFunds(headers, request);
		});
		assertTrue (ex.getStatus() == HttpStatus.BAD_REQUEST);
		assertTrue (ex.getLocalizedMessage().contains("Transaction Amount"));

		// --- add Negative Amount
		request.setTransactionAmount(-7890);
		ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferFunds(headers, request);
		});
		assertTrue (ex.getStatus() == HttpStatus.BAD_REQUEST);
		assertTrue (ex.getLocalizedMessage().contains("Transaction Amount"));
		
		// --- add Amount
		request.setTransactionAmount(7890);
		ex = assertThrows(ResponseStatusException.class, () -> {
			controller.postTransferFunds(headers, request);
		});
		assertTrue (ex.getStatus() == HttpStatus.BAD_REQUEST);
		assertTrue (ex.getLocalizedMessage().contains("Invalid service version"));
		
		// --- add right version
		headers.put(TraceableRequest.ACCEPT_VERSION, TransferFundsRequest.Version1_0);

		// --- all clear
		TransferFundsResponse setupResponse = new TransferFundsResponse();
		setupResponse.setStatus(TransferFundsResponse.SUCCESS);
		when(transferService.transferFunds(any(), any())).thenReturn(setupResponse);
		
		//- Execute --------------------------
		ResponseEntity<TimedResponse<TransferFundsResponse>> httpResponse = controller.postTransferFunds(headers, request);
		assertTrue( httpResponse.hasBody() );
	}
}
