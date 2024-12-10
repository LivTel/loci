// GetExposureProgressCommand.java
// $Id$
package ngat.loci.ccd;

import java.io.*;
import java.lang.*;
import java.net.*;

import org.json.*;

import ngat.flask.EndPoint;
import ngat.util.logging.*;
import ngat.loci.ccd.Command;

/**
 * Invoke the Loci CCD Flask end-point 'getExposureProgress'.
 * @author Chris Mottram
 * @version $Revision$
 */
public class GetExposureProgressCommand extends Command implements Runnable
{	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	
	/**
	 * Default constructor. Call's the Command super-class constructor.
	 * Then sets the Flask end-point name to "getExposureProgress" and the end-point to a "GET" end-point.
	 * @see #logger
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#setFlaskEndPointName
	 * @see ngat.flask.EndPoint#setDoGet
	 */
	public GetExposureProgressCommand()
	{
		super();
		logger = LogManager.getLogger(this);
		endPoint.setFlaskEndPointName("getExposureProgress");
		endPoint.setDoGet();
	}

	/**
	 * Return the elapsed time the current exposure has been underway status 
	 * returned by the Flask getExposureProgress end-point.
	 * @return A double, the elapsed time the current exposure has been underway in decimal seconds
	 *         returned by the Flask getExposureProgress end-point (with the JSON key 'time_elapsed').
	 * @see #endPoint
	 * @exception JSONException Thrown if the key is not found or if the value cannot be converted to a double.
	 */
	public double getElapsedTime() throws org.json.JSONException
	{
		return endPoint.getReturnValueDouble("time_elapsed");
	}
	
	/**
	 * Return the remaining time the current exposure has left status 
	 * returned by the Flask getExposureProgress end-point.
	 * @return A double, the remaining time the current exposure has left in decimal seconds
	 *         returned by the Flask getExposureProgress end-point (with the JSON key 'time_remaining').
	 * @see #endPoint
	 * @exception JSONException Thrown if the key is not found or if the value cannot be converted to a double.
	 */
	public double getRemainingTime() throws org.json.JSONException
	{
		return endPoint.getReturnValueDouble("time_remaining");
	}
	
	/**
	 * Return the total exposure time the current exposure is configured to expose for
	 * returned by the Flask getExposureProgress end-point.
	 * @return A double, the total exposure time the current exposure is configured to expose for in decimal seconds
	 *         returned by the Flask getExposureProgress end-point (with the JSON key 'exposure_time').
	 * @see #endPoint
	 * @exception JSONException Thrown if the key is not found or if the value cannot be converted to a double.
	 */
	public double getExposureTime() throws org.json.JSONException
	{
		return endPoint.getReturnValueDouble("exposure_time");
	}
	
	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		GetExposureProgressCommand command = null;
		String hostname = null;
		int portNumber = 5100;

		if(args.length != 2)
		{
			System.out.println("java ngat.loci.ccd.GetExposureProgressCommand <hostname> <port number>");
			System.exit(1);
		}
		try
		{
			hostname = args[0];
			portNumber = Integer.parseInt(args[1]);
			command = new GetExposureProgressCommand();
			command.initialiseLogging();
			command.setAddress(hostname);
			command.setPortNumber(portNumber);
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("GetExposureProgressCommand: Command failed.");
				command.getRunException().printStackTrace(System.err);
				System.exit(1);
			}
			System.out.println("Finished:"+command.getCommandFinished());
			System.out.println("Http Response Code (200 on success):"+command.getHttpResponseCode());
			System.out.println("Return Status:"+command.getReturnStatus());
			System.out.println("Is Return Status Success:"+command.isReturnStatusSuccess());
			System.out.println("Exposure Length:"+command.getExposureTime()+" s.");
			System.out.println("Exposure Length Elapsed:"+command.getElapsedTime()+" s.");
			System.out.println("Exposure Length Remaining:"+command.getRemainingTime()+" s.");
		}
		catch(Exception e)
		{
			e.printStackTrace(System.err);
			System.exit(1);
		}
		System.exit(0);
	}
}
