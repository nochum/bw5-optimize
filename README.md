bw5-optimize
============

This project contains a set of tools designed to help optimize BusinessWorks 5 projects.  The current set of functionality includes:

deadcode
--------
This utility discovers BusinessWorks activities that have not been executed
within BusinessWorks processes that have executed. It outputs a file of 
comma-separated values with two values on each line: The process name, and
the activity name.  Thus if a BusinessWorks process has five activities 
that have never been executed, there will be five lines in the result file
witn the first value (process name) the same, and the second value (activity
name) different.

It should be noted that not all dead code is bad.  Exception-handling logic
for example is absolutely necessary, however in many cases will never have 
been executed.  Care and understanding should therefore be taken when
reviewing and evaluating the results.

this class works by creating two lists for each BusinessWorks process.  One
list contains all activities within the process, and the other contains only
those that have executed.  The difference between the two sets are the
un-executed processes or dead code.

ifToCopyOf
----------
This utility modifies generated value-of within if constructs for optional-to-optional mappings to straight copy-of.

We often see a construct similar to the following if we examine BW process
files in a test editor:

    `<xsl:if test="pfx10:ContactFrequency">
        <pfx10:ContactFrequency>
             <xsl:value-of select="pfx10:ContactFrequency"/>
        </pfx10:ContactFrequency>
    </xsl:if>`

The 'if' statement surrounding the actual mapping is often generated by
Designer as the result of an optional-to-optional mapping.  Where the 
value is being passed unchanged, the same result can be delivered much
more succinctly with the following construct instead:

    `<xsl:copy-of select="pfx10:ContactFrequency"/>`

This will copy the value if it exists in the source data.  Otherwise it
should not produce an output element.  While this may seem like a "trivial"
savings, where large XML documents are being mapped the actual savings can
be significant.

It is important to note that the space savings that we see on disk does not
necessarily translate to in-memory savings.  When processing large XML
documents most of the memory consumption may end up being the actual 
transient data being passed and manipulated rather than the mappings.  So
if the goal is to reduce memory utilization it may be best to consider ways
that a large XML document can be broken down into smaller chunks so that
the entire document does not need to be in memory at once.


namespaces
----------
This utility traverse a project directory and locate all BW process files.
For each process file introspect and evaluate the imported namespaces in 
order to determine whether the namespaces are actually referenced.

Over the course of time as a result over normal development and maintenance
activities within BW processes are changed and or removed.  This often
results in scenarios where schemas that were once referenced are no longer
referenced.

While the additional unnecessary schema references will not cause any harm
functionally, it adds additional processing and memory overhead since the
schemas must be loaded even if they are not used.  It is therefore a good 
practice to clean up unused schemas on a periodic basis.  This class helps
with that task. 

xpathRef
--------
Introspects BusinessWorks processes, and evaluates the input mappings to
each activity.  It is known that the cost of XML parsing increases significantly
in relation to the depth of nested elements within XML instances.  This
problem is multiplied many-fold if there are multiple references to deeply
nested XML elements within the same activity input.  Each time the parser
encounters another deeply nested element the cost is incurred yet again.

It is possible to significantly reduce this cost by creating shortcuts to
deeply nested levels within the XML.  For example, let's examine the
following hypothetical example:

  $ActivityOutput/ns:Accounts/ns:AccountArray/ns:BankAccounts/ns:CheckingAccounts/ns:CheckingAccount
  
  The last element above (ns:CheckingAccount) has sub-elements called:
  - ns:Name
  - ns:Type
  - ns:Number

By creating a shortcut called $CheckingAccount that referes to the deeply 
nested element we can allow the deeply nested element to be evaluated
just once, rather than once for every sub-element.

This class produces a report that shows both the depth of elements that are
referred to within activity input mappings, and also the number of times
the deeply nested reference is itself referenced.  This allows users to
prioritize the effort of creating semantically meaningful shortcuts based
on both depth and number of references.

Usage
=====
Usage: Optimize **COMMAND**
       where **COMMAND** is one of:
  **deadcode**   - Discover and report dead code within a BW project.
  **ifToCopyOf** - Modify generated value-of within if constructs for optional-to-optional mappings to straight copy-of.
  **namespaces** - Remove unused namespace declarations from processes.
  **xpathRef**   - Report on the number and depth of XPath references for each activity in each process.\n

Most commands print help when invoked w/o parameters

Planned Enhancements
====================
Current planned enhancements include:
- checking for HTTP, JDBC, and SOAP activities that do not have timeouts configured
- analysis of engine TRA files for sanity checks