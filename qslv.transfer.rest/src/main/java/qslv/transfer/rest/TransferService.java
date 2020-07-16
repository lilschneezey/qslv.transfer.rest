package qslv.transfer.rest;
import qslv.data.Account;
import qslv.transaction.request.CancelReservationRequest;
import qslv.transaction.request.ReservationRequest;
import qslv.transaction.resource.TransactionResource;
import qslv.transaction.response.CancelReservationResponse;
import qslv.transaction.response.ReservationResponse;
import qslv.transfer.request.TransferFulfillmentMessage;
import qslv.transfer.request.TransferFundsRequest;
import qslv.transfer.response.TransferFundsResponse;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;



@Service
public class TransferService {
	private static final Logger log = LoggerFactory.getLogger(TransferService.class);

	@Autowired
	private JdbcDao jdbcDao;
	@Autowired
	private KafkaDao kafkaDao;
	@Autowired
	private ReservationDao reservationDao;

	public JdbcDao getJdbcDao() {
		return jdbcDao;
	}

	public void setJdbcDao(JdbcDao jdbcDao) {
		this.jdbcDao = jdbcDao;
	}

	public KafkaDao getKafkaDao() {
		return kafkaDao;
	}

	public void setKafkaDao(KafkaDao kafkaDao) {
		this.kafkaDao = kafkaDao;
	}

	public ReservationDao getReservationDao() {
		return reservationDao;
	}

	public void setReservationDao(ReservationDao reservationDao) {
		this.reservationDao = reservationDao;
	}

	public TransferFundsResponse transferFunds(Map<String, String> callingHeaders, TransferFundsRequest request) {
		log.debug("service.transferFunds ENTRY");
		
		Account fromAccount = jdbcDao.getAccount(request.getFromAccountNumber());
		if (false == accountInGoodStanding(fromAccount)) {
			log.debug("service.transferFunds EXIT From Account in bad standing. {}", fromAccount.toString());
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
					String.format("From account is in an invalid state."));
		}
		Account toAccount = jdbcDao.getAccount(request.getToAccountNumber());
		if (false == accountInGoodStanding(toAccount)) {
			log.debug("service.transferFunds EXIT To Account in bad standing. {}", fromAccount.toString());
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
					String.format("To account is in an invalid state."));
		}
		
		// --------------
		ReservationRequest treq = new ReservationRequest();
		treq.setAccountNumber(request.getFromAccountNumber());
		treq.setDebitCardNumber(null);
		treq.setRequestUuid(request.getRequestUuid());
		treq.setTransactionAmount(0L - request.getTransactionAmount());
		treq.setTransactionMetaDataJson(request.getTransactionJsonMetaData());
		
		// Reserve Money in From Account---------------
		ReservationResponse tresp = reservationDao.recordReservation(callingHeaders, treq);
		
		// ---------------
		TransferFundsResponse response = new TransferFundsResponse();
		response.setReservations(new ArrayList<TransactionResource>());
		response.getReservations().add(tresp.getResource());
		response.setStatus(tresp.getStatus());
		
		// ---------------
		if (tresp.getStatus() == ReservationResponse.INSUFFICIENT_FUNDS) {
			response.setFulfillmentMessage(null);
		} else {
			TransferFulfillmentMessage tfr = new TransferFulfillmentMessage();
			tfr.setFromAccountNumber(fromAccount.getAccountNumber());
			tfr.setToAccountNumber(toAccount.getAccountNumber());
			tfr.setRequestUuid(UUID.randomUUID());
			tfr.setReservationUuid(tresp.getResource().getTransactionUuid());
			tfr.setTransactionAmount(request.getTransactionAmount());
			tfr.setTransactionMetaDataJson(request.getTransactionJsonMetaData());
			tfr.setVersion(TransferFulfillmentMessage.version1_0);
			
			try {
				kafkaDao.produceTransferMessage(callingHeaders, tfr);
				response.setFulfillmentMessage(tfr);
			} catch (ResponseStatusException ex) {
				log.trace("Kafka message production failed. Attempting to cancel reservation.");
				response.setStatus(TransferFundsResponse.FAILURE);
				
				CancelReservationRequest cancellation = new CancelReservationRequest();
				cancellation.setRequestUuid(UUID.randomUUID());
				cancellation.setReservationUuid(tresp.getResource().getTransactionUuid());
				cancellation.setTransactionMetaDataJson(request.getTransactionJsonMetaData());
				try {
					CancelReservationResponse cresp = reservationDao.cancelReservation(callingHeaders, cancellation);
					response.getReservations().add(cresp.getResource());
				} catch (ResponseStatusException cancelException) {
					log.error("Could not cancel reservation {}", tresp.getResource().getTransactionUuid());
					log.error(cancelException.getLocalizedMessage());
				}
				throw new DisruptedProcessingException(response, ex.getStatus(), "Kakfa producer Timed Out", ex);
			}
		}
	
		log.debug("service.transferFunds EXIT");
		return response;
	}

	private boolean accountInGoodStanding(Account account) {
		return (account.getAccountLifeCycleStatus() == "EF");
	}

}