package org.smltools.grepp.cli

import groovy.util.logging.Slf4j
import groovy.util.ConfigObject
import groovy.util.OptionAccessor
import org.smltools.grepp.cli.varparsers.*
import org.smltools.grepp.config.ConfigHolder
import org.smltools.grepp.filters.FilterChain
import org.smltools.grepp.filters.StringAggregator
import org.smltools.grepp.filters.entry.EntryDateFilter
import org.smltools.grepp.filters.entry.LogEntryFilter
import org.smltools.grepp.filters.entry.SimpleFilter
import org.smltools.grepp.filters.entry.ThreadFilter
import org.smltools.grepp.filters.entry.PropertiesFilter
import org.smltools.grepp.filters.entry.ReportFilter
import org.smltools.grepp.util.GreppUtil
import org.smltools.grepp.filters.enums.*
import org.smltools.grepp.filters.logfile.FileDateFilter
import org.smltools.grepp.filters.logfile.FileSortFilter
import org.smltools.grepp.output.ConfigOutput
import org.smltools.grepp.output.GreppOutput
import org.smltools.grepp.output.SimpleOutput
import org.smltools.grepp.processors.DataProcessor
import org.smltools.grepp.processors.InputStreamProcessor
import org.smltools.grepp.processors.TextFileProcessor
import static org.smltools.grepp.Constants.*

/**
 * Class represents wgrep config, which will be used to parse incoming arguments, config.xml and would be a source for processing, filtering etc. 
 *
 * @author Alexander Semelit 
 *
 */
@Slf4j
public class CLIFacade {
	
	protected ConfigHolder config;

	//OPTIONS
	protected File curWorkDir //allows to restrict access to a supplied working dir only
        
	public CLIFacade(ConfigHolder config) {
		this.config = config
	}
	

	// INITIALIZATION

	public void setWorkingDir(File cwd) {
		log.trace("Directory limited to {}", cwd.getAbsolutePath())
		curWorkDir = cwd
	}
	
	public File getWorkingDir() {
		return curWorkDir
	}


	/**
	 * Main method for the command-line arguments processing.
	 * <p>
	 * It processes arguments in the following way:
	 *   <li>1. Flags starting with - and options starting with --</li>
	 *   <li>2. All other arguments</li>
	 * <p>
	 * All other arguments are parsed via subscribed {@link varParsers}. <br>
	 * I.e. if option, or flag requires some arguments to be parsed immediately after it was specified, a valid subclass of {@link ParserBase} should be instantiated and subscribed in the option/flag handler. <br>
	 * {@link varParsers} are iterated in a LIFO manner. Only the last one recieves an argument for parsing. As soon as parser recieves all the required arguments, it should unsubscribe, so further arguments are passed to the next parser. <br>
	 * By default the following parser are instantiated:
	 *   <li>1. {@link FilterParser}</li>
	 *   <li>2. {@link FileNameParser}</li>
	 *
	 * @param args Array of strings containing arguments for parsing.
	 */

	public OptionAccessor parseOptions(String[] args)
	{
		if (args == null || args.length == 0) throw new IllegalArgumentException("Invalid arguments")
		
		def cli = new CliBuilder(usage:"grepp [options] filter_regex [filename [filename]]"
            , width: 100
            , header:"Options:"
            , footer: """
===========================
Parameters:
filter_regex     - a string to find in the input. Could be replaced with pre-configured filter_regex, for this just put '--filter_regex_id'
filename         - filename for analysis. Could be multiple, or with wildcard *. In case of piping (i.e. cat bla.txt | grepp blabla) filename should be omitted.
===========================
Examples:
Using in Windows
grepp -s \"Something_I#Need ToFind\" \"D:\\myfolder\\LOGS\\myapp\\node*.log*\"
grepp -s \"SomethingINeed To Find\" D:\\myfolder\\LOGS\\myapp\\node*.log
grepp -s SomethingINeedToFind D:\\myfolder\\LOGS\\myapp\\node*.log
grepp -l \"RecordStart\" \"SomethingINeedToFind\" D:\\myfolder\\LOGS\\myapp\\node*.log*
---------------------------
Using on NIX 
grepp --my_predefined_config -d 2011-11-11T11:10;2011-11-11T11:11 myapp.log 
grepp --my_predefined_config -d 2011-11-11T11:10;-10 myapp.log 
grepp --my_predefined_regex_id myapp.log 
grepp 'SomethingINeedToFind' myanotherapp.log 
grepp -s -d 2012-12-12T12;2012-12-12T12:12 'RecordShouldContainThis%and%ShouldContainThisAsWell' thirdapp.log 
grepp -d 2009-09-09T09:00;+ 'RecordShouldContainThis%and%ShouldContainThisAsWell%or%ItCouldContainThis%and%This' thirdapp.log 
grepp -s 'SimplyContainsThis' onemoreapp.log1 onemoreapp.log2 onemoreapp.log3 
cat blabla.txt | grepp -l Chapter 'Once upon a time' > myfavoritechapter.txt
""")
        cli.v("Enforce info to stdout")
        cli.t("Enforce trace to stdout")
        cli.s("Toggles spooling to configured results dir and with configured spooling extension")
        cli.m("Toggles non-stop file traversing")
        cli.h("Print this message")
        cli.l(args:1, argName:"entry_regex", "Tells grepp to split the input in blocks, treating <entry_regex> as a start of the next block (so it's a block end at the same time).\n<entry_regex> - a string which will be used to \"split\" the input. Is optinal, as by default it will be looked up by the filename in config. Anyway, if not found input would be processed by line.")
        cli.p(longOpt:"parse", "Toggles logging .properties file to grepp config parsing")
        cli.e("Toggles thread ID preserving, i.e. all the records for a thread will be fetched")
        cli.d(args:2, valueSeparator:";", argName:"from;to", """Tells grepp to include files/log entries within the supplied timeframe.
            <from to> - string representing date constraints for current search. 
                        Default format is yyyy-mm-ddTHH:MM:ss (could be reduced till yyyy). If <from> or <to> is not known (or is indefinite) '+' can be passed as argument.
                        Date's could be constructed by an offset from NOW or from supplied date. I.e. -d -10;+ will mean 'searching period is last 10 minutes'.
                        E.g. -d 2013-05-01T12:00;-20, -d 2013-05-01T12:00;+20
                        If <from> is after <to> they will be swapped automatically.
                        Usage requires valid date pattern to be configured for such a file in config. Otherwise it won't be applied
""")
        cli.add(args:1, argName:"configId", "Instructs to save given configuraion as a config. <configId> should be unique")
        cli.dateProp(args:2, valueSeparator:";", argName:"format;regex", "Loads date entry filter with <format> (SimpleDateFormat compliant) and <regex> to extract the date from entries")
        cli.threadProp(args:3, valueSeparator:";", argName:"start;skipend;end", "Loads thread filter with <start>, <skipend> (leave as blank if not needed) and <end> regexes")
        cli.repProp(args:1, argName:"type(regex,colName);...", "Loads report filter with <type(regex,colName)> in the given order. Type should be equal to one of the post filter methods. Separate with ';' if multiple columns. You need to escape ',' and ';' with \\ in the <regex> part for correct processing")
        cli.lock("Locks the filter chains after full initialization. I.e. it means if any file processed won't update filter params even if such are configured for it")
        cli.noff("No File Filtering - i.e. turns off file filtering based on date etc.")
        def options = cli.parse(args)
        if (options.h) {
        	cli.usage()
        	println "Press any key to exit"
        	System.in.read()
        	System.exit(0)
        }

        if (options.v) {
        	enforceInfo()
        }
        else if (options.t) {
        	enforceTrace()
        }

        return options
    }

	/**
	 *  Method loads defaults and spooling extension as configured in config.xml's <code>global</code> section.
	 *  Loads some values set via System properties as well.
	 */
	public ConfigObject makeRuntimeConfig() {

        ConfigObject runtimeConfig = new ConfigObject()
        runtimeConfig.spoolFileExtension = config.defaults.spoolFileExtension
        runtimeConfig.resultsDir = config.defaults.resultsDir
		runtimeConfig.spoolFileName = String.format("result_%tY%<tm%<td_%<tH%<tM%<tS", new Date())
		
		if (curWorkDir != null) {
			runtimeConfig.cwd = curWorkDir
		}
		
		def systemSep = System.getProperty("file.separator")
		runtimeConfig.home = System.getProperty(GREPP_HOME_SYSTEM_OPTION) + systemSep
		if ("\\".equals(systemSep)) {
			systemSep += "\\"
		}
		runtimeConfig.folderSeparator = systemSep

		return runtimeConfig
	}


    public ConfigObject makeFilterChains(ConfigObject runtimeConfig, OptionAccessor options) {
        FilterChain<String> entryFilterChain = new FilterChain<String>(config, new StringAggregator(), String.class)
		Deque<ParamParser> varParsers = new ArrayDeque<ParamParser>();

        FilterChain<List<File>> fileFilterChain = new FilterChain<List<File>>(config, new StringAggregator(), new ArrayList<File>().class)
        fileFilterChain.add(fileFilterChain.getInstance(FileSortFilter.class))

		FilterParser filterParser = new FilterParser()
		FileNameParser fileNameParser = new FileNameParser()
		varParsers.addAll([filterParser, fileNameParser])
		def logEntryFilter

		if (options.l) {
			logEntryFilter = entryFilterChain.getInstance(LogEntryFilter.class)
			logEntryFilter.setStarter(options.l)
			logEntryFilter.lock()
			entryFilterChain.add(logEntryFilter)
		}

		if (options.p) {
			varParsers.remove(filterParser)
			entryFilterChain.add(entryFilterChain.getInstance(PropertiesFilter.class))
			entryFilterChain.disableFilter(ReportFilter.class)
			entryFilterChain.disableFilter(SimpleFilter.class)
			entryFilterChain.disableFilter(ThreadFilter.class)
			entryFilterChain.disableFilter(EntryDateFilter.class)
			fileFilterChain.disableFilter(FileDateFilter.class)			
		}
		else {
			entryFilterChain.disableFilter(PropertiesFilter.class)
		}

		if (options.repProp) {
			def reportFilter = entryFilterChain.getInstance(ReportFilter.class)
			reportFilter.setColumnSeparator(config.defaults.reportSeparator.value)
			reportFilter.setSpoolFileExtension(config.defaults.reportSeparator.spoolFileExtension)

			options.repProp.split(/(?<!\\);/).each { prop ->
				def mtchr = prop =~ /(\w+?)\((.*)\)/
				if (mtchr.matches()) {
				    def type = mtchr.group(1)
				    def regexAndColName = mtchr.group(2).split(/(?<!\\),/)
				    reportFilter.addFilterMethodByType(type, regexAndColName[0], (regexAndColName.length > 1) ? regexAndColName[1] : null)
				}
			}			

			reportFilter.lock()
			entryFilterChain.add(reportFilter)
		}

		if (options.e) {
			entryFilterChain.enableFilter(ThreadFilter.class)			
		}
		else {
			entryFilterChain.disableFilter(ThreadFilter.class)	
			entryFilterChain.enableFilter(SimpleFilter.class)
		}

		if (options.d) {
			def dtimeParser = new DateTimeParser()
			log.trace('Got date options: {}', options.ds)
			dtimeParser.parseVar(runtimeConfig, options.ds[0])
			dtimeParser.parseVar(runtimeConfig, options.ds[1])

			if (!options.noff) {
				def fileDateFilter = fileFilterChain.getInstance(FileDateFilter.class)
				fileDateFilter.setFrom(runtimeConfig.dateFilter.from)
				fileDateFilter.setTo(runtimeConfig.dateFilter.to)
				fileFilterChain.add(fileDateFilter)
			}

			def entryDateFilter = entryFilterChain.getInstance(EntryDateFilter.class)
			entryDateFilter.setFrom(runtimeConfig.dateFilter.from)
			entryDateFilter.setTo(runtimeConfig.dateFilter.to)

			if (options.dateProp) {
				entryDateFilter.setLogDateFormat(options.dateProps[0])
				entryDateFilter.setLogDatePattern(options.dateProps[1])
				entryDateFilter.lock()
				if (logEntryFilter == null) { //enabling if null; otherwise it's useless
					logEntryFilter = entryFilterChain.getInstance(LogEntryFilter.class)
					logEntryFilter.lock()
					entryFilterChain.add(logEntryFilter)
				}

				logEntryFilter.setDateRegex(options.dateProps[1])
			}

			entryFilterChain.add(entryDateFilter) //postpone file-specific filter creation
		}
		else {
			entryFilterChain.disableFilter(EntryDateFilter.class)
			fileFilterChain.disableFilter(FileDateFilter.class)
		}

		for (arg in options.arguments()) {
			log.debug("next arg: {}", arg);

			if (arg =~/^-(?![-0-9])/) //such flags should be processed by CliBuilder in parseOptions()
			{
				throw new IllegalArgumentException("Invalid flag: " + arg)
			}

			if (!processConfigId([entryFilterChain, fileFilterChain], arg)) {
				ParamParser<?> paramParser = varParsers.pop()
				if (paramParser instanceof FilterParser) {
					if (entryFilterChain.has(SimpleFilter.class) || entryFilterChain.has(ThreadFilter.class)) {
						paramParser = varParsers.pop() //i.e. skipping filterParser
					}
				}
				if (!paramParser.parseVar(runtimeConfig, arg)) { //pushing back since this parser has more to parse
					varParsers.push(paramParser)
				}
			} 
		}

		if (runtimeConfig.containsKey('filterPattern')) {
			def mainFilter = entryFilterChain.getInstance(SimpleFilter.class)
			mainFilter.setFilterPattern(runtimeConfig.filterPattern)
			if (options.e) {
				if (options.threadProp)	{
					mainFilter.setThreadExtractorList(options.threadProps[0].size() > 0 ? [options.threadProps[0]] : null)
					mainFilter.setThreadSkipEndPatternList(options.threadProps[1].size() > 0 ? [options.threadProps[1]] : null)
					mainFilter.setThreadEndPatternList(options.threadProps[2].size() > 0 ? [options.threadProps[2]] : null)
				}
			}
			entryFilterChain.add(mainFilter)
		}

		if (options.lock) {
			log.trace("Locking filter chains")
			entryFilterChain.lock()
			fileFilterChain.lock()	
		}

		if (entryFilterChain.has(ReportFilter.class)) {
			runtimeConfig.spoolFileExtension = entryFilterChain.get(ReportFilter.class).getSpoolFileExtension()
		}

		runtimeConfig.entryFilterChain = entryFilterChain
		runtimeConfig.fileFilterChain = fileFilterChain

		return runtimeConfig
	}

	public GreppOutput makeOutput(ConfigObject runtimeConfig, FilterChain entryFilterChain, OptionAccessor options) {
		PrintWriter printer = null
		GreppOutput output = null
		if (options.p) {
			log.info("Creating config output")
			output = new ConfigOutput(config, entryFilterChain)
		}
		else if (options.s) {
			log.info("Creating file output")
			printer = getFilePrinter(runtimeConfig)
			output = new SimpleOutput<String>(config, entryFilterChain, printer)
		}
		else {
			log.info("Creating console output")
			printer = getConsolePrinter()
			output = new SimpleOutput<String>(config, entryFilterChain, printer)
		}
		return output
	}

	public DataProcessor makeProcessor(ConfigObject runtimeConfig, GreppOutput output, OptionAccessor options) {
		DataProcessor processor = null
		if (runtimeConfig.data.containsKey('files')) {
			processor = new TextFileProcessor(output, options.m)
			runtimeConfig.data = runtimeConfig.data.files
			
		}
		else {
			processor = new InputStreamProcessor(output)
			runtimeConfig.data = System.in
		}		
		return processor
	}

	public void process(String[] args) {
		def options = parseOptions(args)
		def runtimeConfig = makeRuntimeConfig()
		makeFilterChains(runtimeConfig, options)
		def entryFilterChain = runtimeConfig.entryFilterChain
		def fileFilterChain = runtimeConfig.fileFilterChain

		if (runtimeConfig.data.containsKey('files')) {
        	List<File> filteredData = fileFilterChain.filter(runtimeConfig.data.files)
			if (filteredData != null) {
				runtimeConfig.data.files = filteredData
			}
			else {
				return //nothing to process
			}
		}		

		if (options.add) {
			if (entryFilterChain.configIdExists(options.add) || fileFilterChain.configIdExists(options.add)) {
				println "ConfigId $options.add already exists for a given filter chain; try different one or remove the old one"
				return
			}
		}

		if (options.add) {
			log.info("Saving config to {}", options.add)
			config.merge(entryFilterChain.getAsConfig(options.add))
			config.merge(fileFilterChain.getAsConfig(options.add))
			config.save()
		}

		def output = makeOutput(runtimeConfig, entryFilterChain, options)
		def processor = makeProcessor(runtimeConfig, output, options)
		processor.process(runtimeConfig.data)
	}

	/**
	 * Method for flags and options parsing. It identifies if passed help flag, simple flag or option, and calls appropriate method. Also it removes special symbols - and -- before passing argument further.
	 *
	 * @param arg An argument to be parsed
	 * @return <code>1</code> if <code>arg</code> was processed(i.e. it was a valid arg) <code>0</code> otherwise.
	 */

	protected boolean processConfigId(List<FilterChain> filterChainList, String arg)	{
		if (arg =~ /^--/)
		{
			String wannaBeConfigId = arg.substring(2)
			if (wannaBeConfigId == null) throw new IllegalArgumentException("Invalid option: " + arg)			
			
			boolean isAConfigId = false
			filterChainList.each { filterChain ->
				isAConfigId |= filterChain.refreshByConfigId(wannaBeConfigId)
			}
			
			if (!isAConfigId) throw new IllegalArgumentException("Invalid configId, doesn't match any pre-configured: " + arg)
			return isAConfigId
		}
		else {
			return false //otherwise it's something else and we're letting somebody else to process it
		}
	}

	/**
	*
	* Method enforces TRACE level of logging by resetting logback config and redirects it to STDOUT.
	*/

	protected void enforceTrace()
	{
		log.debug("Enabling trace")
		String traceConfig ="""\
<configuration>

  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <!-- encoders are assigned the type
         ch.qos.logback.classic.encoder.PatternLayoutEncoder by default -->
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <root level="trace">
    <appender-ref ref="STDOUT" />
  </root>
</configuration>
"""
		GreppUtil.resetLogging(traceConfig)
	}

	/**
	*
	* Method enforces INFO level of logging by resetting logback config and redirects it to STDOUT.
	*/

	protected void enforceInfo()
	{
		log.debug("Redirecting info to STDOUT")
		String infoConfig ="""\
<configuration>

  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <!-- encoders are assigned the type
         ch.qos.logback.classic.encoder.PatternLayoutEncoder by default -->
    <encoder>
      <pattern>%msg%n</pattern>
    </encoder>
  </appender>

  <root level="info">
    <appender-ref ref="STDOUT" />
  </root>
</configuration>
"""
		GreppUtil.resetLogging(infoConfig)
	}

       	public static PrintWriter getConsolePrinter() {
		def console = System.console()
		if (console != null) {
			return console.writer()
		}
		else {
			log.debug("There is no associated console to use with this output! Defaulting to System.out.");
			return new PrintWriter(System.out, true)
		}
	}
	
	public static PrintWriter getFilePrinter(ConfigObject runtimeConfig) {
		def outputDir = new File(runtimeConfig.home, runtimeConfig.resultsDir)
		if (!outputDir.exists()) outputDir.mkdir()
		def out_file = new File(outputDir, runtimeConfig.spoolFileName + "." + runtimeConfig.spoolExtension)
		log.trace("Creating new file: {}", out_file.getCanonicalPath())
		out_file.createNewFile()
		return new PrintWriter(new FileWriter(out_file), true) //autoflushing PrintWriter
	}

}
