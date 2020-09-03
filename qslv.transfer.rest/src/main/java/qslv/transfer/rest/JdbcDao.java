package qslv.transfer.rest;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import qslv.data.Account;
import qslv.util.ExternalResourceSLI;

@Repository
public class JdbcDao {
	private static final Logger log = LoggerFactory.getLogger(JdbcDao.class);

	@Autowired
	private JdbcTemplate jdbcTemplate;

	public void setJdbcTemplate(JdbcTemplate template) {
		this.jdbcTemplate = template;
	}

	public final static String getAccount_sql = "SELECT account_no, lifecycle_status_cd FROM account WHERE account_no = ?; ";

	@ExternalResourceSLI(value="jdbc::AccountDB", ait = "#{@configProperties.aitid}", remoteFailures= {DataAccessException.class})
	public Account getAccount(final String accountNumber) {
		log.debug("getAccount ENTRY {}", accountNumber);

		// TODO - test retries using QueryTimeoutException
		List<Account> resources = jdbcTemplate.query(getAccount_sql,
				new RowMapper<Account>() {
					public Account mapRow(ResultSet rs, int rowNum) throws SQLException {
						Account res = new Account();
						
						res.setAccountNumber(rs.getString(1));
						res.setAccountLifeCycleStatus(rs.getString(2));
						return res;
					}
				}, accountNumber);
		
		log.debug("getDebitCardAccountJoin  {}", resources.get(0));
		return resources.get(0);
	}

}