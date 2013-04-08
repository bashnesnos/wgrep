package org.smlt.tools.wgrep.filters

import java.util.regex.Matcher
import org.smlt.tools.wgrep.WgrepConfig
import org.smlt.tools.wgrep.filters.enums.*

import groovy.util.logging.Slf4j;
import groovy.xml.dom.DOMCategory

@Slf4j
class ComplexFilter extends FilterBase {

    //Complex pattern processing and stuff
    StringBuilder PATTERN = new StringBuilder("(?ms)") //for multiline support
    List EXTNDD_PTTRNS = []
    Map EXTNDD_PTTRN_DICT = [:]

    String pt_tag = null
    Map THRD_START_EXTRCTRS =[:]
    List THRD_START_PTTRNS = []
    List THRD_SKIP_END_PTTRNS = []
    List THRD_END_PTTRNS =[]

    ComplexFilter(FilterBase nextFilter_, WgrepConfig config)
    {
        super(nextFilter_, config)
		setPattern(getFilterPattern())
        log.trace("Added on top of " + nextFilter.getClass().getCanonicalName())
        pt_tag = getParam('PRESERVE_THREAD')
        use(DOMCategory)
        {
            if (pt_tag != null)
            {
                def extrctrs = getRoot().custom.thread_configs.extractor.findAll { it.'@tags' =~ pt_tag }
                extrctrs.each { THRD_START_EXTRCTRS[it.text()] = it.'@qlfr' }
                def pttrns = getRoot().custom.thread_configs.pattern.findAll { it.'@tags' =~ pt_tag } 
                pttrns.each { this."${it.'@clct'}".add(it.text()) }
            }
        }
        processExtendedPattern(filterPtrn)
    }

    void addExtendedFilterPattern(String val, String qualifier)
    {
      log.trace("adding complex pattern: val=" + val + " qual=" + qualifier)
      
      if (qualifier != null) PATTERN = PATTERN.append(Qualifier.valueOf(qualifier).getPattern())
      PATTERN = PATTERN.append(val)
      
      EXTNDD_PTTRNS.add(val)
      EXTNDD_PTTRN_DICT[val] = qualifier ? Qualifier.valueOf(qualifier) : null
      
      log.trace(EXTNDD_PTTRNS.toString())
      log.trace(EXTNDD_PTTRN_DICT.toString())
    }

    void removeExtendedFilterPattern(String val)
    {
      Qualifier qlfr = EXTNDD_PTTRN_DICT[val]
      String ptrn = (qlfr ? qlfr.getPattern() : '') + val
      int ptrnIndex = PATTERN.indexOf(ptrn)
      log.trace('to delete:/' + ptrn +'/ index:' + ptrnIndex)
      if (ptrnIndex != -1)
      {
        PATTERN = PATTERN.delete(ptrnIndex, ptrnIndex + ptrn.length())
        EXTNDD_PTTRNS.remove(val)
        EXTNDD_PTTRN_DICT.remove(val)
      }
    }

    void processExtendedPattern(String val)
    {
        String filterPattern = null
        String qRegex = ""
        Qualifier.each { qRegex += '%' + it + '%|' }
        qRegex = qRegex[0..qRegex.size()-2] //removing last |
        Matcher qualifierMatcher = (val =~ /$qRegex/) //matching any qualifiers with % signs
        if (qualifierMatcher.find())
        {
            log.trace('Processing complex pattern')
            List tokens = val.tokenize("%")
            String nextQualifier = null
            if (tokens != null)
            {
                qRegex = qRegex.replaceAll(/%/, "") //matching only qualifier names
                for (grp in tokens)
                {
                    log.trace('Next group in match: ' + grp)
                    qualifierMatcher = (grp =~ /$qRegex/)
                    if (qualifierMatcher.find())
                    {
                        nextQualifier = qualifierMatcher[0]
                        continue
                    }

                    addExtendedFilterPattern(grp, nextQualifier)
                    nextQualifier = null

                }
            }
            else throw new IllegalArgumentException('Check your complex pattern:/' + val + '/')
        }
        else 
        {
            log.trace('No extended pattern supplied, might be a preserve thread')
            addExtendedFilterPattern(val, null)
        }
    }

    /**
    * Method for complex pattern processing.
    * <p> 
    * Is called against each block.
    *
    * @param data A String to be filtered.
    *
    */

    def filter(def data)
    {
        setPattern(PATTERN.toString())

        Matcher blockMtchr = data =~ filterPtrn
        if (blockMtchr.find())
        {
            if (isThreadPreserveEnabled())
            {
                extractThreadPatterns(data)
            }
            
            super.filter(data)
        }
        else 
        {
            log.trace("not passed")
        }
    }

    boolean isThreadPreserveEnabled()
    {
        return pt_tag != null
    }

    void extractThreadPatterns(String data)
    {
        if (searchThreadEnds(data)) 
        { 
            extractThreadStarts(data, "removeThreadStart")
        }
        else
        {
            log.trace("Thread continues. Keeping starts")
            extractThreadStarts(data, "addThreadStart")
        }
    }

    void extractThreadStarts(String data, String method)
    {
        THRD_START_EXTRCTRS.each
        {extrctr, qlfr -> 
            log.trace(extrctr);
            Matcher extractorMatcher = (data =~ extrctr);
            if (extractorMatcher)
            {
                def start = extractorMatcher[0]
                log.trace("extracted; " + start)
                this."$method"(start, qlfr)
            }
        }
    }

    boolean searchThreadEnds(String data)
    {
        if (!shouldBeSkipped(data))
        {
            def decision = THRD_END_PTTRNS.find
            { thrend ->
                log.trace("thrend ptrn: " + thrend);
                data =~ thrend
            }
            return decision != null
        }
        return false
    }

    boolean shouldBeSkipped(String data)
    {
        def decision = THRD_SKIP_END_PTTRNS.find
        {skip->
            log.trace("skip ptrn: " + skip)
            data =~ skip
        }
        return decision != null
    }

    void addThreadStart(String start, String qlfr)
    {
      log.trace("adding thread start: " + start);
      if (!THRD_START_PTTRNS.contains(start))
      {
        THRD_START_PTTRNS.add(start)
        addExtendedFilterPattern(start, qlfr)
      }
      else log.trace("Start exists")
    }

    void removeThreadStart(String start, String qlfr)
    {
      log.trace("removing thread start: " + start);
      THRD_START_PTTRNS.remove(start);
      removeExtendedFilterPattern(start);
    }

}