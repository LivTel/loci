// GetFilterPositionCommand.java
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
 * Invoke the Loci filter wheel Flask end-point 'getFilterPosition'.
 * @author Chris Mottram
 * @version $Revision$
 */
public class GetFilterPositionCommand extends Command implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Default constructor. Call's the Command super-class constructor.
	 * Then sets the Flask end-point name to "getFilterPosition" and the end-point to a "GET" end-point.
	 * @see #logger
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#setFlaskEndPointName
	 * @see ngat.flask.EndPoint#setDoGet
	 */
	public GetFilterPositionCommand()
	{
		super();
		logger = LogManager.getLogger(this);
		endPoint.setFlaskEndPointName("getFilterPosition");
		endPoint.setDoGet();
	}

	/**
	 * Return the filter wheel position returned by the Flask getFilterPosition end-point.
	 * @return An integer, the filter wheel position returned by the Flask getFilterPosition end-point 
	 *         (with the JSON key 'filter_position'). This should be between 1 and 5.
	 * @see #endPoint
	 * @exception JSONException Thrown if the key is not found, or is not a valid integer.
	 */
	public int getFilterPosition() throws org.json.JSONException
	{
		return endPoint.getReturnValueInteger("filter_position");
	}
	
	/**
	 * Return the filter wheel position returned by the Flask getFilterPosition end-point.
	 * @return An string, the name of the currently selected filter 
	 *         returned by the Flask getFilterPosition end-point 
	 *         (with the JSON key 'filter_name'). 
	 * @see #endPoint
	 * @exception JSONException Thrown if the key is not found.
	 */
	public String getFilterName() throws org.json.JSONException
	{
		return endPoint.getReturnValueString("filter_name");
	}
	
	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		GetFilterPositionCommand command = null;
		String hostname = null;
		int portNumber = 5101;

		if(args.length != 2)
		{
			System.out.println("java ngat.loci.filterwheel.GetFilterPositionCommand <hostname> <port number>");
			System.exit(1);
		}
		try
		{
			hostname = args[0];
			portNumber = Integer.parseInt(args[1]);
			command = new GetFilterPositionCommand();
			command.initialiseLogging();
			command.setAddress(hostname);
			command.setPortNumber(portNumber);
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("GetFilterPositionCommand: Command failed.");
				command.getRunException().printStackTrace(System.err);
				System.exit(1);
			}
			System.out.println("Finished:"+command.getCommandFinished());
			System.out.println("Http Response Code (200 on success):"+command.getHttpResponseCode());
			System.out.println("Return Status:"+command.getReturnStatus());
			System.out.println("Is Return Status Success:"+command.isReturnStatusSuccess());
			System.out.println("Filter Wheel Position:"+command.getFilterPosition());
			System.out.println("Filter Name:"+command.getFilterName());
		}
		catch(Exception e)
		{
			e.printStackTrace(System.err);
			System.exit(1);
		}
		System.exit(0);
	}
}
