package qslv.transfer.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import qslv.data.Account;

@ExtendWith(MockitoExtension.class)
public class Unit_JdbcDao {
	@Mock JdbcTemplate jdbcTemplate; 
	
	JdbcDao dao = new JdbcDao();
	
	@BeforeEach
	public void setup() {
		dao.setJdbcTemplate(jdbcTemplate);
	}

	@Test
	public void getAccount_success() {		
		String accountNumber = "DDDD3456HKWER7890";
		Account setupAccount = new Account();
		setupAccount.setAccountLifeCycleStatus("EF");
		setupAccount.setAccountNumber(accountNumber);
		List<Account> setupResults = Collections.singletonList(setupAccount);
		
		when( jdbcTemplate.query(eq(JdbcDao.getAccount_sql), ArgumentMatchers.<RowMapper<Account>>any(), anyString()) )
			.thenReturn(setupResults);
		
		Account account = dao.getAccount(accountNumber);
		verify(jdbcTemplate).query(eq(JdbcDao.getAccount_sql), ArgumentMatchers.<RowMapper<Account>>any(), anyString());
		assertEquals(setupAccount.getAccountLifeCycleStatus(), account.getAccountLifeCycleStatus());
		assertEquals(setupAccount.getAccountNumber(), account.getAccountNumber());
	}

	@Test
	public void getAccount_throws() {		
		String accountNumber = "DDDD3456HKWER7890";
		
		DataAccessException dae = new QueryTimeoutException("message");
		when( jdbcTemplate.query(eq(JdbcDao.getAccount_sql), ArgumentMatchers.<RowMapper<Account>>any(), anyString()) )
			.thenThrow(dae);
		
		assertThrows(DataAccessException.class, ()-> { dao.getAccount(accountNumber); } );

	}
}
