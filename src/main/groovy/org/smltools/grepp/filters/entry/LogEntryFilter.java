package org.smltools.grepp.filters.entry;

import java.util.Map;
import java.util.regex.Pattern;
import org.smltools.grepp.config.ConfigHolder;
import org.smltools.grepp.exceptions.ConfigNotExistsRuntimeException;
import org.smltools.grepp.exceptions.PropertiesNotFoundRuntimeException;
import org.smltools.grepp.filters.StatefulFilterBase;
import org.smltools.grepp.filters.enums.Event;

/**
 * Class which provide grouping lines into log entries. <br>
 * Each entry has some pattern which defines it's start, and generally each
 * entry's start is another one's end and vice-versa. <br>
 * This filter works on that principle.
 * 
 * @author Alexander Semelit
 */

public final class LogEntryFilter extends StatefulFilterBase<String> {
	private boolean isBlockMatched = false;
	private StringBuilder curBlock = new StringBuilder();
	private Pattern logEntryPtrn = null;

	/**
	 * Creates non-refreshable and non-publicly modifiable, standalone LogEntryFilter
	 * @param logEntryPtrn
	 *            pattern to slice data for entries
	 */
	public LogEntryFilter(String logEntryPtrn) {
		super(LogEntryFilter.class);
		LOGGER.debug("Entry pattern :/{}/", logEntryPtrn);
		this.logEntryPtrn = Pattern.compile(logEntryPtrn);
        this.setState(null);
        flush();
	}

	/**
	* Creates LogEntryFilter from config
	*
	*/
	public LogEntryFilter(Map<?, ?> config, String configId) {
		super(LogEntryFilter.class, config);
		fillParamsByConfigIdInternal(configId);
	}

	@SuppressWarnings("unchecked")
	@Override
    protected boolean fillParamsByConfigIdInternal(String configId) {
    	if (!LogEntryFilter.configIdExists(config, configId)) {
    		throw new ConfigNotExistsRuntimeException(configId);
    	}

    	Map<?, ?> configs = (Map<?,?>) config.get(ConfigHolder.SAVED_CONFIG_KEY);
    	Map<?, ?> customCfg = (Map<?,?>) configs.get(configId);

		String starter = null;
		String dateRegex = null;

		if (customCfg.containsKey(ConfigHolder.SAVED_CONFIG_STARTER_KEY))
		{
			starter = (String) customCfg.get(ConfigHolder.SAVED_CONFIG_STARTER_KEY);
		}

		if (customCfg.containsKey(ConfigHolder.SAVED_CONFIG_DATE_FORMAT_KEY))
		{
			dateRegex = (String) ((Map<?, ?>) customCfg.get(ConfigHolder.SAVED_CONFIG_DATE_FORMAT_KEY)).get(ConfigHolder.SAVED_CONFIG_DATE_FORMAT_REGEX_KEY);
		}
		
		if (starter != null || dateRegex != null) 
		{
			logEntryPtrn = Pattern.compile((starter != null ? starter : "") + (dateRegex != null ? dateRegex : "" ));
			LOGGER.debug("Entry pattern :/{}/", logEntryPtrn.pattern());
			return true;
		}
		else
		{
			throw new PropertiesNotFoundRuntimeException("Either " + ConfigHolder.SAVED_CONFIG_STARTER_KEY + " or " + ConfigHolder.SAVED_CONFIG_DATE_FORMAT_KEY + "." + ConfigHolder.SAVED_CONFIG_DATE_FORMAT_REGEX_KEY + " should be filled for config: " + configId);
		}
    }

    @SuppressWarnings("unchecked")
	public static boolean configIdExists(Map<?, ?> config, String configId) {
		Map<?, ?> configs = (Map<?,?>) config.get(ConfigHolder.SAVED_CONFIG_KEY);
		if (configs != null) {
			return configs.containsKey(configId);
		}
		else {
			return false;
		}
	}

	/**
	 * Overrided check method. Once log entry start pattern was matched it
	 * accumulates data coming through until another entry start would be found. <br>
	 * Once it was found, it passes all the data which was accumulated to the
	 * next filter. <br>
	 * 
	 * @param blockData
	 *            A String to be checked.
	 */

	@Override
    public String filter(String blockData) {
  		if ( logEntryPtrn.matcher(blockData).find() ) //finding match of current blockData
  		{
  			if (!isBlockMatched)
  			{
  				isBlockMatched = true;
  				if (LOGGER.isTraceEnabled()) LOGGER.trace("appending");
  				appendCurBlock(blockData);
  			}
  			else if (isBlockMatched)
  			{
  				if (LOGGER.isTraceEnabled()) LOGGER.trace("returning block");
				String passingVal = curBlock.toString();
        		startNewBlock(blockData);
  				return passingVal;
  			}
  		}
  		else if (isBlockMatched)
  		{
  			if (LOGGER.isTraceEnabled()) LOGGER.trace("appending");
  			appendCurBlock(blockData);
  		}
  		return null;
    }

	/**
	 * Implementation of appender to accumulate lines
	 * 
	 * @param line
	 *            String to be appended
	 */

	private void appendCurBlock(String line)
    {
        if (curBlock != null)
        {
            if (curBlock.length() != 0) curBlock = curBlock.append('\n');
            curBlock = curBlock.append(line);
        }
    }

	/**
	 * Implementation of appender to start accumulating new block and to clear
	 * out previous block data from buffer.
	 * 
	 * @param line
	 *            String to be appended. If null was supplied simply clears
	 *            previous data from buffer
	 */

	private void startNewBlock(String line)
    {
        flush();
        if (line != null)
        {
            if (LOGGER.isTraceEnabled()) LOGGER.trace("appending end, since it is the start of new block");
            appendCurBlock(line);
        }
    }

	/**
	 * Flushes all state
	 * 
	 */
	@Override
	public void flush() {
        curBlock.setLength(0);
    }

	/**
	 * 
	 * Listens for CHUNK_ENDED event to return current accumulated block
	 */

	@Override
	protected String processEventInternal(Event event) {
        switch (event)
        {
            case CHUNK_ENDED: {
				String passingVal = curBlock.toString();
        		flush();
  				return passingVal;
            }
            default: {
            	return null;
            }
        }
    }

}