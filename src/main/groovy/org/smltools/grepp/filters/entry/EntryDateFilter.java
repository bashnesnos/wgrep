package org.smltools.grepp.filters.entry;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.smltools.grepp.config.ConfigHolder;
import org.smltools.grepp.exceptions.ConfigNotExistsRuntimeException;
import org.smltools.grepp.exceptions.PropertiesNotFoundRuntimeException;
import org.smltools.grepp.exceptions.TimeToIsOverduedException;
import org.smltools.grepp.filters.OptionallyStateful;
import org.smltools.grepp.filters.FilterParams;
import org.smltools.grepp.filters.StatefulFilterBase;
import org.smltools.grepp.filters.enums.Event;
import groovy.util.ConfigObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Class provides entry date filtering for supplied FROM and TO dates.
 * 
 * @author Alexander Semelit
 * 
 */

@FilterParams(configIdPath = ConfigHolder.SAVED_CONFIG_KEY + "|" + EntryDateFilter.LOG_DATE_FORMATS_KEY, mandatoryProps = {ConfigHolder.SAVED_CONFIG_DATE_FORMAT_KEY, ""}, order = 15)
public class EntryDateFilter extends StatefulFilterBase<String> implements OptionallyStateful<String> {
	private static final Logger LOGGER = LoggerFactory.getLogger(EntryDateFilter.class);
	public static final String LOG_DATE_FORMATS_KEY = "logDateFormats";

	private boolean isStateOptional;
	private Date from;
	private Date to;
	private boolean isDateFromPassed = false;
	private Pattern logDatePtrn = null;
	private SimpleDateFormat logDateFormat;

	/**
	 * Creates non-refreshable and non-publicly modifiable, standalone and stateless EntryDateFilter
	 * @param logDatePtrn
	 *            pattern to slice data for entries
	 */
	public EntryDateFilter() {		
		isStateOptional = true;
	}

	@Override
	public void setConfig(Map<?, ?> config) {
		super.setConfig(config);
		isStateOptional = false;
	}

    public void setFrom(Date from) {
    	this.from = from;
    }

    public void setTo(Date to) {
    	this.to = to;
    }

	public void setLogDatePattern(String logDatePtrn) {
		if (logDatePtrn != null) {
			this.logDatePtrn = Pattern.compile(logDatePtrn);	
		}
		else {
			throw new IllegalArgumentException("logDatePtrn was not supplied");
		}
	}

	public String getLogDatePattern() {
		if (logDatePtrn != null) {
			return logDatePtrn.pattern();
		}
		else {
			return null;
		}
	}

	public void setLogDateFormat(String logDateFormat) {
		if (logDateFormat != null) {
			this.logDateFormat = new SimpleDateFormat(logDateFormat);
		}	
		else {
			throw new IllegalArgumentException("logDateFormat was not supplied");
		}
	}

	@SuppressWarnings("unchecked")
	@Override
    public boolean fillParamsByConfigId(String configId) {
    	if (!configIdExists(configId)) {
    		throw new ConfigNotExistsRuntimeException(configId);
    	}
    	this.configId = configId;

    	Map<?, ?> configs = (Map<?,?>) config.get(ConfigHolder.SAVED_CONFIG_KEY);
    	Map<?, ?> customCfg = (Map<?,?>) configs.get(configId);

		if (customCfg != null && customCfg.containsKey(ConfigHolder.SAVED_CONFIG_DATE_FORMAT_KEY))	{
			Map<?,?> dateFormatProps = (Map<?, ?>) customCfg.get(ConfigHolder.SAVED_CONFIG_DATE_FORMAT_KEY);
			if (dateFormatProps.containsKey(ConfigHolder.SAVED_CONFIG_DATE_FORMAT_REGEX_KEY)) {
				logDatePtrn = Pattern.compile((String) dateFormatProps.get(ConfigHolder.SAVED_CONFIG_DATE_FORMAT_REGEX_KEY));
			}
			else {
				throw new PropertiesNotFoundRuntimeException(ConfigHolder.SAVED_CONFIG_DATE_FORMAT_KEY + "." + ConfigHolder.SAVED_CONFIG_DATE_FORMAT_REGEX_KEY + " is not filled for config: " + configId);
			}

			if (dateFormatProps.containsKey(ConfigHolder.SAVED_CONFIG_DATE_FORMAT_VALUE_KEY)) {
				logDateFormat = new SimpleDateFormat((String) dateFormatProps.get(ConfigHolder.SAVED_CONFIG_DATE_FORMAT_VALUE_KEY));
			}
			else {
				throw new PropertiesNotFoundRuntimeException(ConfigHolder.SAVED_CONFIG_DATE_FORMAT_KEY + "." + ConfigHolder.SAVED_CONFIG_DATE_FORMAT_REGEX_KEY + " is not filled for config: " + configId);
			}
			return true;
		}
		else {
			LOGGER.debug(ConfigHolder.SAVED_CONFIG_DATE_FORMAT_KEY + " is not filled for config: " + configId);
		}

		configs = (Map<?, ?>) config.get(LOG_DATE_FORMATS_KEY);
		customCfg = (Map<?,?>) configs.get(configId);

		if (customCfg.containsKey(ConfigHolder.SAVED_CONFIG_DATE_FORMAT_REGEX_KEY)) {
			logDatePtrn = Pattern.compile((String) customCfg.get(ConfigHolder.SAVED_CONFIG_DATE_FORMAT_REGEX_KEY));
		}
		else {
			throw new PropertiesNotFoundRuntimeException(LOG_DATE_FORMATS_KEY + "." + configId + "." + ConfigHolder.SAVED_CONFIG_DATE_FORMAT_REGEX_KEY + " is not filled for config!");
		}

		if (customCfg.containsKey(ConfigHolder.SAVED_CONFIG_DATE_FORMAT_VALUE_KEY)) {
			logDateFormat = new SimpleDateFormat((String) customCfg.get(ConfigHolder.SAVED_CONFIG_DATE_FORMAT_VALUE_KEY));
		}
		else {
			throw new PropertiesNotFoundRuntimeException(LOG_DATE_FORMATS_KEY + "." + configId + "." + ConfigHolder.SAVED_CONFIG_DATE_FORMAT_REGEX_KEY + " is not filled for config!");
		}
		return true;
    }

	@SuppressWarnings("unchecked")
    @Override
    public ConfigObject getAsConfig(String configId) {
        if (configId == null) {
            if (this.configId == null) {
                throw new IllegalArgumentException("Can't derive configId (none was supplied)");
            }
            else {
                configId = this.configId;
            }
        }

        ConfigObject result = new ConfigObject();
    	ConfigObject logDateFormats = (ConfigObject) result.getProperty(LOG_DATE_FORMATS_KEY);
    	ConfigObject config = (ConfigObject) logDateFormats.getProperty(configId);

    	config.put(ConfigHolder.SAVED_CONFIG_DATE_FORMAT_VALUE_KEY, logDateFormat.toPattern());
		config.put(ConfigHolder.SAVED_CONFIG_DATE_FORMAT_REGEX_KEY, logDatePtrn.pattern());    	

    	ConfigObject savedConfigs = (ConfigObject) result.getProperty(ConfigHolder.SAVED_CONFIG_KEY);
    	((ConfigObject) savedConfigs.getProperty(configId)).put(ConfigHolder.SAVED_CONFIG_DATE_FORMAT_KEY, config);
    	return result;
	}

	/**
	 * Checks if supplied entry suits desired from and to date and time.
	 * 
	 * @param entry
	 *            A String to be checked
	 * @throws IllegalArgumentException
	 *             if supplied blockData is not String
	 * @throws TimeToIsOverduedException
	 *             if to was passed
	 */

	@Override
	public String filter(String blockData) throws TimeToIsOverduedException {
		if (from == null && to == null) {
			throw new IllegalStateException("Either 'from' or 'to' should be supplied to the filter");
		}

		if (blockData != null && logDatePtrn != null && logDateFormat != null) {

			Date entryDate = null;

			if (!isDateFromPassed || to != null) {
				String timeString;

				if (LOGGER.isTraceEnabled())
					LOGGER.trace("Checking log entry {} for log date pattern |{}| and formatting to |{}|"
						, blockData, logDatePtrn, logDateFormat.toPattern());

				Matcher entryDateMatcher = logDatePtrn.matcher(blockData);
				if (entryDateMatcher.find()) {
					timeString = entryDateMatcher.group(1);
				} 
				else {
					if (LOGGER.isTraceEnabled())
						LOGGER.trace("No signs of time in here");
					return null;
				}

				try {
					entryDate = logDateFormat.parse(timeString);
				} 
				catch (ParseException e) {
					throw new RuntimeException(e); //re-throwing as unchecked exception, as it will mean that date time config is invalid 
				}
				
			} 
			else {
				if (LOGGER.isTraceEnabled())
					LOGGER.trace("Date check was skipped after dateFromPassed={}, to={}", isDateFromPassed, to);
				return blockData;
			}

			if (entryDate != null && (from == null || !entryDate.before(from))) {
				
				if (isStateful()) {
					isDateFromPassed = true;
				}

				if (to != null) {
					if (!entryDate.after(to)) {
						if (LOGGER.isTraceEnabled()) {
							LOGGER.trace("Passed to");
						}
						return blockData;
					} 
					else {
						if (LOGGER.isTraceEnabled()) {
							LOGGER.trace("Not passed");
						}
						throw new TimeToIsOverduedException(logDateFormat.format(entryDate));
					}
				}
				if (LOGGER.isTraceEnabled())
					LOGGER.trace("Passed from only");
				return blockData;
			} 
			else {
				if (LOGGER.isTraceEnabled()) LOGGER.trace("Not passed");
				return null;
			}
		}
		if (LOGGER.isTraceEnabled())
			LOGGER.trace("Date check was totally skipped, logDatePtrn={}", logDatePtrn);
		return blockData;
	}

	/**
	 * Flushes all state
	 * 
	 */
	@Override
	public void flush() {
        isDateFromPassed = false;
    }

    @Override
	public boolean isStateful() {
		return isStateOptional;
	}

	/**
	 * Listens for CHUNK_ENDED event. Cleans isDateFromPassed in that case.
	 * 
	 */
	@Override
	protected String processEventInternal(Event event) {
		switch (event) {
			case CHUNK_ENDED:
				flush();
			default:
				return null;
		}
	}

}