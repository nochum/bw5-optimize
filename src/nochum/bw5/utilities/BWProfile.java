package nochum.bw5.utilities;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

import javax.management.MBeanServer;

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
     * Call this method from your application whenever you 
     * want to dump the heap snapshot into a file.
     *
     * @param fileName name of the heap dump file
     * @param live flag that tells whether to dump
     *             only the live objects
     * @throws IOException 
     * @throws AttachNotSupportedException 
     */
    public static void profileMemory(String fileName, Long memoryThreshold) throws AttachNotSupportedException, IOException {
        
        // take a full gc before profiling.  This will reduce
    	// the number of objects awaiting finalization
        System.gc();

        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
    	MemoryUsage mu = memBean.getHeapMemoryUsage();
    	long usedMemory = mu.getUsed();
		
//    	int objectCount = memBean.getObjectPendingFinalizationCount();
//    	boolean dumpHeap = ((usedMemory > memoryThreshold) || (objectCount > objectThreashold));
    	boolean dumpHeap = (usedMemory > memoryThreshold);
    	
    	logger.warn(fileName + " - Used memory: " + usedMemory + 
//    			".  Object count: " + objectCount +
    			( dumpHeap == true ? ".  Generating heap histogram of live objects..." : ""));
    	
    	if (dumpHeap == true) {
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