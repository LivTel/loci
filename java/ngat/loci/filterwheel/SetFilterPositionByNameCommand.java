// SetFilterPositionByNameCommand.java
// $Id$
package ngat.loci.filterwheel;

import java.io.*;
import java.lang.*;
import java.net.*;

import org.json.*;

import ngat.flask.EndPoint;
import ngat.util.logging.*;
import ngat.loci.filterwheel.Command;

/**
 * Invoke the Loci filter wheel Flask end-point 'setFilterPositionByName'.
 * @author Chris Mottram
 * @version $Revision$
 */
public class SetFilterPositionByNameCommand extends Command implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Default constructor. Call's the Command super-class constructor.
	 * Then sets the Flask end-point name to "setFilterPositionByName" and the end-point to a "POST" end-point.
	 * @see #logger
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#setFlaskEndPointName
	 * @see ngat.flask.EndPoint#setDoPost
	 */
	public SetFilterPositionByNameCommand()
	{
		super();
		logger = LogManager.getLogger(this);
		endPoint.setFlaskEndPointName("setFilterPositionByName");
		endPoint.setDoPost();
	}

	/**
	 * Set the filter wheel to move to the specified filter.
	 * @param name A string, the name of the filter to move the filter wheel to.
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#addParameter(java.lang.String,java.lang.String)
	 */
	public void setFilterName(String name)
	{
		logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			   ":setFilterName:Set filter name to:"+name);
		endPoint.addParameter("filter_name",name);
	}
	
	/**
	 * Return the message string returned by the Flask end-point.
	 * @return The message as a string returned by the Flask end-point 
	 *         (with the JSON key 'message').
	 * @see #endPoint
	 * @exception JSONException Thrown if the key is not found or if the value is not a string.
	 */
	public String getMessage() throws org.json.JSONException
	{
		return endPoint.getReturnValueString("message");
	}
	
	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		SetFilterPositionByNameCommand command = null;
		String hostname = null;
		int portNumber = 5101;
		String name;
		
		if(args.length != 3)
		{
			System.out.println("java ngat.loci.filterwheel.SetFilterPositionByNameCommand <hostname> <port number> <filter name>");
			System.exit(1);
		}
		try
		{
			hostname = args[0];
			portNumber = Integer.parseInt(args[1]);
			name = args[2];
			command = new SetFilterPositionByNameCommand();
			command.initialiseLogging();
			command.setAddress(hostname);
			command.setPortNumber(portNumber);
			command.setFilterName(name);
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("SetFilterPositionByNameCommand: Command failed.");
				command.getRunException().printStackTrace(System.err);
				System.exit(1);
			}
			System.out.println("Finished:"+command.getCommandFinished());
			System.out.println("Http Response Code (200 on success):"+command.getHttpResponseCode());
			System.out.println("Return Status:"+command.getReturnStatus());
			System.out.println("Is Return Status Success:"+command.isReturnStatusSuccess());
			System.out.println("Message:"+command.getMessage());
		}
		catch(Exception e)
		{
			e.printStackTrace(System.err);
			System.exit(1);
		}
		System.exit(0);
	}
}
