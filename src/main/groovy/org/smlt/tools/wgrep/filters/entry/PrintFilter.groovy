package org.smlt.tools.wgrep.filters.entry

import groovy.util.logging.Slf4j
import org.smlt.tools.wgrep.filters.FilterBase

@Slf4j
class PrintFilter extends FilterBase{

	PrintFilter(FilterBase nextFilter_) {
		super(nextFilter_)
	}

    /**
    * Default filter method. Prints passed data block.
    * <p> 
    * Is considered to be last in line.
    *
    * @param blockData A String to be filtered.
    */

	
	
    @Override
	boolean check(def blockData) {
		return blockData != null
	}

	@Override
	void beforePassing(def blockData) {
		println blockData
	}

}