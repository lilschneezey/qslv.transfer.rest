package qslv.transfer.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import qslv.data.Account;
import qslv.transaction.request.CancelReservationRequest;
import qslv.transaction.request.ReservationRequest;
import qslv.transaction.resource.TransactionResource;
import qslv.transaction.response.CancelReservationResponse;
import qslv.transaction.response.ReservationResponse;
import qslv.transfer.request.TransferFulfillmentMessage;
import qslv.transfer.request.TransferFundsRequest;
import qslv.transfer.response.TransferFundsResponse;

@ExtendWith(MockitoExtension.class)
class Unit_TransferService_transferFunds {
	@Mock
	private JdbcDao jdbcDao;
	@Mock
	private KafkaDao kafkaDao;
	@Mock
	private ReservationDao reservationDao;

	TransferService service = new TransferService();

	@BeforeEach
	public void setup() {
		service.setJdbcDao(jdbcDao);
		service.setKafkaDao(kafkaDao);
		service.setReservationDao(reservationDao);
	}

	@Test
	void test_transferFunds_success() {

		HashMap<String, String> headers = new HashMap<String, String>();

		TransferFundsRequest request = new TransferFundsRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setFromAccountNumber("12341234");
		request.setToAccountNumber("1334124");
		request.setTransactionAmount(-2345L);
		request.setTransactionJsonMetaData("{}");

		// --------------
		Account acct1 = new Account();
		acct1.setAccountLifeCycleStatus("EF");
		acct1.setAccountNumber(request.getFromAccountNumber());
		Account acct2 = new Account();
		acct2.setAccountLifeCycleStatus("EF");
		acct2.setAccountNumber(request.getToAccountNumber());
		when(jdbcDao.getAccount(anyString())).thenReturn(acct1).thenReturn(acct2);

		// --------------
		ReservationResponse setupResponse = new ReservationResponse(ReservationResponse.SUCCESS, new TransactionResource());
		setupResponse.getResource().setTransactionUuid(UUID.randomUUID());
		setupResponse.getResource().setAccountNumber(request.getFromAccountNumber());
		setupResponse.getResource().setDebitCardNumber(null);
		setupResponse.getResource().setInsertTimestamp(new Timestamp(Instant.now().toEpochMilli()));
		setupResponse.getResource().setReservationUuid(null);
		setupResponse.getResource().setRequestUuid(request.getRequestUuid());
		setupResponse.getResource().setRunningBalanceAmount(99999L);
		setupResponse.getResource().setTransactionAmount(request.getTransactionAmount());
		setupResponse.getResource().setTransactionMetaDataJson(request.getTransactionJsonMetaData());
		setupResponse.getResource().setTransactionTypeCode(TransactionResource.RESERVATION);
		when(reservationDao.recordReservation(any(), any(ReservationRequest.class))).thenReturn(setupResponse);

		// ---------------
		doNothing().when(kafkaDao).produceTransferMessage(any(), any(TransferFulfillmentMessage.class));

		// ---------------
		TransferFundsResponse response = service.transferFunds(headers, request);

		verify(kafkaDao).produceTransferMessage(any(), any(TransferFulfillmentMessage.class));
		verify(jdbcDao, times(2)).getAccount(anyString());
		ArgumentCaptor<ReservationRequest> trCaptor = ArgumentCaptor.forClass(ReservationRequest.class);
		verify(reservationDao).recordReservation(any(), trCaptor.capture());
		
		assertEquals(request.getFromAccountNumber(),trCaptor.getValue().getAccountNumber());
		assertEquals(request.getRequestUuid(),trCaptor.getValue().getRequestUuid());
		assertEquals((0-request.getTransactionAmount()),trCaptor.getValue().getTransactionAmount());
		assertEquals(request.getTransactionJsonMetaData(),trCaptor.getValue().getTransactionMetaDataJson());

		assertEquals(TransferFundsResponse.SUCCESS, response.getStatus());
		assertEquals(request.getFromAccountNumber(), response.getReservations().get(0).getAccountNumber());
		assertEquals(request.getFromAccountNumber(), response.getFulfillmentMessage().getFromAccountNumber());
		assertEquals(request.getToAccountNumber(), response.getFulfillmentMessage().getToAccountNumber());
		assertEquals(request.getTransactionAmount(), response.getFulfillmentMessage().getTransactionAmount());
		assertEquals(request.getTransactionJsonMetaData(),
				response.getFulfillmentMessage().getTransactionMetaDataJson());
		assertEquals(setupResponse.getResource().getTransactionUuid(),
				response.getFulfillmentMessage().getReservationUuid());

	}

	@Test
	void test_transferFunds_badFromAccount() {

		HashMap<String, String> headers = new HashMap<String, String>();

		TransferFundsRequest request = new TransferFundsRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setFromAccountNumber("12341234");
		request.setToAccountNumber("1334124");
		request.setTransactionAmount(-2345L);
		request.setTransactionJsonMetaData("{}");

		// --------------
		Account acct1 = new Account();
		acct1.setAccountLifeCycleStatus("CL");
		acct1.setAccountNumber(request.getFromAccountNumber());
		when(jdbcDao.getAccount(anyString())).thenReturn(acct1);

		// ---------------
		assertThrows(ResponseStatusException.class, () -> {
			service.transferFunds(headers, request);
		});

		verify(jdbcDao, times(1)).getAccount(anyString());
	}

	@Test
	void test_transferFunds_badToAccount() {

		HashMap<String, String> headers = new HashMap<String, String>();

		TransferFundsRequest request = new TransferFundsRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setFromAccountNumber("12341234");
		request.setToAccountNumber("1334124");
		request.setTransactionAmount(-2345L);
		request.setTransactionJsonMetaData("{}");

		// --------------
		Account acct1 = new Account();
		acct1.setAccountLifeCycleStatus("EF");
		acct1.setAccountNumber(request.getFromAccountNumber());
		Account acct2 = new Account();
		acct2.setAccountLifeCycleStatus("CL");
		acct2.setAccountNumber(request.getToAccountNumber());
		when(jdbcDao.getAccount(anyString())).thenReturn(acct1).thenReturn(acct2);

		// --------------
		// ---------------
		assertThrows(ResponseStatusException.class, () -> {
			service.transferFunds(headers, request);
		});

		verify(jdbcDao, times(2)).getAccount(anyString());
	}

	@Test
	void test_transferFunds_insufficientFunds() {
		HashMap<String, String> headers = new HashMap<String, String>();

		TransferFundsRequest request = new TransferFundsRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setFromAccountNumber("12341234");
		request.setToAccountNumber("1334124");
		request.setTransactionAmount(-2345L);
		request.setTransactionJsonMetaData("{}");

		// --------------
		Account acct1 = new Account();
		acct1.setAccountLifeCycleStatus("EF");
		acct1.setAccountNumber(request.getFromAccountNumber());
		Account acct2 = new Account();
		acct2.setAccountLifeCycleStatus("EF");
		acct2.setAccountNumber(request.getToAccountNumber());
		when(jdbcDao.getAccount(anyString())).thenReturn(acct1).thenReturn(acct2);

		// --------------		
		ReservationResponse setupResponse = new ReservationResponse(ReservationResponse.INSUFFICIENT_FUNDS, new TransactionResource());
		setupResponse.getResource().setTransactionUuid(UUID.randomUUID());
		setupResponse.getResource().setAccountNumber(request.getFromAccountNumber());
		setupResponse.getResource().setDebitCardNumber(null);
		setupResponse.getResource().setInsertTimestamp(new Timestamp(Instant.now().toEpochMilli()));
		setupResponse.getResource().setReservationUuid(null);
		setupResponse.getResource().setRequestUuid(request.getRequestUuid());
		setupResponse.getResource().setRunningBalanceAmount(99999L);
		setupResponse.getResource().setTransactionAmount(request.getTransactionAmount());
		setupResponse.getResource().setTransactionMetaDataJson(request.getTransactionJsonMetaData());
		setupResponse.getResource().setTransactionTypeCode(TransactionResource.REJECTED_TRANSACTION);
		when(reservationDao.recordReservation(any(), any(ReservationRequest.class))).thenReturn(setupResponse);

		// ---------------
		TransferFundsResponse response = service.transferFunds(headers, request);

		verify(jdbcDao, times(2)).getAccount(anyString());
		verify(reservationDao).recordReservation(any(), any(ReservationRequest.class));

		assertEquals(TransferFundsResponse.INSUFFICIENT_FUNDS, response.getStatus());
		assertEquals(request.getFromAccountNumber(), response.getReservations().get(0).getAccountNumber());
	}

	@Test
	void test_transferFunds_reservationFailure() {
		HashMap<String, String> headers = new HashMap<String, String>();

		TransferFundsRequest request = new TransferFundsRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setFromAccountNumber("12341234");
		request.setToAccountNumber("1334124");
		request.setTransactionAmount(-2345L);
		request.setTransactionJsonMetaData("{}");

		// --------------
		Account acct1 = new Account();
		acct1.setAccountLifeCycleStatus("EF");
		acct1.setAccountNumber(request.getFromAccountNumber());
		Account acct2 = new Account();
		acct2.setAccountLifeCycleStatus("EF");
		acct2.setAccountNumber(request.getToAccountNumber());
		when(jdbcDao.getAccount(anyString())).thenReturn(acct1).thenReturn(acct2);

		// --------------
		when(reservationDao.recordReservation(any(), any(ReservationRequest.class)))
			.thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "test msg"));

		// ---------------
		assertThrows(ResponseStatusException.class, () -> {
			service.transferFunds(headers, request);
		});
		
		verify(jdbcDao, times(2)).getAccount(anyString());
		verify(reservationDao).recordReservation(any(), any(ReservationRequest.class));
	}

	@Test
	void test_transferFunds_kafkaFailure() {

		HashMap<String, String> headers = new HashMap<String, String>();

		TransferFundsRequest request = new TransferFundsRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setFromAccountNumber("12341234");
		request.setToAccountNumber("1334124");
		request.setTransactionAmount(-2345L);
		request.setTransactionJsonMetaData("{}");

		// --------------
		Account acct1 = new Account();
		acct1.setAccountLifeCycleStatus("EF");
		acct1.setAccountNumber(request.getFromAccountNumber());
		Account acct2 = new Account();
		acct2.setAccountLifeCycleStatus("EF");
		acct2.setAccountNumber(request.getToAccountNumber());
		when(jdbcDao.getAccount(anyString())).thenReturn(acct1).thenReturn(acct2);

		// --------------
		ReservationResponse setupResponse = new ReservationResponse(ReservationResponse.SUCCESS, new TransactionResource());
		setupResponse.getResource().setTransactionUuid(UUID.randomUUID());
		setupResponse.getResource().setAccountNumber(request.getFromAccountNumber());
		setupResponse.getResource().setDebitCardNumber(null);
		setupResponse.getResource().setInsertTimestamp(new Timestamp(Instant.now().toEpochMilli()));
		setupResponse.getResource().setReservationUuid(null);
		setupResponse.getResource().setRequestUuid(request.getRequestUuid());
		setupResponse.getResource().setRunningBalanceAmount(99999L);
		setupResponse.getResource().setTransactionAmount(request.getTransactionAmount());
		setupResponse.getResource().setTransactionMetaDataJson(request.getTransactionJsonMetaData());
		setupResponse.getResource().setTransactionTypeCode(TransactionResource.RESERVATION);
		when(reservationDao.recordReservation(any(), any(ReservationRequest.class))).thenReturn(setupResponse);

		// ---------------
		doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "test msg"))
			.when(kafkaDao).produceTransferMessage(any(), any(TransferFulfillmentMessage.class));

		// --------------
		CancelReservationResponse cancelResponse = new CancelReservationResponse(CancelReservationResponse.SUCCESS, new TransactionResource());
		cancelResponse.getResource().setTransactionUuid(UUID.randomUUID());
		cancelResponse.getResource().setAccountNumber(request.getFromAccountNumber());
		cancelResponse.getResource().setDebitCardNumber(null);
		cancelResponse.getResource().setInsertTimestamp(new Timestamp(Instant.now().toEpochMilli()));
		cancelResponse.getResource().setReservationUuid(setupResponse.getResource().getReservationUuid());
		cancelResponse.getResource().setRequestUuid(UUID.randomUUID());
		cancelResponse.getResource().setRunningBalanceAmount(99999L);
		cancelResponse.getResource().setTransactionAmount(request.getTransactionAmount());
		cancelResponse.getResource().setTransactionMetaDataJson(request.getTransactionJsonMetaData());
		cancelResponse.getResource().setTransactionTypeCode(TransactionResource.RESERVATION_CANCEL);
		when(reservationDao.cancelReservation(any(), any(CancelReservationRequest.class))).thenReturn(cancelResponse);
		
		// ---------------
		DisruptedProcessingException ex = assertThrows(DisruptedProcessingException.class, () -> {
			service.transferFunds(headers, request);
		});
		verify(kafkaDao).produceTransferMessage(any(), any(TransferFulfillmentMessage.class));
		verify(jdbcDao, times(2)).getAccount(anyString());
		verify(reservationDao).recordReservation(any(), any(ReservationRequest.class));
		verify(reservationDao).cancelReservation(any(), any(CancelReservationRequest.class));

		assertTrue( ex.getCause() instanceof ResponseStatusException);
		assertEquals(TransferFundsResponse.FAILURE, ex.getResponse().getStatus());
		assertEquals( 2, ex.getResponse().getReservations().size());
		assertEquals(request.getFromAccountNumber(), ex.getResponse().getReservations().get(0).getAccountNumber());
		assertEquals(TransactionResource.RESERVATION, ex.getResponse().getReservations().get(0).getTransactionTypeCode());
		assertEquals(request.getFromAccountNumber(), ex.getResponse().getReservations().get(1).getAccountNumber());
		assertEquals(TransactionResource.RESERVATION_CANCEL, ex.getResponse().getReservations().get(1).getTransactionTypeCode());
		assertNull(ex.getResponse().getFulfillmentMessage());

	}

	@Test
	void test_transferFunds_kafkaFailureAndCancellationFailure() {

		HashMap<String, String> headers = new HashMap<String, String>();

		TransferFundsRequest request = new TransferFundsRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setFromAccountNumber("12341234");
		request.setToAccountNumber("1334124");
		request.setTransactionAmount(-2345L);
		request.setTransactionJsonMetaData("{}");

		// --------------
		Account acct1 = new Account();
		acct1.setAccountLifeCycleStatus("EF");
		acct1.setAccountNumber(request.getFromAccountNumber());
		Account acct2 = new Account();
		acct2.setAccountLifeCycleStatus("EF");
		acct2.setAccountNumber(request.getToAccountNumber());
		when(jdbcDao.getAccount(anyString())).thenReturn(acct1).thenReturn(acct2);

		// --------------
		ReservationResponse setupResponse = new ReservationResponse(ReservationResponse.SUCCESS, new TransactionResource());
		setupResponse.getResource().setTransactionUuid(UUID.randomUUID());
		setupResponse.getResource().setAccountNumber(request.getFromAccountNumber());
		setupResponse.getResource().setDebitCardNumber(null);
		setupResponse.getResource().setInsertTimestamp(new Timestamp(Instant.now().toEpochMilli()));
		setupResponse.getResource().setReservationUuid(null);
		setupResponse.getResource().setRequestUuid(request.getRequestUuid());
		setupResponse.getResource().setRunningBalanceAmount(99999L);
		setupResponse.getResource().setTransactionAmount(request.getTransactionAmount());
		setupResponse.getResource().setTransactionMetaDataJson(request.getTransactionJsonMetaData());
		setupResponse.getResource().setTransactionTypeCode(TransactionResource.RESERVATION);
		when(reservationDao.recordReservation(any(), any(ReservationRequest.class))).thenReturn(setupResponse);

		// ---------------
		doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "test msg"))
			.when(kafkaDao).produceTransferMessage(any(), any(TransferFulfillmentMessage.class));

		// --------------
		when(reservationDao.cancelReservation(any(), any(CancelReservationRequest.class)))
			.thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "test msg"));
		
		// ---------------
		DisruptedProcessingException ex = assertThrows(DisruptedProcessingException.class, () -> {
			service.transferFunds(headers, request);
		});
		verify(kafkaDao).produceTransferMessage(any(), any(TransferFulfillmentMessage.class));
		verify(jdbcDao, times(2)).getAccount(anyString());
		verify(reservationDao).recordReservation(any(), any(ReservationRequest.class));
		verify(reservationDao).cancelReservation(any(), any(CancelReservationRequest.class));

		assertTrue( ex.getCause() instanceof ResponseStatusException);
		assertEquals(TransferFundsResponse.FAILURE, ex.getResponse().getStatus());
		assertEquals( 1, ex.getResponse().getReservations().size());
		assertEquals(request.getFromAccountNumber(), ex.getResponse().getReservations().get(0).getAccountNumber());
		assertEquals(TransactionResource.RESERVATION, ex.getResponse().getReservations().get(0).getTransactionTypeCode());
		assertNull(ex.getResponse().getFulfillmentMessage());

	}

}