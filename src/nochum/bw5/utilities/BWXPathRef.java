package nochum.bw5.utilities;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Introspects BusinessWorks processes, and evaluates the input mappings to
 * each activity.  It is known that the cost of XML parsing increases significantly
 * in relation to the depth of nested elements within XML instances.  This
 * problem is multiplied many-fold if there are multiple references to deeply
 * nested XML elements within the same activity input.  Each time the parser
 * encounters another deeply nested element the cost is incurred yet again.
 * <p>
 * It is possible to significantly reduce this cost by creating shortcuts to
 * deeply nested levels within the XML.  For example, let's examine the
 * following hypothetical example:
 * <p>
 *   $ActivityOutput/ns:Accounts/ns:AccountArray/ns:BankAccounts/ns:CheckingAccounts/ns:CheckingAccount
 *   
 *   The last element above (ns:CheckingAccount) has sub-elements called:
 *   - ns:Name
 *   - ns:Type
 *   - ns:Number
 * <p>
 * By creating a shortcut called $CheckingAccount that referes to the deeply 
 * nested element we can allow the deeply nested element to be evaluated
 * just once, rather than once for every sub-element.
 * <p>
 * This class produces a report that shows both the depth of elements that are
 * referred to within activity input mappings, and also the number of times
 * the deeply nested reference is itself referenced.  This allows users to
 * prioritize the effort of creating semantically meaningful shortcuts based
 * on both depth and number of references.
 * 
 * @author Nochum Klein
 * @version %I%, %G%
 */
 class BWXPathRef {

	/**
	 * 
	 */
	PrintWriter        out           = null;
	/**
	 * 
	 */
	HashMap <String, Integer> refMap = null;
	/**
	 * The properties required to invoke Hawk and traverse the project filesystem
	 */
	private Properties _props = null;
	
	/**
	 * @param props                           the properties collected by the Optimize facade class.
	 * @throws UsageException                 if required options are missing or invalid
	 * @throws ParserConfigurationException   If errors are incurred parsing the BW process.
	 * @throws IOException                    If errors are incurred parsing the BW process.
	 * @throws SAXException                   If errors are incurred parsing the BW process.
	 */
	public BWXPathRef(Properties props) throws ParserConfigurationException, SAXException, IOException, UsageException  {
		final String usageString = "\nUsage: Optimize xpathRef [options]\n\n" +
				"   where options are:\n\n" +
				"-output    <output location>    - Full path and file name for output report.\n" +
				"-project   <project location>   - Full path to project on the filesystem.\n"
				;

		if (!_props.containsKey("projectRoot")) {
			throw(new UsageException("Required input parameter '-project' has not been specified.", usageString));
		}

		if (!_props.containsKey("outLoc")) {
			throw(new UsageException("Required input parameter '-output' has not been specified.", usageString));
		}
		
		out = new PrintWriter(_props.getProperty("outLoc"));
		traverse(new File(_props.getProperty("projectRoot")));
		out.close();
	}

	/**
     * Works on a single file system entry and
     * calls itself recursively if it turns out
     * to be a directory.
     * @param  file                           A file or a directory to process
	 * @throws ParserConfigurationException   If errors are incurred parsing the BW process.
	 * @throws IOException                    If errors are incurred parsing the BW process.
	 * @throws SAXException                   If errors are incurred parsing the BW process.
	 */
	private void traverse( File file )throws ParserConfigurationException, SAXException, IOException  {
       // Print the name of the entry
       System.out.println( file.getName() ) ;

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
	 * @param bwProcess
	 * @throws ParserConfigurationException   If errors are incurred parsing the BW process.
	 * @throws IOException                    If errors are incurred parsing the BW process.
	 * @throws SAXException                   If errors are incurred parsing the BW process.
	 */
	private void processBWProcess(File bwProcess) throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(bwProcess);

		doc.getDocumentElement().normalize();

		// Retrieve the set of activities within the BW process
		NodeList activityList = doc.getElementsByTagName("pd:activity");

		for (int i = 0; i < activityList.getLength(); i++) {

			Node activity = activityList.item(i);
			String activityName = activity.getAttributes().getNamedItem("name").getNodeValue();
//			System.out.println("\nActivity name : " + activityName);

			if (activity.getNodeType() == Node.ELEMENT_NODE) {

				Element eActivity = (Element) activity;
				
				Node inputBindings = eActivity.getElementsByTagName("pd:inputBindings").item(0);
				
				// Here I want to get the first child node and recursively process it to completion.
				refMap = new HashMap <String, Integer>();
				processActivityInput(getChildElementNode(inputBindings));
			    Iterator<Entry<String, Integer>> it = refMap.entrySet().iterator();
			    while (it.hasNext()) {
			        Map.Entry<String, Integer> pairs = (Map.Entry<String, Integer>)it.next();
			        String key = (String) pairs.getKey();
			        int depth = key.length() - key.replace("/", "").length();
			        out.println(bwProcess+","+activityName+","+key+","+pairs.getValue()+","+depth);
			        it.remove(); // avoids a ConcurrentModificationException
			    }
			}
		}
	}
       
	/**
	 * processes and introspects a part of the activity input.  Call itself
	 * recursively in order to handle levels of nesting.
	 * @param node   an XML element.
	 */
	private void processActivityInput(Node node) {
        
        if ((node != null) && (node.getNodeType() == Node.ELEMENT_NODE)) {
        	if (node.hasAttributes()) {
        		// process copy-of, value-of, for-each, ...
        		Node contentNode = node.getAttributes().getNamedItem("select");
        		if (contentNode != null) {
                	String nodeContent = contentNode.getNodeValue();
                	if (nodeContent != null && nodeContent.contains("$")) {
                		processSelect(nodeContent);
                	}
        		}
        		// process if, when, ...
        		Node testNode = node.getAttributes().getNamedItem("test");
        		if (testNode != null) {
                	String nodeContent = testNode.getNodeValue();
                	if (nodeContent != null && nodeContent.contains("$")) {
                		processSelect(nodeContent);
                	}
        		}
        	}
        	
            // process children
            Node child = node.getFirstChild();
            while(child != null){
                processActivityInput(child);
                child = child.getNextSibling();
            }
        }
            
        return;
    }
	
	/**
	 * tokenizes element values since elements may contain XPath functions that
	 * include references to more than once element.
	 * @param elemValue
	 */
	private void processSelect(String elemValue) {
		String delims = "[ .,?!&#()\n]+";
		String[] tokens = elemValue.split(delims);
		
        // Find all "words" beginning with "$"
        for (String token : tokens) {
            if ( token.startsWith("$") && !token.startsWith("$_globalVariables") ) {
            	// add the current token to the map
            	addToMap(token);
            	
            	int pos = token.length();
            	while ((pos = token.lastIndexOf("/", pos)) != -1) {
            	    String curToken = token.substring(0, pos);
            	    addToMap(curToken);
            	    pos--;
            	}
            }
        }
	}
	
	/**
	 * @param key
	 */
	private void addToMap(String key) {
    	if (refMap.containsKey(key)) {
    		Integer curValue = refMap.get(key);
    		refMap.put(key, curValue + 1);
    	} else {
    		refMap.put(key, 1);
    	}		
	}
	
    /**
     * Returns a first child DOM Node of type ELEMENT_NODE
     * for the specified Node.
     */
    /**
     * @param xmlNode
     * @return
     */
    private static Node getChildElementNode(Node xmlNode) {
        if (xmlNode == null || !xmlNode.hasChildNodes()) {
            return null;
        }
        
        xmlNode = xmlNode.getFirstChild();
        while (xmlNode != null 
               && xmlNode.getNodeType() != Node.ELEMENT_NODE) {
            xmlNode = xmlNode.getNextSibling();
        }

        return xmlNode;
    }
}