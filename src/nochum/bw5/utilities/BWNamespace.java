package nochum.bw5.utilities;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
/**
 * Traverse a project directory and locate all BW process files.  For each
 * process file introspect and evaluate the imported namespaces in order to
 * determine whether the namespaces are actually referenced.
 * <p>
 * Over the course of time as a result over normal development and maintenance
 * activities within BW processes are changed and or removed.  This often
 * results in scenarios where schemas that were once referenced are no longer
 * referenced.
 * <p>
 * While the additional unnecessary schema references will not cause any harm
 * functionally, it adds additional processing and memory overhead since the
 * schemas must be loaded even if they are not used.  It is therefore a good 
 * practice to clean up unused schemas on a periodic basis.  This class helps
 * with that task. 
 * 
 * @author Nochum Klein
 * @version %I%, %G%
 */

class BWNamespace {
	/**
	 * The properties required to invoke Hawk and traverse the project filesystem
	 */
	private Properties _props = null;


	/**
	 * Class constructor triggers all processing and signals completion.
	 * 
	 * @param props
	 *            the properties collected by the Optimize facade class.
	 * @throws UsageException
	 *             if required options are missing or invalid
	 */
	public BWNamespace(Properties props) throws UsageException, ParserConfigurationException, SAXException, IOException, TransformerException {
	final String usageString = "\nUsage: Optimize namespaces [options]\n\n" +
			"   where options are:\n\n" +
			"-project   <project location>   - Full path to project on the filesystem.\n"
			;
	
	// set the props class variable
	_props = props;
	
	if (!_props.containsKey("projectRoot")) {
		throw(new UsageException("Required input parameter '-project' has not been specified.", usageString));
	}
	
	traverse(new File(_props.getProperty("projectRoot")));
  }

	/**
	 * Works on a single file system entry and
	 * calls itself recursively if it turns out
	 * to be a directory.
	 * @param file A file or a directory to process
	 * @throws Exception
	 */
	private void traverse( File file ) throws ParserConfigurationException, SAXException, IOException, TransformerException {
		// Print the name of the entry
		//   System.out.println( file.getName() ) ;
	
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
	
	private void processBWProcess(File bwProcess) throws ParserConfigurationException, SAXException, IOException, TransformerException {
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(bwProcess);
		
		Element documentElement = doc.getDocumentElement();
		documentElement.normalize();
		
		// retrieve the process into a string
	    String processString = FileUtils.readFileToString(bwProcess);
		
		// retrieve all xmlns attributes
		NamedNodeMap attrs = documentElement.getAttributes();
		for (int i = 0; i < attrs.getLength(); i++) {
			Node attr = attrs.item(i);
			String attrName = attr.getNodeName();
			if (attrName != null && attrName.startsWith("xmlns:")) {
				// take the value immediately after "xmlns:" and append a ":"
				String xmlnsPrefix = attrName.substring(6);
				String searchPrefix = xmlnsPrefix +":";
				
				// find the actual namespace that the attribute refers to
				String attrValue = attr.getNodeValue();
				NodeList imports = documentElement.getElementsByTagName("xsd:import");
				
				// count whether the prefix is used
				int useCount = StringUtils.countMatches(processString, searchPrefix);
				if (useCount == 0) {
					System.out.println(bwProcess + "\tDeleting namespace " + xmlnsPrefix);
					documentElement.removeAttributeNode((Attr) attr);
					
					// delete corresponding imports
					for (int j = 0; j < imports.getLength(); j++) {
						Node currentNode = imports.item(j);
						if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
							Element elemNS = (Element) currentNode;
							String importNS = elemNS.getAttribute("namespace");
							if (importNS.equals(attrValue)) {
								System.out.println(bwProcess + "\t\tDeleting associated import...");
								documentElement.removeChild(elemNS);
							}
						}
					}
				}
			}
		}
		
		// write the DOM object back to the BW process file
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		DOMSource domSource = new DOMSource(doc);

		StreamResult streamResult = new StreamResult(bwProcess);
		transformer.transform(domSource, streamResult);
	}
}
