package nochum.bw5.utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

import javax.management.MBeanServer;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.log4j.Logger;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.tools.attach.AttachNotSupportedException;

public class BWProfile {
    // This is the name of the HotSpot Diagnostic MBean
    private static final String HOTSPOT_BEAN_NAME =
         "com.sun.management:type=HotSpotDiagnostic";

    // field to store the hotspot diagnostic MBean 
    private static volatile HotSpotDiagnosticMXBean hotspotMBean;
    
    private static final Logger logger = org.apache.log4j.Logger.getLogger("bw.logger");

    /**
     * This method executes the jmap executable in order to generate a heap
     *  histogram.  It requires a full path to a jmap executable as input.
     *  
     *  Due to the fact that System.gc() cannot be relied upon to cause an
     *  immediate gc, there is really no accurate way of determining the
     *  size of large objects on the heap until the histogram is generated (it
     *  is believed that jmap triggers a full gc when the live option is
     *  specified).  For this reason this method executes unconditionally.
     *  
     * @param fileName  - The full path to the location where the histogram 
     *                    should be written.
     * @param jmapLoc   - The full path to the jmap executable.
     * @param memoryThreshold
     * @throws IOException
     */
    public static void heapHisto(String fileName, String jmapLoc)  throws IOException {
		
    	if ( (null != jmapLoc) && (jmapLoc.isEmpty() == false) && (new File(jmapLoc).exists()) ) {
			// get the process pid
			String procName = ManagementFactory.getRuntimeMXBean().getName();
			int atLoc = procName.indexOf('@');
			String pid = procName.substring(0, atLoc);
			
			// create a new executor
			Executor exec = new DefaultExecutor();
			
			// set stdout and stderr
			FileOutputStream outErr=new FileOutputStream(fileName);
			PumpStreamHandler streamHandler = new PumpStreamHandler(outErr);
			exec.setStreamHandler(streamHandler);
			 
			CommandLine cl = new CommandLine(jmapLoc);
			cl.addArgument("-histo:live");
			cl.addArgument(pid);
	
			try {
				exec.execute(cl);
				
			} catch (ExecuteException ex) {
				System.err.println(outErr);
				ex.printStackTrace();
			}
			
			outErr.close();
			
            // attempt to compress the heap histogram
            Runtime.getRuntime().exec("gzip " + fileName);
    	}
    }
    
	/**
	 * It is worthwhile to note that there are a number of drawbacks with this
	 * approach: 
	 * 1. It is time consuming. 
	 * 2. Each hprof file takes up a large amount of disk space.
	 * 3. The gc() that precedes the heap dump is only advisory, so there is
	 * no guarantee that it will happen. The garbage collector is free to
	 * ignore the request.
	 * 
	 * @param fileName
	 *            name of the heap dump file
	 * @param live
	 *            flag that tells whether to dump only the live objects
	 * @throws IOException
	 * @throws AttachNotSupportedException
	 */
    public static void heapDump(String fileName, Long memoryThreshold) throws AttachNotSupportedException, IOException {
        
        // take a full gc before profiling.  This will reduce
    	// the number of objects awaiting finalization
        System.gc();

        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
    	MemoryUsage mu = memBean.getHeapMemoryUsage();
    	long usedMemory = mu.getUsed();
		
    	boolean thresholdExceeded = (usedMemory > memoryThreshold);
    	
    	logger.warn(fileName + " - Used memory: " + usedMemory + 
//    			".  Object count: " + objectCount +
    			( thresholdExceeded == true ? ".  Generating heap dump..." : ""));
    	
    	if (thresholdExceeded == true) {
            // initialize hotspot diagnostic MBean
            initHotspotMBean();
            
            try {
                hotspotMBean.dumpHeap(fileName, true);
                
                // heap dumps can get large -- compress them
                Runtime.getRuntime().exec("gzip " + fileName);
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception exp) {
            	// ignore
                // throw new RuntimeException(exp);
            }    	
    	}
    }
    
    // initialize the hotspot diagnostic MBean field
    private static void initHotspotMBean() {
        if (hotspotMBean == null) {
            synchronized (BWProfile.class) {
                if (hotspotMBean == null) {
                    hotspotMBean = getHotspotMBean();
                }
            }
        }
    }

    // get the hotspot diagnostic MBean from the
    // platform MBean server
    private static HotSpotDiagnosticMXBean getHotspotMBean() {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            HotSpotDiagnosticMXBean bean = 
                ManagementFactory.newPlatformMXBeanProxy(server,
                HOTSPOT_BEAN_NAME, HotSpotDiagnosticMXBean.class);
            return bean;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception exp) {
            throw new RuntimeException(exp);
        }
    }
}