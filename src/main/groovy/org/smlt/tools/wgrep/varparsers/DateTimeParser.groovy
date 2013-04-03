package org.smlt.tools.wgrep.varparsers

import java.text.SimpleDateFormat
import groovy.xml.dom.DOMCategory

class DateTimeParser extends DefaultVarParser
{
    private def INPUT_DATE_PTTRNS = []
    private boolean isFromParsed = false
    private boolean isToParsed = false

    DateTimeParser(def dt_tag)
    {
        if (dt_tag == null) throw new IllegalArgumentException("There should be some date time tag specified")
        use(DOMCategory)
        {
            def ptrns = getRoot().date_time_config.pattern.findAll { it.'@tags' =~ dt_tag }
            ptrns.sort { it.'@order' }.each {INPUT_DATE_PTTRNS.add(it.text())}
        }
    }

    def parseVar(def arg)
    {
        if (isTraceEnabled()) trace("Additional var: " + arg)
        if (!isFromParsed) setDateFrom(arg)
        else if (!isToParsed) 
        {
            setDateTo(arg)
            getFacade().unsubscribeVarParsers([this])
        }
    }

    def parseInput(def dateStr)
    {
        def date = null
        INPUT_DATE_PTTRNS.find { ptrn -> 
            if (isTraceEnabled()) trace("trying date pattern="+ ptrn); 
            try {
                date = (new SimpleDateFormat(setFileDateFormat(ptrn))).parse(dateStr) 
                if (isTraceEnabled()) trace("Pattern found")
                true
            }
            catch(java.text.ParseException e)
            {
                false
            }
        }
        if (date != null) return date 
        else null
    }

    def setDateFrom(def date)
    {
        if (date != "+") getFacade().setParam('FROM_DATE', parseInput(date))
        isFromParsed = true
    }

    def setDateTo(def date)
    {
        if (date != "+") getFacade().setParam('TO_DATE', parseInput(date))
        isToParsed = true
    }

    def setFileDateFormat(def format)
    {
        if (isTraceEnabled()) trace("FILE_DATE_FORMAT set to " + format)
        getFacade().setParam('FILE_DATE_FORMAT', format)
        return format
    }

}