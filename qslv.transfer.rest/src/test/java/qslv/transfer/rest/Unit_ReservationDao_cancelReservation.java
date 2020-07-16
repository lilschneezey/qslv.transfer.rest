package qslv.transfer.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import qslv.common.TimedResponse;
import qslv.common.TraceableRequest;
import qslv.transaction.request.CancelReservationRequest;
import qslv.transaction.resource.TransactionResource;
import qslv.transaction.response.CancelReservationResponse;

@ExtendWith(MockitoExtension.class)
@ExtendWith(SpringExtension.class)
@SpringBootTest
@EnableRetry
class Unit_ReservationDao_cancelReservation {
	
	@Mock
	RestTemplate restTemplate;
	@Autowired
	ConfigProperties config;
	@Autowired
	RestTemplateProxy restTemplateProxy;
	@Autowired
	ReservationDao reservationDao;
	
	@BeforeEach
	public void init() {
		config.setAitid("723842");
		config.setReservationUrl("http://localhost:9091/reservation");
		reservationDao.setConfig(config);
		restTemplateProxy.setRestTemplate(restTemplate);
	}
	
	@Test
	void test_cancelReservation_success() {
		
		// ------------------
		Map<String, String> headers = new HashMap<String, String>();
		headers.put(TraceableRequest.BUSINESS_TAXONOMY_ID, "78237492834");
		headers.put(TraceableRequest.CORRELATION_ID, "234234234234234234");
		
		//------------------
		CancelReservationRequest request = new CancelReservationRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setReservationUuid(UUID.randomUUID());
		request.setTransactionMetaDataJson("{}");
		
		//------------------
		CancelReservationResponse rr = new CancelReservationResponse(CancelReservationResponse.SUCCESS,new TransactionResource());
		rr.getResource().setAccountNumber("12345679");
		rr.getResource().setDebitCardNumber("7823478239467");
		ResponseEntity<TimedResponse<CancelReservationResponse>> response = 
			new ResponseEntity<TimedResponse<CancelReservationResponse>>(new TimedResponse<>(rr), HttpStatus.OK);
		
		//-----------------
		when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<CancelReservationRequest>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<CancelReservationResponse>>>any()))
			.thenReturn(response);
		
		CancelReservationResponse callresult = reservationDao.cancelReservation(headers, request);
		assert(callresult.getStatus() == CancelReservationResponse.SUCCESS);
		assertEquals(rr.getResource().getAccountNumber(), callresult.getResource().getAccountNumber());
		assertEquals(rr.getResource().getDebitCardNumber(), callresult.getResource().getDebitCardNumber());

	}

	@Test
	void test_cancelReservation_failsOnce() {
		
		// ------------------
		Map<String, String> headers = new HashMap<String, String>();
		headers.put(TraceableRequest.BUSINESS_TAXONOMY_ID, "78237492834");
		headers.put(TraceableRequest.CORRELATION_ID, "234234234234234234");
		
		//------------------
		CancelReservationRequest request = new CancelReservationRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setReservationUuid(UUID.randomUUID());
		request.setTransactionMetaDataJson("{}");
		
		//------------------
		CancelReservationResponse rr = new CancelReservationResponse(CancelReservationResponse.SUCCESS,new TransactionResource());
		rr.getResource().setAccountNumber("12345679");
		rr.getResource().setDebitCardNumber("7823478239467");
		ResponseEntity<TimedResponse<CancelReservationResponse>> response = 
			new ResponseEntity<TimedResponse<CancelReservationResponse>>(new TimedResponse<>(rr), HttpStatus.OK);
		
		//-----------------
		when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<CancelReservationRequest>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<CancelReservationResponse>>>any()))
			.thenThrow(new ResourceAccessException("message", new SocketTimeoutException()) )
			.thenReturn(response);
		
		CancelReservationResponse callresult = reservationDao.cancelReservation(headers, request);
		assert(callresult.getStatus() == CancelReservationResponse.SUCCESS);
		assertEquals(rr.getResource().getAccountNumber(), callresult.getResource().getAccountNumber());
		assertEquals(rr.getResource().getDebitCardNumber(), callresult.getResource().getDebitCardNumber());

	}

	@Test
	void test_cancelReservation_failsTwice() {
		
		// ------------------
		Map<String, String> headers = new HashMap<String, String>();
		headers.put(TraceableRequest.BUSINESS_TAXONOMY_ID, "78237492834");
		headers.put(TraceableRequest.CORRELATION_ID, "234234234234234234");
		
		//------------------
		CancelReservationRequest request = new CancelReservationRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setReservationUuid(UUID.randomUUID());
		request.setTransactionMetaDataJson("{}");
		
		//------------------
		CancelReservationResponse rr = new CancelReservationResponse(CancelReservationResponse.SUCCESS,new TransactionResource());
		rr.getResource().setAccountNumber("12345679");
		rr.getResource().setDebitCardNumber("7823478239467");
		ResponseEntity<TimedResponse<CancelReservationResponse>> response = 
			new ResponseEntity<TimedResponse<CancelReservationResponse>>(new TimedResponse<>(rr), HttpStatus.OK);
		
		//-----------------
		when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<CancelReservationRequest>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<CancelReservationResponse>>>any()))
			.thenThrow(new ResourceAccessException("message", new SocketTimeoutException()) )
			.thenThrow(new ResourceAccessException("message", new SocketTimeoutException()) )
			.thenReturn(response);
		
		CancelReservationResponse callresult = reservationDao.cancelReservation(headers, request);
		assertTrue(callresult.getStatus() == CancelReservationResponse.SUCCESS);
		assertEquals(rr.getResource().getAccountNumber(), callresult.getResource().getAccountNumber());
		assertEquals(rr.getResource().getDebitCardNumber(), callresult.getResource().getDebitCardNumber());
	}
	
	@Test
	void test_cancelReservation_failsThrice() {
		
		// ------------------
		Map<String, String> headers = new HashMap<String, String>();
		headers.put(TraceableRequest.BUSINESS_TAXONOMY_ID, "78237492834");
		headers.put(TraceableRequest.CORRELATION_ID, "234234234234234234");
		
		//------------------
		CancelReservationRequest request = new CancelReservationRequest();
		request.setRequestUuid(UUID.randomUUID());
		request.setReservationUuid(UUID.randomUUID());
		request.setTransactionMetaDataJson("{}");
		
		//-----------------
		when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<CancelReservationRequest>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<CancelReservationResponse>>>any()))
			.thenThrow(new ResourceAccessException("message", new SocketTimeoutException()) )
			.thenThrow(new ResourceAccessException("message", new SocketTimeoutException()) )
			.thenThrow(new ResourceAccessException("message", new SocketTimeoutException()) );
		
		assertThrows(ResponseStatusException.class, () -> {
			reservationDao.cancelReservation(headers, request);
		});

	}
}
