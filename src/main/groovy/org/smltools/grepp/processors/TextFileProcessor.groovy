package org.smltools.grepp.processors

import groovy.util.logging.Slf4j;

import java.util.regex.Matcher
import java.lang.StringBuilder
import org.smltools.grepp.filters.FilterChain
import org.smltools.grepp.filters.enums.Event
import org.smltools.grepp.output.GreppOutput
import org.smltools.grepp.processors.DataProcessor;
import org.smltools.grepp.exceptions.*

/**
 * Class which triggers and controls text file processing.
 * 
 * @author Alexander Semelit
 *
 */
@Slf4j
class TextFileProcessor implements DataProcessor<List<File>>
{
   
    private boolean isMerging;
	private GreppOutput<String> output;
	private FilterChain<List<File>> fileFilter;
 
	/**
	 * Create new instance with supplied filter chains and {@link WgrepConfig} instance.
	 * 
	 * @param config WgrepConfig instance which will be used as param source
	 * @param filterChain_ FilterBase chain which will be used to filter each file line
	 * @param filesFilterChain_ FilterBase chain which will be used to filter filename List
	 */
    TextFileProcessor(GreppOutput<String> output_, FilterChain<List<File>> fileFilter_, boolean isMerging_) 
    {
		output = output_
        isMerging = isMerging_
		fileFilter = fileFilter_
    }


	/**
	 * Hook method which is called prior to file processing. Needed for check and configInstance refreshing if it is on. 
	 * 
	 * @param file_ a File instance which is needed to be initialized.
	 * @return File instance if it was successfully initialized. null otherwise
	 */
    private File initFile(File file_)
    {
        log.info("Initializating {}", file_.name)
		output.refreshFilters(file_.name)
        return file_
    }

	/**
	 * Method which does processing of one File, which contains lines. <br>
	 * 
	 * @param data a File which needs to be processed
	 */
    void processSingleFile(File data)
    {
        if (data == null) return
        def curLine = 0
        GreppOutput output = output //shadowing to get rid of GetEffectivePogo in the loop
        try {
            data.eachLine { String line ->
                log.trace("curLine: {}", curLine)
                curLine += 1
                output.printToOutput(line)
            }
        }
        catch(FilteringIsInterruptedException e) {
            log.trace("No point to read file further as identified by filter chain")
        }
        finally {
            if (!isMerging) output.processEvent(Event.CHUNK_ENDED)
            log.info("File ended. Lines processed: {}", curLine)
        }
    }

	@Override
	public void process(List<File> data) {
        List<File> filteredData = fileFilter.filter(data)
		if (filteredData != null) {
			filteredData.each {
				processSingleFile(initFile(it))
			}
			output.processEvent(Event.ALL_CHUNKS_PROCESSED)
			output.closeOutput()
		}
	}
}