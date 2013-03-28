package org.smlt.tools.wgrep

class DateTimeVarParser extends AdditionalVarParser
{

    def parseVar(def arg)
    {
        if (isTraceEnabled()) trace("Additional var: " + arg)
        if (getFacade().getExtraParam('FROM_DATE') == null) setDateFrom(arg)
        else if (getFacade().getExtraParam('TO_DATE') == null) 
        {
            setDateTo(arg)
            getFacade().unsubscribeVarParsers([this])
        }
    }

    def setDateFrom(def date)
    {
        getFacade().setExtraParam('FROM_DATE', date)
    }

    def setDateTo(def date)
    {
        getFacade().setExtraParam('TO_DATE', date)
    }
}