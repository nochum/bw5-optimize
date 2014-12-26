package nochum.bw5.utilities;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.zip.GZIPInputStream;


/**
 * After running the BusinessWorks project that was previously instrumented
 * using the <nochum.bw5.utilities.Optimize instrument> option, a directory
 * is populated with JVM heap histogram profile snapshots taken at the
 * start and end of every BW process that was invoked.  The "instrument"
 * tool places these snapshots in the location specified by the -output
 * argument.
 * <p>
 * This class reads all of the JVM heap histogram profile snapshots that the
 * "instrument tool" created, and creates a summary report that shows the
 * total number of live objects allocated and the total memory utilization
 * by live objects on the JVM heap at the beginning and at the end of each
 * BusinessWorks process invocation.  This allows the user to pinpoint
 * exactly where memory spikes are taking place.    
 * 
 * @author Nochum Klein
 * @version %I%, %G%
 */ 
public class HistoSummary {
	private BufferedWriter bw = null;
	/**
	 * The properties required to invoke Hawk and traverse the project filesystem
	 */
	private Properties _props = null;
	
	public HistoSummary(Properties props) throws IOException, UsageException {
		final String usageString = "\nUsage: Optimize histoSummary [options]\n\n" +
				"   where options are:\n\n" +
				"-output    <output location>    - Full path and file name for output report.\n" +
				"-profiles  <profile location>   - Full path to the directory containing the output from the profiling.\n"
				;
		
		_props = props;

		if (!_props.containsKey("profileLoc")) {
			throw(new UsageException("Required input parameter '-profiles' has not been specified.", usageString));
		}

		if (!_props.containsKey("outLoc")) {
			throw(new UsageException("Required input parameter '-output' has not been specified.", usageString));
		}

		File summaryFile = new File(_props.getProperty("outLoc"));
		FileWriter fw = new FileWriter(summaryFile.getAbsoluteFile());
		bw = new BufferedWriter(fw);
		
		processHistoFiles(_props.getProperty("profileLoc"));
		
		bw.close();
	}
	
	private void processHistoFiles(String histoDir) throws IOException {
	       File file = new File(histoDir);

	        // Reading directory contents
	        File[] files = file.listFiles();

	        for (int i = 0; i < files.length; i++) {
	        	readHisto(files[i]);
	        }
	}
	
	private void readHisto(File histoFile) throws IOException {
		final String encoding = "US-ASCII";
		
		BufferedReader buffered = null;
		InputStream gzipStream  = null;
		InputStream fileStream = new FileInputStream(histoFile);
		
		if ( histoFile.getName().endsWith(".gz") ) {
			gzipStream = new GZIPInputStream(fileStream);
			Reader decoder = new InputStreamReader(gzipStream, encoding);
			buffered = new BufferedReader(decoder);
		} else {
			buffered = new BufferedReader(new InputStreamReader(fileStream));
		}

		String curLine = null;
		String lastLine = null;
		
		while((curLine = buffered.readLine()) != null){
			lastLine = curLine;
	    }
		
		if ((lastLine != null) && !lastLine.isEmpty()) {
			String fields[] = lastLine.split("\\s+");
			
			if (fields.length == 3) {
				
				String totalObjects = fields[1];
				String totalSize = fields[2];
				
				// get the timestamp from the filename
				String histoFileName = histoFile.getName();
				int firstDash = histoFileName.indexOf('-');
				String timeString = histoFileName.substring(0, firstDash);
				Date processTime = new Date(new Long(timeString));
				
				// format the date
				 SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss.SSS");
				 String formattedTime = simpleDateFormat.format(processTime);
				
				bw.write(histoFileName + "," + formattedTime + "," + totalObjects + "," + totalSize + "\n");
			}
		}
		
		if (gzipStream != null)
			gzipStream.close();
		
		buffered.close();
	}
}
