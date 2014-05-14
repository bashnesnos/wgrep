import org.smltools.grepp.*
import org.smltools.grepp.filters.entry.*
import org.smltools.grepp.filters.logfile.*
import org.smltools.grepp.config.CLIFacade
import org.smltools.grepp.config.XMLConfigHolder
import org.smltools.grepp.config.ConfigHolder
import org.smltools.grepp.util.GreppUtil
import java.net.URL
import groovy.xml.DOMBuilder
import groovy.xml.dom.DOMCategory
import groovy.util.GroovyTestCase
import java.text.SimpleDateFormat

class GreppTest extends GroovyTestCase {

	ConfigHolder config
	CLIFacade facade
	def BASE_HOME = System.getProperty("grepp.home")
	def HOME = BASE_HOME + "\\build\\resources\\test"
	def GREPP_CONFIG = BASE_HOME + "\\build\\resources\\main\\config\\" + System.getProperty("grepp.config")
	def GREPP_CONFIG_XSD = BASE_HOME + "\\build\\resources\\main\\config\\config.xsd"

	void setUp() {
		switch(GREPP_CONFIG) {
			case ~/.*\.groovy/:
				config = new ConfigHolder(new URL('file', '/', GREPP_CONFIG))			
				break
			case ~/.*\.xml/:
				config = new XMLConfigHolder(GREPP_CONFIG, GREPP_CONFIG_XSD)			
				break
		}
		facade = new CLIFacade(config);
	}

	public static String getOutput(Closure operation) {
		def oldStdout = System.out
		def pipeOut = new PipedOutputStream()
		def pipeIn = new PipedInputStream(pipeOut)
		System.setOut(new PrintStream(pipeOut))

		try {
			operation.call()
		}
		catch (Exception e) {
			pipeOut.close()
			System.setOut(oldStdout)
			throw e
		}
		finally {
			System.setOut(oldStdout)
			pipeOut.close()
		}

		def outputReader = new BufferedReader(new InputStreamReader(pipeIn))

		StringBuffer actualResult = new StringBuffer()
		if (outputReader.ready()) {
			def line = outputReader.readLine()
			while (line != null) {
				actualResult.size() > 0 ? actualResult.append('\n').append(line) : actualResult.append(line)
				line = outputReader.readLine()
			}
		}
		return actualResult.toString()
	}

	public static void assertGreppOutput(String expectedResult, Closure operation) {
		println "ER: ####\n$expectedResult\n#### :ER"
		String actualResult = getOutput(operation)
		println "AR: ####\n$actualResult\n#### :AR"
		assertTrue(expectedResult == actualResult)
	}

	public static def getFilterChain(def facade, String arguments) {
		def options = facade.parseOptions(arguments.split())
		def runtimeConfig = facade.makeRuntimeConfig()
		return facade.makeEntryFilterChain(runtimeConfig, options)
	}

//	void testGetOptions(){
//		config.getOptions()
//	}
	
	void testMainVarsProcessing() {
		def options = facade.parseOptions("-l test test $HOME\\fpTest*".split())
		assertTrue("User entry pattern option not recognized: " + options.l, "test".equals(options.l))
		def runtimeConfig = facade.makeRuntimeConfig()
		def entryFilterChain = facade.makeEntryFilterChain(runtimeConfig, options)
		assertTrue("Filter pattern not recognized", "test".equals(runtimeConfig.runtime.filterPattern))
		assertTrue("Files not recognized", runtimeConfig.runtime.data.files == [
			new File(HOME+"\\fpTest_test.log")]
		)
		assertTrue("Folder separator not initialized", runtimeConfig.runtime.folderSeparator != null )
	}
	
	void testConfigsProcessing() {
		def entryFilterChain = getFilterChain(facade, "--to_test --predef $HOME\\fpTest*")
		assertTrue("Should have LogEntryFilter", entryFilterChain.has(LogEntryFilter.class))
		assertTrue("Should have SimpleFilter", entryFilterChain.has(SimpleFilter.class))
	}

	void testExtendedPatternProcessing() {
		def entryFilterChain = getFilterChain(facade, "-l test test%and%tets $HOME\\test*")
		assertTrue("Should have LogEntryFilter", entryFilterChain.has(LogEntryFilter.class))
		assertTrue("Should have SimpleFilter", entryFilterChain.has(SimpleFilter.class))
	}

	void testComplexVarsProcessing() {
		def options = facade.parseOptions("-l test --dtime 2013-01-25T12:00:00;+ test $HOME\\test*".split())
		def runtimeConfig = facade.makeRuntimeConfig()
		def entryFilterChain = facade.makeEntryFilterChain(runtimeConfig, options)
		assertTrue("Should have EntryDateFilter", entryFilterChain.has(EntryDateFilter.class))
		assertTrue("Should have LogEntryFilter", entryFilterChain.has(LogEntryFilter.class))
		assertTrue("Should have SimpleFilter", entryFilterChain.has(SimpleFilter.class))
		def fileFilterChain = facade.makeFileFilterChain(runtimeConfig, options)		
		assertTrue("Should have FileDateFilter", fileFilterChain.has(FileDateFilter.class))
	}

	void testAutomationProcessing() {
		def options = facade.parseOptions("test $HOME\\fpTest_*".split())
		def runtimeConfig = facade.makeRuntimeConfig()
		def entryFilterChain = facade.makeEntryFilterChain(runtimeConfig, options)

		entryFilterChain.refreshByConfigId(ConfigHolder.findConfigIdByFileName(config, runtimeConfig.runtime.data.files[0].name))
		assertTrue("Should have LogEntryFilter", entryFilterChain.has(LogEntryFilter.class))
		assertTrue("Should have SimpleFilter", entryFilterChain.has(SimpleFilter.class))
	}

	void testMoreComplexVarsProcessing() {
		def options = facade.parseOptions("-s -l stCommand --some_timings cmd_only_1.log".split())
		def runtimeConfig = facade.makeRuntimeConfig()
		def entryFilterChain = facade.makeEntryFilterChain(runtimeConfig, options)
		assertTrue("Should have LogEntryFilter", entryFilterChain.has(LogEntryFilter.class))
		assertTrue("Should have SimpleFilter", entryFilterChain.has(SimpleFilter.class))
		assertTrue("Files not recognized", runtimeConfig.runtime.data.files.containsAll([new File("cmd_only_1.log")]))
		assertTrue("Separator wasn't identified", "\\\\".equals(runtimeConfig.runtime.folderSeparator))
	}

	void testFileMTimeFiltering() {
		def fileTime = new Date(new File(HOME+"\\processing_time_test.log").lastModified())
		def dateFormat = new SimpleDateFormat("yyyy-MM-dd")
		def testTimeStringFrom = dateFormat.format(new Date(fileTime.getTime() + 24*60*60*1000))

		def expectedResult = ""
		assertGreppOutput(expectedResult) {
			Grepp.main("--dtime $testTimeStringFrom;+ Foo $HOME\\processing_time_test.log".split(" "))
		}

	}

	void testComplexFiltering() {

		def expectedResult = """\
2012-09-20 05:05:56,951 [ACTIVE] ThreadStart: '22' 
Foo Koo

2013-09-20 05:05:57,951 [ACTIVE] ThreadStart: '22' SkipPattern
Too
2014-09-20 05:05:57,951 [ACTIVE] ThreadStart: '22' ThreadEnd1
Goo
2012-10-20 05:05:56,951 [ACTIVE] ThreadStart: '1' 
Foo Man Chu
#basic
2012-10-20 05:05:57,952 [ACTIVE] ThreadStart: '1' SkipPattern
Loo
2012-10-20 05:05:57,953 [ACTIVE] ThreadStart: '1' ThreadEnd2
Voo
#complex"""

		assertGreppOutput(expectedResult) {
			Grepp.main("-e Foo $HOME\\processing_test.log".split(" "))
		}
	}

	void testComplexUserPatternFiltering() {

		def expectedResult = """\
2012-10-20 05:05:56,951 [ACTIVE] ThreadStart: '1' 
Foo Man Chu
#basic"""
		assertGreppOutput(expectedResult) {
			Grepp.main((String[]) ["Foo%and%Man Chu%or%#basic" //don't need to split here
				, "$HOME\\processing_test.log"])
		}
	}

	void testBasicFiltering() {

		def expectedResult = """\
2012-09-20 05:05:56,951 [ACTIVE] ThreadStart: '22' 
Foo Koo

2012-10-20 05:05:56,951 [ACTIVE] ThreadStart: '1' 
Foo Man Chu
#basic"""

		assertGreppOutput(expectedResult) {
			Grepp.main("Foo $HOME\\processing_test.log".split(" "))
		}
	}

	void testTimeFiltering() {

		def fileTime = new Date(new File(HOME+"\\processing_time_test.log").lastModified())
		def dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH")
		def logDateFormat = new SimpleDateFormat("yyyy-MM-dd HH")
		def testTimeStringFrom = dateFormat.format(fileTime)

		def expectedResult = """\
${logDateFormat.format(fileTime)}:05:56,951 [ACTIVE] ThreadStart: '22' 
Foo Koo
"""
		assertGreppOutput(expectedResult) {
			Grepp.main("--dtime $testTimeStringFrom;+60 Foo $HOME\\processing_time_test.log".split(" "))
		}
	}

	void testTimeLeftBoundOnlyFiltering() {

		def fileTime = new Date(new File(HOME+"\\processing_time_test.log").lastModified())
		def dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH")
		def logDateFormat = new SimpleDateFormat("yyyy-MM-dd HH")
		def testTimeStringFrom = dateFormat.format(fileTime)

		def expectedResult = """\
${logDateFormat.format(fileTime)}:05:56,951 [ACTIVE] ThreadStart: '22' 
Foo Koo

${logDateFormat.format(new Date(fileTime.getTime() + 3*60*60*1000))}:05:56,951 [ACTIVE] ThreadStart: '1' 
Foo Man Chu
#basic"""
		assertGreppOutput(expectedResult) {
			Grepp.main("--dtime $testTimeStringFrom;+ Foo $HOME\\processing_time_test.log".split(" "))
		}
	}

	void testTimeRightBoundOnlyFiltering() {

		def fileTime = new Date(new File(HOME+"\\processing_time_test.log").lastModified())
		def dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH")
		def logDateFormat = new SimpleDateFormat("yyyy-MM-dd HH")
		def testTimeStringTo = dateFormat.format(new Date(fileTime.getTime() + 60*60*1000))

		def expectedResult = """\
${logDateFormat.format(fileTime)}:05:56,951 [ACTIVE] ThreadStart: '22' 
Foo Koo
"""
		assertGreppOutput(expectedResult) {
			Grepp.main("--dtime +;$testTimeStringTo --foo $HOME\\processing_time_test.log".split(" "))
		}
	}

	void testPostFiltering() {

		def expectedResult = """\
some_cmd,count_of_operands
Foo,3
Koo,1"""

		assertGreppOutput(expectedResult) {
			Grepp.main("--some_timings $HOME\\processing_report_test.log".split(" "))
		}
	}

	void testPostAverageFiltering() {

		def expectedResult = """\
some_cmd,avg_processing
Foo,150
Koo,200
"""
		assertGreppOutput(expectedResult) {
			Grepp.main("--avg_timings $HOME\\processing_report_test.log".split(" "))
		}
	}

	void testHeteroFilesGreppMain() {

		def expectedResult = """\
2012-09-20 05:05:56,951 [ACTIVE] ThreadStart: '22' 
Foo Koo

2012-10-20 05:05:56,951 [ACTIVE] ThreadStart: '1' 
Foo Man Chu
#basic"""

		assertGreppOutput(expectedResult) {
			Grepp.main("Foo $HOME\\processing_test.log $HOME\\fpTest_test.log".split(" "))
		}
	}

	void testPropertiesFilter() {
		def configString = """\
log4j.logger.com.netcracker.solutions.tnz.cwms=DEBUG, CWMSGlobal
log4j.appender.CWMSGlobal=org.apache.log4j.RollingFileAppender
log4j.appender.CWMSGlobal.File=logs/cwms_debug_\${weblogic.Name}.log
log4j.appender.CWMSGlobal.MaxFileSize=50MB
log4j.appender.CWMSGlobal.MaxBackupIndex=20
log4j.appender.CWMSGlobal.layout=org.apache.log4j.PatternLayout
log4j.appender.CWMSGlobal.layout.ConversionPattern=\\#\\#\\#\\#[%-5p] %d{ISO8601} %t %c - %n%m%n
"""
		def expectedResult = """\
cwms_debug_ {
	dateFormat {
		value='yyyy-MM-dd HH:mm:ss,SSS'
		regex='(\\\\d{4}-\\\\d{2}-\\\\d{2} \\\\d{2}:\\\\d{2}:\\\\d{2},\\\\d{3})'
	}
	starter='\\\\#\\\\#\\\\#\\\\#\\\\[[TRACEDBUGINFLOWSV]* *\\\\].*'
	pattern='cwms_debug_.*\\\\.log'
}
"""	
		def propFilter = new PropertiesFilter()
		assertTrue(propFilter.filter(configString).replace("\r\n", "\n") == expectedResult)
	}

	void testPropertiesProcessing() {

		Grepp.main("--parse $HOME\\test.properties".split(" "))
		switch (GREPP_CONFIG) {
			case ~/.*\.xml/:
				def cfgDoc = DOMBuilder.parse(new FileReader(GREPP_CONFIG))
				def root = cfgDoc.documentElement
				use(DOMCategory) {
					def config = root.config.find { it.'@id' == "cwms_debug_" }
					assertTrue(config != null)
					assertTrue(config.logDateFormat.text() == "cwms_debug_")
					assertTrue(config.starter.text() == "\\#\\#\\#\\#\\[[TRACEDBUGINFLOWSV]* *\\].*")
					assertTrue(config.pattern.text() == "cwms_debug_.*\\.log")
				}
				break
			case ~/.*\.groovy/:
				def changedConfig = new ConfigHolder(new URL('file', '/', GREPP_CONFIG))
				assertTrue(changedConfig.savedConfigs.containsKey('cwms_debug_'))
				assertTrue(changedConfig.logDateFormats.containsKey('cwms_debug_'))
				assertTrue(changedConfig.savedConfigs.cwms_debug_.starter == "\\#\\#\\#\\#\\[[TRACEDBUGINFLOWSV]* *\\].*")
				assertTrue(changedConfig.savedConfigs.cwms_debug_.pattern == "cwms_debug_.*\\.log")
				break
		}

	}

	void testInputStreamProcessing() {
		def tPipeOut = new PipedOutputStream()
		def tPipeIn = new PipedInputStream(tPipeOut)
		def passToIn = new PrintStream(tPipeOut)
		def text = """\
#asda
asdas
#asdas
#sadas
fdsfd
"""
		passToIn.print(text)
		passToIn.close()
		def oldIn = System.in
		System.setIn(tPipeIn)
		def expectedResult = """#asda
asdas
#asdas"""

		try {
			assertGreppOutput(expectedResult) {
				Grepp.main("-l # asd".split(" "))
			}
		}
		catch (Exception e) {
			tPipeIn.close()
			System.setIn(oldIn)
			throw e
		}
		finally {
			tPipeIn.close()
			System.setIn(oldIn)
		}
	}
	
}