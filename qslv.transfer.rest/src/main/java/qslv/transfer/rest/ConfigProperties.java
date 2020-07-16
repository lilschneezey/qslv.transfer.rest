package qslv.transfer.rest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import qslv.util.EnableQuickSilver;

@Configuration
@ConfigurationProperties(prefix = "qslv")
@PropertySource("classpath:application.properties")
@EnableQuickSilver
public class ConfigProperties {

	private String aitid = "27834";
	private String reservationUrl;
	private String cancelReservationUrl;
	private int restConnectionRequestTimeout = 1000;
	private int restConnectTimeout = 1000;
	private int restTimeout = 1000;
	private int restAttempts = 3;
	private int restBackoffDelay = 100;
	private int restBackoffDelayMax = 500; 
	private String kafkaTransferRequestQueue;
	private String kafkaProperties;
	private int kafkaTimeout;

	public String getAitid() {
		return aitid;
	}

	public void setAitid(String aitid) {
		this.aitid = aitid;
	}

	public String getReservationUrl() {
		return reservationUrl;
	}

	public void setReservationUrl(String reservationUrl) {
		this.reservationUrl = reservationUrl;
	}

	public String getCancelReservationUrl() {
		return cancelReservationUrl;
	}

	public void setCancelReservationUrl(String cancelReservationUrl) {
		this.cancelReservationUrl = cancelReservationUrl;
	}

	public int getRestConnectionRequestTimeout() {
		return restConnectionRequestTimeout;
	}

	public void setRestConnectionRequestTimeout(int restConnectionRequestTimeout) {
		this.restConnectionRequestTimeout = restConnectionRequestTimeout;
	}

	public int getRestConnectTimeout() {
		return restConnectTimeout;
	}

	public void setRestConnectTimeout(int restConnectTimeout) {
		this.restConnectTimeout = restConnectTimeout;
	}

	public int getRestTimeout() {
		return restTimeout;
	}

	public void setRestTimeout(int restTimeout) {
		this.restTimeout = restTimeout;
	}

	public int getRestAttempts() {
		return restAttempts;
	}

	public void setRestAttempts(int restAttempts) {
		this.restAttempts = restAttempts;
	}

	public int getRestBackoffDelay() {
		return restBackoffDelay;
	}

	public void setRestBackoffDelay(int restBackoffDelay) {
		this.restBackoffDelay = restBackoffDelay;
	}

	public int getRestBackoffDelayMax() {
		return restBackoffDelayMax;
	}

	public void setRestBackoffDelayMax(int restBackoffDelayMax) {
		this.restBackoffDelayMax = restBackoffDelayMax;
	}

	public String getKafkaTransferRequestQueue() {
		return kafkaTransferRequestQueue;
	}

	public void setKafkaTransferRequestQueue(String kafkaTransferRequestQueue) {
		this.kafkaTransferRequestQueue = kafkaTransferRequestQueue;
	}

	public String getKafkaProperties() {
		return kafkaProperties;
	}

	public void setKafkaProperties(String kafkaProperties) {
		this.kafkaProperties = kafkaProperties;
	}
	
	public String[] getKafkaPropertiesArray() {
		return kafkaProperties.split(",");
	}

	public int getKafkaTimeout() {
		return kafkaTimeout;
	}

	public void setKafkaTimeout(int kafkaTimeout) {
		this.kafkaTimeout = kafkaTimeout;
	}
	
}
