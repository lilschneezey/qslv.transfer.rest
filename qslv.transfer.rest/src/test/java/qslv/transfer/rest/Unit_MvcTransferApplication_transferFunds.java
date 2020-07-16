package qslv.transfer.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import qslv.common.TimedResponse;
import qslv.common.TraceableRequest;
import qslv.common.kafka.TraceableMessage;
import qslv.data.Account;
import qslv.transaction.request.CancelReservationRequest;
import qslv.transaction.request.ReservationRequest;
import qslv.transaction.resource.TransactionResource;
import qslv.transaction.response.CancelReservationResponse;
import qslv.transaction.response.ReservationResponse;
import qslv.transfer.request.TransferFulfillmentMessage;
import qslv.transfer.request.TransferFundsRequest;
import qslv.transfer.response.TransferFundsResponse;

@SpringBootTest
@AutoConfigureMockMvc
class Unit_MvcTransferApplication_transferFunds {

	public static final MediaType APPLICATION_JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON.getType(),
			MediaType.APPLICATION_JSON.getSubtype(), Charset.forName("utf8"));
			
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	JdbcDao jdbcDao;
	@Autowired
	KafkaDao kafkaDao;
	@Autowired
	ConfigProperties config;
	@Autowired
	RestTemplateProxy restTemplateProxy;
	
	@Mock
	RestTemplate restTemplate;
	@Mock
	JdbcTemplate jdbcTemplate;
	@Mock
	KafkaTemplate<String, TraceableMessage<TransferFulfillmentMessage>> kafkaTemplate;
	@Mock
	ListenableFuture<SendResult<String, TraceableMessage<TransferFulfillmentMessage>>> future;
	ObjectMapper mapper = new ObjectMapper();

	@BeforeEach
	void setup() {
		jdbcDao.setJdbcTemplate(jdbcTemplate);
		kafkaDao.setKafkaTemplate(kafkaTemplate);
		restTemplateProxy.setRestTemplate(restTemplate);
	}
	
	@Test
	void test_transferFunds_success() throws Exception {
		setup_request();
		setup_fromAcct();
		setup_toAcct();
		prepare_jdbcTemplate();
	
		setup_reservation();
		prepare_restTemplate();
		
		setup_and_prepare_kafka();

		//--Execute---post transaction
		execute_post(status().isOk());
		
		verify(jdbcTemplate, times(2)).query( eq(JdbcDao.getAccount_sql), ArgumentMatchers.<RowMapper<Account>>any(), anyString() );
		verify(restTemplate).exchange(eq(config.getReservationUrl()), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<ReservationRequest>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<ReservationResponse>>>any());
		verify(future).get(anyLong(), any(TimeUnit.class));
		verify(kafkaTemplate).send(anyString(), anyString(), ArgumentMatchers.<TraceableMessage<TransferFulfillmentMessage>>any());

		extract_response();
		
		TransferFundsResponse tfr = response.getPayload();
		assertEquals(tfr.getStatus(), TransferFundsResponse.SUCCESS);
		assertEquals(1, tfr.getReservations().size());
		
		TransactionResource reservation = tfr.getReservations().get(0);
		assertEquals(reservationResource.getAccountNumber(), reservation.getAccountNumber());
		assertNull(reservation.getDebitCardNumber());
		assertEquals(request.getRequestUuid(), reservation.getRequestUuid());
		assertEquals(reservationResource.getRunningBalanceAmount(), reservation.getRunningBalanceAmount());
		assertEquals(reservationResource.getTransactionAmount(), reservation.getTransactionAmount());
		assertEquals(reservationResource.getTransactionMetaDataJson(), reservation.getTransactionMetaDataJson());
		assertEquals(TransactionResource.RESERVATION, reservation.getTransactionTypeCode());

		TransferFulfillmentMessage rtfm = tfr.getFulfillmentMessage();
		assertEquals(request.getFromAccountNumber(), rtfm.getFromAccountNumber());
		assertNotNull(rtfm.getRequestUuid());
		assertEquals(reservationResource.getTransactionUuid(), rtfm.getReservationUuid());
		assertEquals(request.getToAccountNumber(), rtfm.getToAccountNumber());
		assertEquals(request.getTransactionAmount(), rtfm.getTransactionAmount());
		assertEquals(request.getTransactionJsonMetaData(), rtfm.getTransactionMetaDataJson());		
	}
	
	@Test void test_transferFunds_invalidToAccount() throws Exception {
		setup_request();
		setup_fromAcct();
		fromAcct.setAccountLifeCycleStatus("CL");
		
		when( jdbcTemplate.query(eq(JdbcDao.getAccount_sql), ArgumentMatchers.<RowMapper<Account>>any(), anyString()) )
		.thenReturn(Collections.singletonList(fromAcct));
		
		execute_post(status().isUnprocessableEntity());
		
		verify(jdbcTemplate).query( eq(JdbcDao.getAccount_sql), ArgumentMatchers.<RowMapper<Account>>any(), anyString() );

	}
	
	@Test void test_transferFuncs_invalidFromAccount() throws Exception {
		setup_request();
		setup_fromAcct();
		setup_toAcct();
		toAcct.setAccountLifeCycleStatus("CL");
		
		when( jdbcTemplate.query(eq(JdbcDao.getAccount_sql), ArgumentMatchers.<RowMapper<Account>>any(), anyString()) )
		.thenReturn(Collections.singletonList(fromAcct))
		.thenReturn(Collections.singletonList(toAcct));
		
		execute_post(status().isUnprocessableEntity());

		verify(jdbcTemplate, times(2)).query( eq(JdbcDao.getAccount_sql), ArgumentMatchers.<RowMapper<Account>>any(), anyString() );
	}
	
	@Test void test_transferFunds_jdbcError() throws Exception {
		setup_request();
		
		when( jdbcTemplate.query(eq(JdbcDao.getAccount_sql), ArgumentMatchers.<RowMapper<Account>>any(), anyString()) )
			.thenThrow(new QueryTimeoutException("message"));

		execute_post(status().isInternalServerError());
		
		verify(jdbcTemplate).query( eq(JdbcDao.getAccount_sql), ArgumentMatchers.<RowMapper<Account>>any(), anyString() );

	}
	
	@Test void test_transferFunds_insufficientFunds() throws Exception {
		setup_request();
		setup_fromAcct();
		setup_toAcct();
		prepare_jdbcTemplate();
		setup_reservation();
		//-- override with NSF message
		reservationResource.setTransactionTypeCode(TransactionResource.REJECTED_TRANSACTION);
		reservationResponse.setStatus(ReservationResponse.INSUFFICIENT_FUNDS);	
		prepare_restTemplate();
		
		execute_post(status().isOk());
		extract_response();
		
		verify(jdbcTemplate, times(2)).query( eq(JdbcDao.getAccount_sql), ArgumentMatchers.<RowMapper<Account>>any(), anyString() );
		verify(restTemplate).exchange(eq(config.getReservationUrl()), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<ReservationRequest>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<ReservationResponse>>>any());
		
		assertNull(response.getPayload().getFulfillmentMessage());
		assertEquals(TransferFundsResponse.INSUFFICIENT_FUNDS, response.getPayload().getStatus());
	}
	
	@Test 
	void test_transferFunds_reservationFails() throws Exception {
		setup_request();
		setup_fromAcct();
		setup_toAcct();
		prepare_jdbcTemplate();
		setup_reservation();
		
		//-----------------
		when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<ReservationRequest>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<ReservationResponse>>>any()))
			.thenThrow(new ResourceAccessException("message", new SocketTimeoutException("Mock SocketTimeoutException")) )
			.thenThrow(new ResourceAccessException("message", new SocketTimeoutException("Mock SocketTimeoutException")) )
			.thenThrow(new ResourceAccessException("message", new SocketTimeoutException("Mock SocketTimeoutException")) );
		
		execute_post(status().isInternalServerError());
		
		verify(jdbcTemplate, times(2)).query( eq(JdbcDao.getAccount_sql), ArgumentMatchers.<RowMapper<Account>>any(), anyString() );
		verify(restTemplate, times(3)).exchange(eq(config.getReservationUrl()), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<ReservationRequest>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<TransactionResource>>>any());

	}
	
	@Test void test_transferFunds_kafkaFails() throws Exception {
		setup_request();
		setup_fromAcct();
		setup_toAcct();
		prepare_jdbcTemplate();
		setup_reservation();
		prepare_restTemplate();
		
		when( future.get(anyLong(), any(TimeUnit.class) ) ).thenThrow(new InterruptedException("Mock Interrupted Exception"));
		when(kafkaTemplate.send(anyString(), anyString(), ArgumentMatchers.<TraceableMessage<TransferFulfillmentMessage>>any()))
			.thenReturn(future);
		
		setup_cancellation();
		prepare_cancellation();
		
		execute_post(status().isInternalServerError());
		extract_response();
		
		verify(jdbcTemplate, times(2)).query( eq(JdbcDao.getAccount_sql), ArgumentMatchers.<RowMapper<Account>>any(), anyString() );
		verify(restTemplate).exchange(eq(config.getReservationUrl()), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<ReservationRequest>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<ReservationResponse>>>any());

		TransferFundsResponse tfr = response.getPayload();
		assertEquals(tfr.getStatus(), TransferFundsResponse.FAILURE);

		assertNull(tfr.getFulfillmentMessage());
		assertEquals(2, tfr.getReservations().size());

		TransactionResource cancellation = tfr.getReservations().get(1);
		assertEquals(reservationResource.getAccountNumber(), cancellation.getAccountNumber());
		assertNull(cancellation.getDebitCardNumber());
		assertEquals(request.getRequestUuid(), cancellation.getRequestUuid());
		assertEquals(cancelResource.getRunningBalanceAmount(), cancellation.getRunningBalanceAmount());
		assertEquals(cancelResource.getTransactionAmount(), cancellation.getTransactionAmount());
		assertEquals(request.getTransactionJsonMetaData(), cancellation.getTransactionMetaDataJson());
		assertEquals(TransactionResource.RESERVATION_CANCEL, cancellation.getTransactionTypeCode());

	}
	
	@Test void test_transferFunds_kafkaAndCancellationFails() throws Exception {
		setup_request();
		setup_fromAcct();
		setup_toAcct();
		prepare_jdbcTemplate();
		setup_reservation();
		prepare_restTemplate();
		
		when( future.get(anyLong(), any(TimeUnit.class) ) ).thenThrow(new InterruptedException());
		when(kafkaTemplate.send(anyString(), anyString(), ArgumentMatchers.<TraceableMessage<TransferFulfillmentMessage>>any()))
			.thenReturn(future);
		when(restTemplate.exchange(eq(config.getCancelReservationUrl()), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<CancelReservationRequest>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<CancelReservationResponse>>>any()))
			.thenThrow(new ResourceAccessException("message", new SocketTimeoutException("Mock SocketTimeoutException")) )
			.thenThrow(new ResourceAccessException("message", new SocketTimeoutException("Mock SocketTimeoutException")) )
			.thenThrow(new ResourceAccessException("message", new SocketTimeoutException("Mock SocketTimeoutException")) );
		
		execute_post(status().isInternalServerError());
		extract_response();
		
		verify(jdbcTemplate, times(2)).query( eq(JdbcDao.getAccount_sql), ArgumentMatchers.<RowMapper<Account>>any(), anyString() );
		verify(restTemplate).exchange(eq(config.getReservationUrl()), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<ReservationRequest>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<ReservationResponse>>>any());
		verify(restTemplate, times(3)).exchange(eq(config.getCancelReservationUrl()), eq(HttpMethod.POST), 
				ArgumentMatchers.<HttpEntity<CancelReservationRequest>>any(), 
				ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<CancelReservationResponse>>>any());

		TransferFundsResponse tfr = response.getPayload();
		assertEquals(tfr.getStatus(), TransferFundsResponse.FAILURE);

		assertNull(tfr.getFulfillmentMessage());
		assertEquals(1, tfr.getReservations().size());
	}
	
	//--Setups()---and----Prepares()---------------
	
	
	TransferFundsRequest request;
	String requestJson;
	void setup_request() throws Exception {
		request = new TransferFundsRequest();
		// ---------------------
		request.setFromAccountNumber("123456789012");
		request.setToAccountNumber("098765432123");
		request.setRequestUuid(UUID.randomUUID());
		request.setTransactionAmount(2323L);
		request.setTransactionJsonMetaData("{\"intvalue\":829342}");
		
		
		mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
		requestJson = mapper.writer().withDefaultPrettyPrinter().writeValueAsString(request);

	}
	Account fromAcct = new Account();
	void setup_fromAcct() {
		fromAcct.setAccountLifeCycleStatus("EF");
		fromAcct.setAccountNumber(request.getFromAccountNumber());
	}
	Account toAcct = new Account();
	void setup_toAcct() {
		toAcct.setAccountLifeCycleStatus("EF");
		toAcct.setAccountNumber(request.getToAccountNumber());
	}
	void prepare_jdbcTemplate() {
		when( jdbcTemplate.query(eq(JdbcDao.getAccount_sql), ArgumentMatchers.<RowMapper<Account>>any(), anyString()) )
		.thenReturn(Collections.singletonList(fromAcct))
		.thenReturn(Collections.singletonList(toAcct));
	}
	
	TransactionResource reservationResource = new TransactionResource();
	ReservationResponse reservationResponse = new ReservationResponse();
	void setup_reservation() {
		reservationResource.setAccountNumber(request.getFromAccountNumber());
		reservationResource.setDebitCardNumber(null);
		reservationResource.setInsertTimestamp(Timestamp.from(Instant.now()));
		reservationResource.setRequestUuid(request.getRequestUuid());
		reservationResource.setTransactionUuid(UUID.randomUUID());
		reservationResource.setReservationUuid(null);
		reservationResource.setRunningBalanceAmount(273848L);
		reservationResource.setTransactionAmount(request.getTransactionAmount());
		reservationResource.setTransactionMetaDataJson(request.getTransactionJsonMetaData());
		reservationResource.setTransactionTypeCode(TransactionResource.RESERVATION);
		
		reservationResponse.setResource(reservationResource);
		reservationResponse.setStatus(ReservationResponse.SUCCESS);
	}
	void prepare_restTemplate() {
		ResponseEntity<TimedResponse<ReservationResponse>> restResponse = 
				new ResponseEntity<TimedResponse<ReservationResponse>>(new TimedResponse<>(234567L, reservationResponse), HttpStatus.OK);
			
			when(restTemplate.exchange(eq(config.getReservationUrl()), eq(HttpMethod.POST), 
					ArgumentMatchers.<HttpEntity<ReservationRequest>>any(), 
					ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<ReservationResponse>>>any()))
				.thenReturn(restResponse);
	}
	
	void setup_and_prepare_kafka() throws InterruptedException, ExecutionException, TimeoutException {
		//--Kafka producer
		TransferFulfillmentMessage tfm = new TransferFulfillmentMessage();
		tfm.setFromAccountNumber("bogus");
		tfm.setToAccountNumber("bogus");
		tfm.setRequestUuid(UUID.randomUUID());
		tfm.setReservationUuid(UUID.randomUUID());
		tfm.setTransactionAmount(0);
		tfm.setTransactionMetaDataJson("bogus");
		
		TraceableMessage<TransferFulfillmentMessage> message = new TraceableMessage<>();
		message.setPayload(tfm);

		ProducerRecord<String, TraceableMessage<TransferFulfillmentMessage>> producerRecord = 
				new ProducerRecord<>(config.getKafkaTransferRequestQueue(), message);
		SendResult<String, TraceableMessage<TransferFulfillmentMessage>> sendResult = 
				new SendResult<String, TraceableMessage<TransferFulfillmentMessage>>(producerRecord, 
						new RecordMetadata(new TopicPartition("mockTopic", 1), 1, 1, 1, 1L, 1, 1));
		when( future.get(anyLong(), any(TimeUnit.class) ) ).thenReturn(sendResult);
		when(kafkaTemplate.send(anyString(), anyString(), ArgumentMatchers.<TraceableMessage<TransferFulfillmentMessage>>any()))
			.thenReturn(future);
	}
	
	TransactionResource cancelResource = new TransactionResource();
	CancelReservationResponse cancelResponse = new CancelReservationResponse();
	void setup_cancellation() {
		cancelResource.setAccountNumber(request.getFromAccountNumber());
		cancelResource.setDebitCardNumber(null);
		cancelResource.setInsertTimestamp(Timestamp.from(Instant.now()));
		cancelResource.setRequestUuid(request.getRequestUuid());
		cancelResource.setTransactionUuid(UUID.randomUUID());
		cancelResource.setReservationUuid(null);
		cancelResource.setRunningBalanceAmount(273848L);
		cancelResource.setTransactionAmount(request.getTransactionAmount());
		cancelResource.setTransactionMetaDataJson(request.getTransactionJsonMetaData());
		cancelResource.setTransactionTypeCode(TransactionResource.RESERVATION_CANCEL);
		
		cancelResponse.setResource(cancelResource);
		cancelResponse.setStatus(CancelReservationResponse.SUCCESS);
	}
	
	void prepare_cancellation() {
		ResponseEntity<TimedResponse<CancelReservationResponse>> restResponse = 
				new ResponseEntity<TimedResponse<CancelReservationResponse>>(new TimedResponse<>(123456L, cancelResponse), HttpStatus.OK);
			
			when(restTemplate.exchange(eq(config.getCancelReservationUrl()), eq(HttpMethod.POST), 
					ArgumentMatchers.<HttpEntity<CancelReservationRequest>>any(), 
					ArgumentMatchers.<ParameterizedTypeReference<TimedResponse<CancelReservationResponse>>>any()))
				.thenReturn(restResponse);
			
	}
	
	String postResult;
	void execute_post(ResultMatcher status) throws UnsupportedEncodingException, Exception {
		postResult = this.mockMvc.perform(post("/TransferFunds")
				.contentType(APPLICATION_JSON_UTF8)
				.content(requestJson)
				.header(TraceableRequest.AIT_ID, "46778")
				.header(TraceableRequest.BUSINESS_TAXONOMY_ID, "12.35.12.01")
				.header(TraceableRequest.CORRELATION_ID, "28394-njs78sd78f-23784234")
				.header(TraceableRequest.ACCEPT_VERSION, TransferFundsRequest.Version1_0))
				.andExpect(status)
				.andReturn()
				.getResponse()
				.getContentAsString();
	}
	
	TimedResponse<TransferFundsResponse> response;
	void extract_response() throws JsonMappingException, JsonProcessingException {
		response = mapper.readValue(postResult, new TypeReference<TimedResponse<TransferFundsResponse>>() {});

	}
}
