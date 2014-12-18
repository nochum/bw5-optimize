package nochum.bw5.utilities;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * This class intruments a BACKUP COPY of an existing BW application with
 * tooling that generates a heap dump after the Start activity and before the
 * End activity of each process in the project.
 * <p>
 * The resulting heap dumps can be subsequently analyzed with the Eclipse
 * Memory Analyzer Tool (MAT) from the command line, to produce reports
 * on memory utilization and class histograms.
 * <p>
 * This allows memory growth to be analyzed within the context of BW processes
 * such that it becomes easier to see which processes cause more memory to be
 * consumed, and which classes are generated within the BW process that consume
 * the memory.
 * 
 * @author Nochum Klein
 * @version %I%, %G%
 */ 
class BWInstrument {
	
	/**
	 * 
	 */
	private Document   doc           = null;  
	/**
	 * The properties required to invoke Hawk and traverse the project filesystem
	 */
	private Properties _props        = null;
	
	/**
	 * directory where hprof files will be placed
	 */
	private String outputLocation    = null;
	
	/**
	 * directory where hprof files will be placed
	 */
	private String jmapLoc    = null;
	
	/**
	 * directory where hprof files will be placed
	 */
	private String slashStyle         = null;
	
	/**
	 * full path to jar file containing heap dump logic
	 */
	private String jarLocation       = null;
	
	/**
	 * Class constructor triggers all processing and signals completion.
	 * @param props                           the properties collected by the Optimize facade class.
	 * @throws UsageException                 if required options are missing or invalid
	 * @throws ParserConfigurationException   If errors are incurred parsing the BW process.
	 * @throws IOException                    If errors are incurred parsing the BW process.
	 * @throws SAXException                   If errors are incurred parsing the BW process.
	 */
	public BWInstrument(Properties props) throws TransformerException, SAXException, IOException, ParserConfigurationException, UsageException {
		final String usageString = "\nUsage: Optimize Instrument [options]\n\n" +
				"   where options are:\n\n" +
				"-project   <project location>   - Full path to project on the filesystem.\n" +
				"-output    <output location>    - Directory where heap dump files will be placed.\n" +
				"-jar       <jar location>       - Full path to jar file containing heap dump logic.\n" +
				"-jmap      <jmap location>      - Full path to the jmap executable on the host where the code will be deployed.\n" +
				"-slash     <file Separator>     - Either a backslash or a forward slash depending on the DEPLOYMENT platform.\n"
				;

		// set the props class variable
		_props = props;

		if (!_props.containsKey("outLoc")) {
			throw(new UsageException("Required input parameter '-output' has not been specified.", usageString));
		} else {
			outputLocation = _props.getProperty("outLoc");
		}

		if (!_props.containsKey("jarFile")) {
			throw(new UsageException("Required input parameter '-jar' has not been specified.", usageString));
		} else {
			jarLocation = _props.getProperty("jarFile");
		}

		if (!_props.containsKey("jmapLoc")) {
			throw(new UsageException("Required input parameter '-jmap' has not been specified.", usageString));
		} else {
			jmapLoc = _props.getProperty("jmapLoc");
		}

		if (!_props.containsKey("slashStyle")) {
			throw(new UsageException("Required input parameter '-slash' has not been specified.", usageString));
		} else {
			slashStyle = _props.getProperty("slashStyle");
		}
		
		if (!_props.containsKey("projectRoot")) {
			throw(new UsageException("Required input parameter '-project' has not been specified.", usageString));
		} else {
			traverse(new File(_props.getProperty("projectRoot")));
		}
	}

    /**
     * Works on a single file system entry and
     * calls itself recursively if it turns out
     * to be a directory.
     * @param file A file or a directory to process
	 * @throws IOException 
	 * @throws SAXException 
	 * @throws ParserConfigurationException 
	 */
	private void traverse( File file ) throws TransformerException, SAXException, IOException, ParserConfigurationException {
       // Print the name of the entry
//       System.out.println( file.getName() ) ;

       // Check if it is a directory
       if( file.isDirectory() ) {
          // Get a list of all the entries in the directory
          String entries[] = file.list() ;

          // Ensure that the list is not null
          if( entries != null ) {
             // Loop over all the entries
             for( String entry : entries ) {
                // Recursive call to traverse
                traverse( new File(file,entry) ) ;
             }
          }
       } else {
    	   if (file.getName().endsWith(".process")) {
    		   processBWProcess(file);
    	   }
       }
	}
	
	/**
	 * recursively process each activity input
	 * 
	 * @param bwProcess
	 * @throws TransformerException
	 * @throws IOException
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 */
	private void processBWProcess(File bwProcess) throws TransformerException,
			SAXException, IOException, ParserConfigurationException {
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		doc = dBuilder.parse(bwProcess);
		
		Node procdefNode = doc.getDocumentElement();
		procdefNode.normalize();
		
		// determine the insertion point for new nodes
		Node insertionPoint = findInsertionPoint(procdefNode);
		
		// do not attempt to instrument dummy processes that do not contain BW activities
		if (doc.getElementsByTagName("pd:activity").getLength() > 0 && null != insertionPoint) {
		
			// get the BW process name and drop the path
			String fullName = bwProcess.getPath();
			int lastSlash = fullName.lastIndexOf(File.separator);
			String processName = fullName.substring(lastSlash + 1);
	
			// create the ProfileStart node
			Node startNode = createNode(processName, "ProfileStart", getStartXY());
			
			// create the ProfileEnd node
			Node endNode = createNode(processName, "ProfileEnd", getEndXY());
	
			// get the name of the Start activity
			String startName = getStartName();
	
			// create a transition from the Start to the "ProfileStart" activity
			Node startTransition = createTransition(startName, "ProfileStart");
			
			// get the name of the End activity
			String endName = doc.getElementsByTagName("pd:endName").item(0).getFirstChild().getNodeValue();
			
			// create a transition from the "ProfileEnd" to the End activity
			Node endTransition = createTransition("ProfileEnd", endName);
			
			if (null != startNode &&
					null != endNode && 
					null != startTransition &&
					null != endTransition) {
				// rewire existing transition references to the Start and End activities
				rewireTransitions(startName, endName);
				
				// insert new nodes at the insertion point
				procdefNode.insertBefore(startNode, insertionPoint);
				procdefNode.insertBefore(endNode, insertionPoint);
				procdefNode.insertBefore(startTransition, insertionPoint);
				procdefNode.insertBefore(endTransition, insertionPoint);
				
				// write the DOM object back to the BW process file
				TransformerFactory transformerFactory = TransformerFactory
						.newInstance();
				Transformer transformer = transformerFactory.newTransformer();
				DOMSource domSource = new DOMSource(doc);
		
				StreamResult streamResult = new StreamResult(bwProcess);
				transformer.transform(domSource, streamResult);
			}
		}
	}

	private Node findInsertionPoint(Node procdefNode) {
		NodeList childNodes = procdefNode.getChildNodes();
		
		Node lastActivity = null;
		for (int i = 0; i < childNodes.getLength(); i++) {
			Node curNode = childNodes.item(i);
			if (curNode.getNodeType() == Element.ELEMENT_NODE) {
				String nodeName = curNode.getNodeName();
				if (nodeName != null && nodeName.equals("pd:activity"))
					lastActivity = curNode;
			}
		}
		
		if (null != lastActivity)
			return lastActivity.getNextSibling();
		else
			return null;
	}

	private void rewireTransitions(String startName, String endName) {
		String transitionFrom = null;
		String transitionTo = null;
		
		// get all of the transitions
		NodeList transitionList = doc.getElementsByTagName("pd:transition");

		for (int tsnIdx = 0; tsnIdx < transitionList.getLength(); tsnIdx++) {
			Element curNode = (Element) transitionList.item(tsnIdx);

			if (curNode.getNodeType() == Element.ELEMENT_NODE) {
				NodeList fromList = curNode.getElementsByTagName("pd:from");
				
				for (int fromIdx = 0; fromIdx < fromList.getLength(); fromIdx++) {
					Element fromNode = (Element) fromList.item(fromIdx);
					
					if (fromNode.getNodeType() == Element.ELEMENT_NODE) {
						Node fromChild = fromNode.getFirstChild();
						transitionFrom = fromChild.getNodeValue();

						if (transitionFrom.equals(startName))
							fromChild.setNodeValue("ProfileStart");
					}
				}
				
				NodeList toList = curNode.getElementsByTagName("pd:to");
				for (int toIdx = 0; toIdx < toList.getLength(); toIdx++) {
					Element toNode = (Element) toList.item(toIdx);
					
					if (toNode.getNodeType() == Element.ELEMENT_NODE) {
						Node toChild = toNode.getFirstChild();
						transitionTo = toChild.getNodeValue();

						if (transitionTo.equals(endName))
							toChild.setNodeValue("ProfileEnd");
					}
				}
			}
		}
	}

	private String getStartName() {
		String startName = null;
		
		NodeList startList = doc.getElementsByTagName("pd:startName");
		for (int startIdx = 0; startIdx < startList.getLength(); startIdx++) {
			Node curNode = startList.item(startIdx);
			if (curNode.getNodeType() == Element.ELEMENT_NODE) {
				startName = curNode.getFirstChild().getNodeValue();
			}
		}

		return startName;
	}

	private XYCoord getEndXY() {
		String endX = doc.getElementsByTagName("pd:endX").item(0).getFirstChild().getNodeValue();
		String endY = doc.getElementsByTagName("pd:endY").item(0).getFirstChild().getNodeValue();

		return new XYCoord(endX, endY);
	}

	private XYCoord getStartXY() {
		String startX = null;
		String startY = null;

		NodeList starterList = doc.getElementsByTagName("pd:starter");
		if (starterList.getLength() > 0) {
			startX = ( (Element) starterList.item(0) ).getElementsByTagName("pd:x").item(0).getFirstChild().getNodeValue();
			startY = ( (Element) starterList.item(0) ).getElementsByTagName("pd:y").item(0).getFirstChild().getNodeValue();
		} else {
			startX = doc.getElementsByTagName("pd:startX").item(0).getFirstChild().getNodeValue();
			startY = doc.getElementsByTagName("pd:startY").item(0).getFirstChild().getNodeValue();
		}

		return new XYCoord(startX, startY);
	}

	private Node createNode(String processName, String activityName, XYCoord xyCoord) 
			throws SAXException, IOException, ParserConfigurationException {
        
		String insertActivity = 
				"    <pd:activity name=\"" + activityName + "\">" +
				"        <pd:type>com.tibco.plugin.java.JavaMethodActivity</pd:type>" +
				"        <pd:resourceType>ae.activities.JavaMethodActivity</pd:resourceType>" +
				"        <pd:x>" + (xyCoord.getXCoord() + 75) + "</pd:x>" +
				"        <pd:y>" + (xyCoord.getYCoord() + 75) + "</pd:y>" +
				"        <config>" +
				"            <ConstructDeclaredClass>false</ConstructDeclaredClass>" +
				"            <CacheConstructedClass>false</CacheConstructedClass>" +
				"            <InvokeCleanupMethod>false</InvokeCleanupMethod>" +
				"            <MethodInfo>" +
				"                <classLocation>" + jarLocation + "</classLocation>" +
				"                <className>nochum.bw5.utilities.BWProfile</className>" +
				"                <methodName>heapHisto</methodName>" +
				"                <methodReturn>void</methodReturn>" +
				"                <methodParameter>java.lang.String</methodParameter>" +
				"                <methodParameter>java.lang.String</methodParameter>" +
				"            </MethodInfo>" +
				"        </config>" +
				"        <pd:inputBindings>" +
				"            <jmai:JavaMethodActivityInput xmlns:jmai=\"www.tibco.com/plugin/java/JavaMethodActivityInput\">" +
				"                <MethodParameters>" +
				"                    <Parameter1 xmlns:tib=\"http://www.tibco.com/bw/xslt/custom-functions\">" +
				"                        <xsl:value-of select=\"concat(\'" + outputLocation + slashStyle + "\'" +
				", tib:timestamp(), " + "\'-" + processName + "-" + activityName + "\')\"/>" +
				"                    </Parameter1>" +
				"                    <Parameter2>" +
				"                        <xsl:value-of select=\"\'" + jmapLoc + "\'\"/>" +
				"                    </Parameter2>" +
				"                </MethodParameters>" +
				"            </jmai:JavaMethodActivityInput>" +
				"        </pd:inputBindings>" +
				"    </pd:activity>";
		
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

	    Node fragmentNode = dBuilder.parse(
	            new InputSource(new StringReader(insertActivity)))
	            .getDocumentElement();
		
		return doc.importNode(fragmentNode, true);
	}

	private Node createTransition(String fromActivity, String toActivity) throws SAXException, IOException, ParserConfigurationException {
	    
		String insertTranstion = "    <pd:transition>"
				+ "        <pd:from>"+ fromActivity + "</pd:from>"
				+ "        <pd:to>"+ toActivity + "</pd:to>"
				+ "        <pd:lineType>Default</pd:lineType>"
				+ "        <pd:lineColor>-16777216</pd:lineColor>"
				+ "        <pd:conditionType>always</pd:conditionType>"
				+ "    </pd:transition>";
		
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
	
	    Node fragmentNode = dBuilder.parse(
	            new InputSource(new StringReader(insertTranstion)))
	            .getDocumentElement();
		
		return doc.importNode(fragmentNode, true);
	}
	
	class XYCoord {
		private Integer xCoord = null;
		private Integer yCoord = null;
		
		public XYCoord(Integer intX, Integer intY) {
			setXCoord(intX);
			setYCoord(intY);
		}
		
		public XYCoord(String startX, String startY) {
			setXCoord(new Integer(startX));
			setYCoord(new Integer(startY));
		}

		public Integer getXCoord() {
			return xCoord;
		}
		
		public void setXCoord(Integer xCoord) {
			this.xCoord = xCoord;
		}
		
		public Integer getYCoord() {
			return yCoord;
		}
		
		public void setYCoord(Integer yCoord) {
			this.yCoord = yCoord;
		}
	}
}