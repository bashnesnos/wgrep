package org.smlt.tools.wgrep.filters.logfile

import java.text.SimpleDateFormat
import java.util.regex.Matcher

import groovy.util.logging.Slf4j
import groovy.xml.dom.DOMCategory
import org.smlt.tools.wgrep.config.WgrepConfig
import org.smlt.tools.wgrep.filters.enums.Event
import org.smlt.tools.wgrep.filters.FilterBase

/**
 * Provides filtering of supplied files by last modified date. <br>
 * If it does not comply to from date and to date + configured file threshold, a file would be skipped. 
 * 
 * @author Alexander Semelit
 *
 */

class FileDateFilter extends FilterBase
{
    //Checking dates everywhere

    protected SimpleDateFormat FILE_DATE_FORMAT = null
    protected List fileList = []
    protected List INPUT_DATE_PTTRNS = []
    protected Date FROM_DATE = null
    protected Date TO_DATE = null
    protected int LOG_FILE_THRESHOLD = 24
    protected int LOG_FILE_THRESHOLD_MLTPLR = 60*60*1000

    FileDateFilter(FilterBase nextFilter_, WgrepConfig config)
    {
        super(nextFilter_, FileDateFilter.class)
        FROM_DATE = config.getParam('FROM_DATE')
        TO_DATE = config.getParam('TO_DATE')
        FILE_DATE_FORMAT = new SimpleDateFormat(config.getParam('FILE_DATE_FORMAT'))
		def trshld = config.getParam('LOG_FILE_THRESHOLD') 
        if (trshld != null) LOG_FILE_THRESHOLD = Integer.valueOf(trshld)
    }
	
	
	/**
    * Checks supplied files if they exceed supplied time boundaries. <br>
    * 
    * @param files List to check
    * @return passed files List
    * @throws IllegalArgumentException if supplied argument is not instanceof List<Files>
    */
    @Override
    boolean check(def files) {
        if (! files instanceof List<File> ) throw new IllegalArgumentException("FileDateFilter accepts file list only")
        fileList = [] //invalidating fileList
        if (log.isTraceEnabled()) log.trace("total files:" + files.size())
        fileList = files.findAll { file -> checkFileTime(file) }
        return fileList != null && fileList.size() > 0
    }

    /**
     *
	 * Passes next filtered collection.
     *
	 * @return <code>super.passNext</code> result
	 */
	@Override
    void beforePassing(def files) {
        passingVal = fileList
    }

    /**
     * Checks if supplied files fits into configured time boundaries
     * 
     * @param file to be checked
     * @return true if it fits, false otherwise
     */
	boolean checkFileTime(File file)
    {
        if (file == null) return
        Date fileTime = new Date(file.lastModified())
        if (log.isTraceEnabled()) log.trace("fileTime:" + FILE_DATE_FORMAT.format(fileTime))
        if (log.isTraceEnabled()) log.trace("Checking if file suits FROM " + FROM_DATE == null ? null : FILE_DATE_FORMAT.format(FROM_DATE))
        if (FROM_DATE == null || FROM_DATE.compareTo(fileTime) <= 0)
        {
            if (TO_DATE != null)
            {
                if (log.isTraceEnabled()) log.trace(" Checking if file suits TO " +  FILE_DATE_FORMAT.format(TO_DATE))
                if (TO_DATE.compareTo(fileTime) >= 0)
                {
                    return true
                }
                if (TO_DATE.compareTo(fileTime) < 0)
                {
                    if (log.isTraceEnabled()) log.trace("Passed TO_DATE")
                    if (fileTime.before(new Date(TO_DATE.getTime() + LOG_FILE_THRESHOLD*LOG_FILE_THRESHOLD_MLTPLR))) return file
                    else
                    {
                        if (log.isTraceEnabled()) log.trace("File is too far")
                        return false
                    }
                }
            }
            if (log.isTraceEnabled()) log.trace("Passed FROM_DATE only")
            return true
        }
        else
        {
            if (log.isTraceEnabled()) log.trace("Not passed")
            return false
        }
    }

}