package qslv.transfer.rest;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import qslv.common.TraceableRequest;
import qslv.common.kafka.TraceableMessage;
import qslv.transfer.request.TransferFulfillmentMessage;
import qslv.util.ElapsedTimeSLILogger;

@Repository
public class KafkaDao {
	private static final Logger log = LoggerFactory.getLogger(KafkaDao.class);

	@Autowired
	private ConfigProperties config;
	@Autowired
	private KafkaTemplate<String, TraceableMessage<TransferFulfillmentMessage>> kafkaTemplate;
	@Autowired
	private ElapsedTimeSLILogger kafkaTimer;
	
	public void setConfig(ConfigProperties config) {
		this.config = config;
	}
	public void setKafkaTemplate(KafkaTemplate<String, TraceableMessage<TransferFulfillmentMessage>> kafkaTemplate) {
		this.kafkaTemplate = kafkaTemplate;
	}
	public void setKafkaTimer(ElapsedTimeSLILogger kafkaTimer) {
		this.kafkaTimer = kafkaTimer;
	}
	
	public void produceTransferMessage(Map<String, String> callingHeaders, TransferFulfillmentMessage tfr) throws ResponseStatusException {
		log.trace("ENTRY produceTransferMessage");
		
		TraceableMessage<TransferFulfillmentMessage> msg = new TraceableMessage<>();
		msg.setBusinessTaxonomyId(callingHeaders.get(TraceableRequest.BUSINESS_TAXONOMY_ID));
		msg.setCorrelationId(callingHeaders.get(TraceableRequest.CORRELATION_ID));
		msg.setProducerAit(config.getAitid());
		msg.setPayload(tfr);
		
		//TODO: multi-data center retry
		kafkaTimer.logElapsedTime(() -> {
			produce(tfr.getFromAccountNumber(), msg);
		});
		log.trace("Exit produceTransferMessage");
	}
	
	private void produce(String key, TraceableMessage<TransferFulfillmentMessage> msg) throws ResponseStatusException {
		try {
			// retry handled internally by kafka using retries & retry.backoff.ms in properties file
			ProducerRecord<String ,TraceableMessage<TransferFulfillmentMessage>> record = 
				kafkaTemplate.send(config.getKafkaTransferRequestQueue(), key, msg)
				// wait with time out for post to complete. 
				// kafkaTemplate auto-flush is set to true. timeouts are in properties.
				.get(config.getKafkaTimeout(), TimeUnit.MILLISECONDS).getProducerRecord();
			log.debug("Kakfa Produce {}", record.value().getPayload());
		} catch ( ExecutionException ex) {
			log.debug(ex.getLocalizedMessage());
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Kafka Producer failure", ex);
		} catch ( TimeoutException | InterruptedException  ex) {
			log.debug(ex.getLocalizedMessage());
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Kafka Producer failure", ex);
		}
		
		// TODO: log time it took
	}
}
