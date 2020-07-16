package qslv.transfer.rest;

import org.junit.platform.runner.JUnitPlatform;
import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
@SelectPackages("qslv.transfer.rest")
@IncludeClassNamePatterns("^(Unit_.*|.+[.$]Unit_.*)$")
class UnitSuiteTransactionTest {


}
