package nochum.bw5.utilities;
import COM.TIBCO.hawk.console.hawkeye.*;
import COM.TIBCO.hawk.talon.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * This class discovers BusinessWorks activities that have not been executed
 * within BusinessWorks processes that have executed. It outputs a file of 
 * comma-separated values with two values on each line: The process name, and
 * the activity name.  Thus if a BusinessWorks process has five activities 
 * that have never been executed, there will be five lines in the result file
 * witn the first value (process name) the same, and the second value (activity
 * name) different.
 * <p>
 * It should be noted that not all dead code is bad.  Exception-handling logic
 * for example is absolutely necessary, however in many cases will never have 
 * been executed.  Care and understanding should therefore be taken when
 * reviewing and evaluating the results.
 * <p> 
 * this class works by creating two lists for each BusinessWorks process.  One
 * list contains all activities within the process, and the other contains only
 * those that have executed.  The difference between the two sets are the
 * un-executed processes or dead code.
 * 
 * @author Nochum Klein
 * @version %I%, %G%
 */
class BWDeadCode {
	 // Class Parameters
	 /**
	  * This contains a list of all activities for each process in the project
	  */
	 private HashMap<String, ArrayList<String>> allActivities = new HashMap<String, ArrayList<String>>();

	// Class Variables
	/**
	 * The properties required to invoke Hawk and traverse the project filesystem
	 */
	private Properties _props = null;

	/**
	 * Class constructor triggers all processing and signals completion.
	 * @param args  An array of Strings containing all arguments for the class.  
	 * @throws ParserConfigurationException 
	 * @throws SAXException 
	 * @throws IOException 
	 * @throws UsageException 
	 * @throws UsageException 
	 * @throws MicroAgentException 
	 * @throws ConsoleInitializationException 
	 * @throws Exception
	 */
	BWDeadCode(Properties props) throws ParserConfigurationException, SAXException, IOException, UsageException, 
		ConsoleInitializationException, MicroAgentException  {
		// set the props class variable
		_props = props;
		
		// ensure that we have all required properties
		validateProperties();
		
		// Retrieve all activities in the project
		processAllActivities();

		// Retrieve the *used* activities
		HashMap<String, ArrayList<String>> usedActivities = processUsedActivities();

		writeResults(usedActivities);
	}

	private void validateProperties() throws UsageException {
		final String usageString = "\nUsage: Optimize deadcode [options]\n\n" +
				"   where options are:\n\n" +
				"-domain    <hawk domain>        - Hawk domain name.\n" +
				"-engine    <engine name>        - BW engine name.\n" +
				"-service   <hawk service>       - Hawk RV service parameter.\n" +
				"-network   <hawk network>       - Hawk RV network parameter.\n" +
				"-daemon    <hawk daemon>        - Hawk RV daemon parameter.\n" +
				"-output    <output location>    - Full path and file name for output report.\n" +
				"-project   <project location>   - Full path to project on the filesystem.\n"
				;
		
		if (!_props.containsKey("projectRoot")) {
			throw(new UsageException("Required input parameter '-project' has not been specified.", usageString));
		}

		if (!_props.containsKey("engineName")) {
			throw(new UsageException("Required input parameter '-engine' has not been specified.", usageString));
		}

		if (!_props.containsKey("outLoc")) {
			throw(new UsageException("Required input parameter '-output' has not been specified.", usageString));
		}

		if (!_props.containsKey("hawkDomain")) {
			throw(new UsageException("Required input parameter '-domain' has not been specified.", usageString));
		}
		
	}

	/**
	 * @throws Exception
	 */
	private void processAllActivities() throws ParserConfigurationException, SAXException, IOException {

		traverse(new File(_props.getProperty("projectRoot")));

		return;
	}

	/**
	 * @return
	 * @throws MicroAgentException 
	 * @throws ConsoleInitializationException 
	 * @throws Exception
	 */
	private HashMap<String, ArrayList<String>> processUsedActivities() throws MicroAgentException, ConsoleInitializationException {
		HashMap<String, ArrayList<String>> usedActivities = new HashMap<String, ArrayList<String>>();
		
		String hawkDomain = _props.getProperty("hawkDomain");

		TIBHawkConsole console = new TIBHawkConsole(hawkDomain, 
				_props.getProperty("rvService"), _props.getProperty("rvNetwork"), _props.getProperty("rvDaemon"));

		// retrieve and initialize the AgentManager
		AgentManager manager = console.getAgentManager();
		manager.initialize();

		// Find the microagent for the BW engine
		final String microAgentBase = "COM.TIBCO.ADAPTER.bwengine";
		String microagentName = microAgentBase + "." + hawkDomain + "." + _props.getProperty("engineName");
		MicroAgentID microAgentIds[] = manager.getMicroAgentIDs(microagentName);

		if (microAgentIds.length > 0) {
			// Get processes that have executed more than once
			ArrayList<String> processList = getExecutedProcesses(manager, microAgentIds[0]);

			// Get the list of executed activities for each process
			if (null != processList) {
				for (String bwProcess : processList) {
					ArrayList<String> activityList = getExecutedActivities(
							manager, microAgentIds[0], bwProcess);
					if (null != activityList) {
						usedActivities.put(bwProcess, activityList);
					}
				}
			}
		} else {
			System.err.println("No microagents found to match the input specifications.");
		}
		
		// Close Hawk down
		manager.shutdown();

		return usedActivities;
	}

	/**
	 * Works on a single file system entry and calls itself recursively if it
	 * turns out to be a directory.
	 * 
	 * @param file
	 *            A file or a directory to process
	 */
	/**
	 * @param file
	 * @throws Exception
	 */
	private void traverse(File file) throws ParserConfigurationException, SAXException, IOException {
		// Print the name of the entry
		String fileName = file.getName();
		// System.out.println( fileName ) ;

		// Check if it is a directory
		if (file.isDirectory()) {
			// Get a list of all the entries in the directory
			String entries[] = file.list();

			// Ensure that the list is not null
			if (entries != null) {
				// Loop over all the entries
				for (String entry : entries) {
					// Recursive call to traverse
					traverse(new File(file, entry));
				}
			}
		} else {
			if (fileName.endsWith(".process")) {
				ArrayList<String> activityList = processBWProcess(file);
				String relativeFile = file.getPath().replaceFirst(
						_props.getProperty("projectRoot") + "/", "");
				// System.err.println("All Adding " + relativeFile + ": " +
				// activityList);
				allActivities.put(relativeFile, activityList);
			}
		}
	}

	/**
	 * @param bwProcess
	 * @return
	 * @throws ParserConfigurationException 
	 * @throws IOException 
	 * @throws SAXException 
	 * @throws Exception
	 */
	private ArrayList<String> processBWProcess(File bwProcess) throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(bwProcess);
		ArrayList<String> activityList = new ArrayList<String>();

		doc.getDocumentElement().normalize();

		// Retrieve the set of activities within the BW process
		NodeList activityNodes = doc.getElementsByTagName("pd:activity");

		for (int i = 0; i < activityNodes.getLength(); i++) {
			activityList.add(activityNodes.item(i).getAttributes()
					.getNamedItem("name").getNodeValue());
		}

		return activityList;
	}

	/**
	 * @param usedActivities
	 * @throws IOException 
	 * @throws Exception
	 */
	private void writeResults(HashMap<String, ArrayList<String>> usedActivities) throws IOException {
		PrintWriter out = null;

		out = new PrintWriter(_props.getProperty("outLoc"));

		Iterator<Entry<String, ArrayList<String>>> it = allActivities
				.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, ArrayList<String>> pairs = (Map.Entry<String, ArrayList<String>>) it
					.next();
			String bwProcess = (String) pairs.getKey();

			// Check whether the process itself has been executed
			if (usedActivities.containsKey(bwProcess)) {
				ArrayList<String> allActivityList = (ArrayList<String>) pairs
						.getValue();
				ArrayList<String> usedActivityList = usedActivities
						.get(bwProcess);

				// Now remove the common activities, leaving only those that are
				// not common
				allActivityList.removeAll(usedActivityList);

				// And write the results to the file
				for (String bwActivity : allActivityList) {
					out.println(bwProcess + "," + bwActivity);
				}
			}
		}

		out.flush();
		out.close();
	}

	/**
	 * @param manager
	 * @param bwMicroAgent
	 * @param bwProcess
	 * @return
	 * @throws MicroAgentException 
	 * @throws Exception
	 */
	private ArrayList<String> getExecutedActivities(AgentManager manager,
			MicroAgentID bwMicroAgent, String bwProcess) throws MicroAgentException {
		ArrayList<String> activityList = new ArrayList<String>();

		// Construct the argument to GetActivities
		DataElement[] processNames = { new DataElement("ProcessDefinition",
				bwProcess) };

		MethodInvocation mactivities = new MethodInvocation("GetActivities",
				processNames);
		MicroAgentData activitiesInfo = manager.invoke(bwMicroAgent, mactivities);
		TabularData tabData = (TabularData) activitiesInfo.getData();
		Object[][] table = tabData.getAllData();

		if (table != null) {
			for (int row = 0; row < table.length; row++) {
				// Check whether the execution count is greater than 0
				// ProcDefName [0], ActivityName [1], ActivityClass [2],
				// ExecutionCount [3]
				Long executionCount = (Long) table[row][3];
				if (null != executionCount && executionCount > 0) {
					// Where an activity exists within one or more groups,
					// Hawk gloms the group names separated by a slash ('/')
					// in front of the actual activity name. We just want the
					// actual activity name.
					String activityName = (String) table[row][1];
					activityList.add(activityName.contains("/") ? activityName
							.substring(activityName.lastIndexOf("/") + 1)
							: activityName);
				}
			}
		}

		return activityList;
	}

	/**
	 * @param manager
	 * @param bwMicroAgent
	 * @return
	 * @throws MicroAgentException 
	 * @throws Exception
	 */
	private ArrayList<String> getExecutedProcesses(AgentManager manager,
			MicroAgentID bwMicroAgent) throws MicroAgentException {
		ArrayList<String> processList = new ArrayList<String>();

		MethodInvocation mprocessDefinitions = new MethodInvocation(
				"GetProcessDefinitions", null);
		MicroAgentData processDefinitionInfo = manager.invoke(bwMicroAgent, mprocessDefinitions);
		TabularData tabData = (TabularData) processDefinitionInfo.getData();
		Object[][] table = tabData.getAllData();

		if (table != null) {
			for (int row = 0; row < table.length; row++) {
				// Check whether the execution count is greater than 0
				// Name [0], Starter [1], Created [2]
				Long executionCount = (Long) table[row][2];
				if (null != executionCount && executionCount > 0) {
					processList.add((String) table[row][0]);
				}
			}
		}

		return processList;
	}
}