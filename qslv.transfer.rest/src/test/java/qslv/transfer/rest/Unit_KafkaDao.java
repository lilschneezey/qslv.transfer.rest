package qslv.transfer.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.server.ResponseStatusException;

import qslv.common.TraceableRequest;
import qslv.common.kafka.TraceableMessage;
import qslv.transfer.request.TransferFulfillmentMessage;
import qslv.util.ElapsedTimeSLILogger;

@ExtendWith(MockitoExtension.class)
public class Unit_KafkaDao {
	@Autowired
	KafkaDao dao = new KafkaDao();
	ConfigProperties config = new ConfigProperties();
	
	@Mock
	KafkaTemplate<String, TraceableMessage<TransferFulfillmentMessage>> kafkaTemplate;
	@Mock
	ListenableFuture<SendResult<String, TraceableMessage<TransferFulfillmentMessage>>> future;

	@BeforeEach
	public void setup() {
		dao.setKafkaTemplate(kafkaTemplate);
		dao.setConfig(config);
		dao.setKafkaTimer(new ElapsedTimeSLILogger(LoggerFactory.getLogger(KafkaDao.class),"AIT","KAFKA"));
		config.setKafkaTransferRequestQueue("sdfsdfsdf");
		config.setAitid("234234");
		config.setKafkaTimeout(23423);
	}
	
	@Test
	public void test_produceTransferMessage_Success() throws InterruptedException, ExecutionException, TimeoutException {
		//---------------
		TraceableMessage<TransferFulfillmentMessage> message = new TraceableMessage<>();
		message.setPayload(new TransferFulfillmentMessage());
		
		//----------------
		ProducerRecord<String, TraceableMessage<TransferFulfillmentMessage>> producerRecord = new ProducerRecord<>("mockTopicName", message);
		SendResult<String, TraceableMessage<TransferFulfillmentMessage>> sendResult = new SendResult<String, TraceableMessage<TransferFulfillmentMessage>>(producerRecord, new RecordMetadata(new TopicPartition("mockTopic", 1), 1, 1, 1, 1L, 1, 1));
		when( future.get(anyLong(), any(TimeUnit.class) ) ).thenReturn(sendResult);
		when(kafkaTemplate.send(anyString(), anyString(), ArgumentMatchers.<TraceableMessage<TransferFulfillmentMessage>>any()))
			.thenReturn(future);

		// ------------------
		Map<String, String> headers = new HashMap<String, String>();
		headers.put(TraceableRequest.BUSINESS_TAXONOMY_ID, "78237492834");
		headers.put(TraceableRequest.CORRELATION_ID, "234234234234234234");
		
		//-----------------------------
		TransferFulfillmentMessage tfr = new TransferFulfillmentMessage();
		tfr.setFromAccountNumber("213478234");
		dao.produceTransferMessage(headers, tfr);
	}

	@Test
	public void test_produceTransferMessage_throwsInterrupted() throws InterruptedException, ExecutionException, TimeoutException {
		//---------------
		TraceableMessage<TransferFulfillmentMessage> message = new TraceableMessage<>();
		message.setPayload(new TransferFulfillmentMessage());
		
		//----------------
		when( future.get(anyLong(), any(TimeUnit.class) ) ).thenThrow(new InterruptedException());
		when(kafkaTemplate.send(anyString(), anyString(), ArgumentMatchers.<TraceableMessage<TransferFulfillmentMessage>>any()))
			.thenReturn(future);

		// ------------------
		Map<String, String> headers = new HashMap<String, String>();
		headers.put(TraceableRequest.BUSINESS_TAXONOMY_ID, "78237492834");
		headers.put(TraceableRequest.CORRELATION_ID, "234234234234234234");
		
		//-----------------------------
		TransferFulfillmentMessage tfr = new TransferFulfillmentMessage();
		tfr.setFromAccountNumber("213478234");
		
		assertThrows(ResponseStatusException.class, () -> {
			dao.produceTransferMessage(headers, tfr);
		});
	}
	
	@Test
	public void test_produceTransferMessage_throwsExecution() throws InterruptedException, ExecutionException, TimeoutException {
		//---------------
		TraceableMessage<TransferFulfillmentMessage> message = new TraceableMessage<>();
		message.setPayload(new TransferFulfillmentMessage());
		
		//----------------
		when( future.get(anyLong(), any(TimeUnit.class) ) ).thenThrow(new ExecutionException(new TimeoutException()));
		when(kafkaTemplate.send(anyString(), anyString(), ArgumentMatchers.<TraceableMessage<TransferFulfillmentMessage>>any()))
			.thenReturn(future);

		// ------------------
		Map<String, String> headers = new HashMap<String, String>();
		headers.put(TraceableRequest.BUSINESS_TAXONOMY_ID, "78237492834");
		headers.put(TraceableRequest.CORRELATION_ID, "234234234234234234");
		
		//-----------------------------
		TransferFulfillmentMessage tfr = new TransferFulfillmentMessage();
		tfr.setFromAccountNumber("213478234");
		
		assertThrows(ResponseStatusException.class, () -> {
			dao.produceTransferMessage(headers, tfr);
		});
	}
	
	@Test
	public void test_produceTransferMessage_throwsTimeout() throws InterruptedException, ExecutionException, TimeoutException {
		//---------------
		TraceableMessage<TransferFulfillmentMessage> message = new TraceableMessage<>();
		message.setPayload(new TransferFulfillmentMessage());
		
		//----------------
		when( future.get(anyLong(), any(TimeUnit.class) ) ).thenThrow(new TimeoutException());
		when(kafkaTemplate.send(anyString(), anyString(), ArgumentMatchers.<TraceableMessage<TransferFulfillmentMessage>>any()))
			.thenReturn(future);

		// ------------------
		Map<String, String> headers = new HashMap<String, String>();
		headers.put(TraceableRequest.BUSINESS_TAXONOMY_ID, "78237492834");
		headers.put(TraceableRequest.CORRELATION_ID, "234234234234234234");
		
		//-----------------------------
		TransferFulfillmentMessage tfr = new TransferFulfillmentMessage();
		tfr.setFromAccountNumber("213478234");
		
		assertThrows(ResponseStatusException.class, () -> {
			dao.produceTransferMessage(headers, tfr);
		});
	}
}
