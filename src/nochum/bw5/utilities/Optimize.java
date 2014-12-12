package nochum.bw5.utilities;

import java.util.Properties;

/**
 * The main facade class that invokes specific optimization processing based
 * on the user's command-line arguments. Required arguments will depend on
 * the specific desired optimization.
 * <p>
 * Current supported optimization choices are:
 * <ul>
 * <li>-deadcode   - Discovers and reports dead code within a BW project.
 * <li>-ifToCopyOf - Modifies generated value-of within if constructs for
 * optional-to-optional mappings to straight copy-of.
 * <li>-namespaces - Removes unused namespace declarations from processes. 
 * <li>-xpathRef   - Reports on the number and depth of XPath references
 * for each activity in each process.  Highlights optimization opportunities.
 * </ul>
 * 
 * @author Nochum Klein
 * @version %I%, %G%
 */
public class Optimize {

	public Optimize(String[] args) {
		// The desired operation is the first argument
		String opType = null;
		if (args.length > 0)
			opType = args[0];
		else
			usage();
		
		// parse the arguments passed in
		Properties props = parseArgs(args);
		
		// perform the desired processing
		try {
			if (opType.equals("deadcode"))
				new BWDeadCode(props);
			else if (opType.equals("ifToCopyOf"))
				new BWIfToCopyOf(props);
			else if (opType.equals("namespaces"))
				new BWNamespace(props);
			else if (opType.equals("xpathRef"))
				new BWXPathRef(props);
			else if (opType.equals("instrument"))
				new BWInstrument(props);
			else
				usage();
		} catch (UsageException ue) {
			System.err.println(ue.getMessage());
			System.err.print(ue.getUsage());
			ue.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		System.out.println("Done!");
	}

	/**
	 * prints general usage information for the Optimize class. Specific usage
	 * info for the called classes will bubble up via the UsageException.
	 */
	private void usage() {
		final String usageString = "Usage: Optimize COMMAND\n" +
				"       where COMMAND is one of:\n" +
				"  deadcode   - Discover and report dead code within a BW project.\n" +
				"  ifToCopyOf - Modify generated value-of within if constructs for optional-to-optional mappings to straight copy-of.\n" +
				"  namespaces - Remove unused namespace declarations from processes.\n" +
				"  xpathRef   - Report on the number and depth of XPath references for each activity in each process.\n" +
				"  instrument - Instrument a BACKUP COPY of a BW project for memory profiling.\n\n" +
				"Most commands print help when invoked w/o parameters.\n";
				
        System.err.print(usageString);
        System.exit(0);		
	}

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		Optimize app = new Optimize(args);

		return;
	}

	/**
	 * Parses the array of arguments passed in to the class and returns them as named properties.
	 * @param args  The array of arguments passed in.
	 */
	private Properties parseArgs(String[] args) {
		Properties props = new Properties();
		int i = 1;
	
		while (i < args.length) {
			if (args[i].equals("-domain")) {
				if ((i + 1) >= args.length)
					usage();
				props.put("hawkDomain", args[i + 1]);
				i += 2;
			} else if (args[i].equals("-service")) {
				if ((i + 1) >= args.length)
					usage();
				props.put("rvService", args[i + 1]);
				i += 2;
			} else if (args[i].equals("-network")) {
				if ((i + 1) >= args.length)
					usage();
				props.put("rvNetwork", args[i + 1]);
				i += 2;
			} else if (args[i].equals("-daemon")) {
				if ((i + 1) >= args.length)
					usage();
				props.put("rvDaemon", args[i + 1]);
				i += 2;
			} else if (args[i].equals("-engine")) {
				if ((i + 1) >= args.length)
					usage();
				props.put("engineName", args[i + 1]);
				i += 2;
			} else if (args[i].equals("-output")) {
				if ((i + 1) >= args.length)
					usage();
				props.put("outLoc", args[i + 1]);
				i += 2;
			} else if (args[i].equals("-project")) {
				if ((i + 1) >= args.length)
					usage();
				props.put("projectRoot", args[i + 1]);
				i += 2;
			} else if (args[i].equals("-jar")) {
				if ((i + 1) >= args.length)
					usage();
				props.put("jarFile", args[i + 1]);
				i += 2;
			} else if (args[i].equals("-memory")) {
				if ((i + 1) >= args.length)
					usage();
				props.put("memoryThreshold", args[i + 1]);
				i += 2;
			} else if (args[i].equals("-slash")) {
				if ((i + 1) >= args.length)
					usage();
				props.put("slashStyle", args[i + 1]);
				i += 2;
			} else {
				System.err.println("Unrecognized parameter: " + args[i]);
				usage();
			}
		}
		
		return(props);
	}
}
