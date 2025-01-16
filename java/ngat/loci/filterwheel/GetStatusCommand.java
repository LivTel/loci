// GetStatusCommand.java
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
 * Invoke the Loci filter wheel Flask end-point 'getStatus'.
 * @author Chris Mottram
 * @version $Revision$
 */
public class GetStatusCommand extends Command implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Default constructor. Call's the Command super-class constructor.
	 * Then sets the Flask end-point name to "getStatus" and the end-point to a "GET" end-point.
	 * @see #logger
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#setFlaskEndPointName
	 * @see ngat.flask.EndPoint#setDoGet
	 */
	public GetStatusCommand()
	{
		super();
		logger = LogManager.getLogger(this);
		endPoint.setFlaskEndPointName("getStatus");
		endPoint.setDoGet();
	}

	/**
	 * Return the filter wheel connection state as returned by the Flask getStatus end-point.
	 * @return An string, a string version of the connection state as
	 *         returned by the Flask getStatus end-point 
	 *         (with the JSON key 'connection'). 
	 * @see #endPoint
	 * @exception JSONException Thrown if the key is not found.
	 */
	public String getConnectionStatus() throws org.json.JSONException
	{
		return endPoint.getReturnValueString("connection");
	}
	
	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		GetStatusCommand command = null;
		String hostname = null;
		int portNumber = 5101;

		if(args.length != 2)
		{
			System.out.println("java ngat.loci.filterwheel.GetStatusCommand <hostname> <port number>");
			System.exit(1);
		}
		try
		{
			hostname = args[0];
			portNumber = Integer.parseInt(args[1]);
			command = new GetStatusCommand();
			command.initialiseLogging();
			command.setAddress(hostname);
			command.setPortNumber(portNumber);
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("GetStatusCommand: Command failed.");
				command.getRunException().printStackTrace(System.err);
				System.exit(1);
			}
			System.out.println("Finished:"+command.getCommandFinished());
			System.out.println("Http Response Code (200 on success):"+command.getHttpResponseCode());
			System.out.println("Return Status:"+command.getReturnStatus());
			System.out.println("Is Return Status Success:"+command.isReturnStatusSuccess());
			System.out.println("Connection Status:"+command.getConnectionStatus());
		}
		catch(Exception e)
		{
			e.printStackTrace(System.err);
			System.exit(1);
		}
		System.exit(0);
	}
}
