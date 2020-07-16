package qslv.transfer.rest;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.web.server.ResponseStatusException;

import qslv.common.kafka.JacksonAvroSerializer;
import qslv.common.kafka.TraceableMessage;
import qslv.transfer.request.TransferFulfillmentMessage;
import qslv.util.ElapsedTimeSLILogger;

@Configuration
public class KafkaProducerConfig {
	private static final Logger log = LoggerFactory.getLogger(KafkaProducerConfig.class);
	@Autowired
	private ConfigProperties config;

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Bean 
	public Map<String,Object> kafkaConfig() throws Exception {
		Properties kafkaconfig = new Properties();
		try {
			kafkaconfig
					.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("app-kafka.properties"));
		} catch (Exception ex) {
			log.debug("app-kafka.properties not found.");
			throw ex;
		}
		return new HashMap(kafkaconfig);
	}
	
	@Bean
	public ProducerFactory<String, TraceableMessage<TransferFulfillmentMessage>> producerFactory() throws Exception {
		Map<String,Object> kafkaprops = kafkaConfig();
		JacksonAvroSerializer<TraceableMessage<TransferFulfillmentMessage>> jas = new JacksonAvroSerializer<>();
		jas.configure(kafkaprops, false);
		return new DefaultKafkaProducerFactory<String, TraceableMessage<TransferFulfillmentMessage>>(kafkaprops,
				new StringSerializer(), jas);
	}

	@Bean
	public KafkaTemplate<String, TraceableMessage<TransferFulfillmentMessage>> kafkaTemplate() throws Exception {
		return new KafkaTemplate<>(producerFactory(), true); // auto-flush true, to force each message to broker.
	}
	
	@Bean
	ElapsedTimeSLILogger kafkaTimer() {
		return new ElapsedTimeSLILogger(LoggerFactory.getLogger(KafkaDao.class), config.getAitid(), config.getKafkaTransferRequestQueue(), 
				Collections.singletonList(ResponseStatusException.class)); 
	}
}
