package org.smltools.grepp.cli.varparsers

import java.io.File;
import java.util.List;
import java.util.Map;

import groovy.util.ConfigObject;
import groovy.util.logging.Slf4j;

/**
 * Provides file name parameter parsing. <br>
 * Simply adds supplied argument to FILES parameter of WgrepConfig instance
 * 
 * @author Alexander Semelit
 *
 */
@Slf4j("LOGGER")
class FileNameParser implements ParamParser<String> {
	private static final String FOLDER_SEPARATOR_KEY = "folderSeparator"
	private static final String FILES_KEY = "files"
	@Override
	public boolean parseVar(ConfigObject config, String fileName) {
		return parseVarInternal(config, fileName)
	}

	private boolean parseVarInternal(ConfigObject config, String fileName) {
		List<File> fileList = []
		def fSeparator = config."$FOLDER_SEPARATOR_KEY"
		def curDir = config.containsKey('cwd') ? config.cwd : null

		LOGGER.trace("analyzing supplied file: {}", fileName)
		if (fileName =~ /\*/) {
			//filename contains asterisk, should be a multi-file pattern
			String flname = fileName
			if (fileName =~ fSeparator) {
				if (curDir == null) { 
					curDir = new File((fileName =~/.*(?=$fSeparator)/)[0])
				}
				else {
					LOGGER.debug("Directory is limited to {}", curDir.getAbsolutePath())
				}
				flname = (fileName =~ /.*$fSeparator(.*)/)[0][1]
			}
			List<File> files = curDir.listFiles()
			LOGGER.trace("files found {}", files)
			String ptrn = flname.replaceAll(/\*/) {it - '*' + '.*'}
			LOGGER.trace("matching ptrn {}", ptrn)
			files.each { file ->
				if (file.name ==~ /$ptrn/) {
					LOGGER.trace("adding file {}", file)
					if (!file.isDirectory()) {
						fileList.add(file)
					}
					else {
						fileList.addAll(file.listFiles() as List<File>)
					}
				}
			}
		}
		else { //all good seems to be a normal file, just adding it
			if (curDir != null) {
				LOGGER.debug("Limiting directory to {}", curDir.getAbsolutePath())
				if (fileName =~ fSeparator) {
					fileName = (fileName =~ /.*$fSeparator(.*)/)[0][1]
				}
				fileName = "${curDir.getAbsolutePath()}$fSeparator$fileName"
			}
			File curFile = new File(fileName)
			if (!curFile.isDirectory()) {
				fileList.add(curFile)
			}
			else {
				fileList.addAll(curFile.listFiles() as List<File>)
			}
		}
		
		List<File> files = config.data.containsKey(FILES_KEY) ? config.data.files : null
		if (files != null) {
			files.addAll(fileList)
		}
		else {
			config.data.files = fileList
		}
		
		// Never unsubscribes, since there could be supplied more than one filename.
		return false;
	}
}