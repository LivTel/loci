// GetTemperatureCommand.java
package ngat.loci.ccd;

import java.io.*;
import java.lang.*;
import java.net.*;

import org.json.*;

import ngat.flask.EndPoint;
import ngat.util.logging.*;
import ngat.loci.ccd.Command;

/**
 * Invoke the Loci CCD Flask end-point 'getTemperature'.
 * @author Chris Mottram
 * @version $Revision$
 */
public class GetTemperatureCommand extends Command implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * Constant defining the log level to send for error messages generated by instances of this class.
	 */
	public final static int LOG_LEVEL_ERROR = 1;
	
	/**
	 * Default constructor. Call's the Command super-class constructor.
	 * Then sets the Flask end-point name to "getTemperature" and the end-point to a "GET" end-point.
	 * @see #logger
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#setFlaskEndPointName
	 * @see ngat.flask.EndPoint#setDoGet
	 */
	public GetTemperatureCommand()
	{
		super();
		logger = LogManager.getLogger(this);
		endPoint.setFlaskEndPointName("getTemperature");
		endPoint.setDoGet();
	}

	/**
	 * Return the temperature returned by the Flask getTemperature end-point.
	 * @return A double, the temperature returned by the Flask getTemperature end-point 
	 *         (with the JSON key 'temeprature').
	 * @see #endPoint
	 * @exception JSONException Thrown if the key is not found or if the value cannot be converted to a double.
	 */
	public double getTemperature() throws org.json.JSONException
	{
		return endPoint.getReturnValueDouble("temperature");
	}
	
	/**
	 * Return whether the cooler is enabled as returned by the Flask getTemperature end-point.
	 * @return A boolean, whether the cooler is enabled as returned by the Flask getTemperature end-point 
	 *         (with the JSON key 'cooling_enabled'). This returns an integer which is converted into a boolean.
	 * @see #endPoint
	 * @exception JSONException Thrown if the key is not found or if the value is not an integer.
	 * @exception IllegalArgumentException Thrown if the 'cooling_enabled' value is not a valid integer (either 0 or 1).
	 */
	public boolean getCoolingEnabled() throws org.json.JSONException, IllegalArgumentException
	{
		boolean coolingEnabled;
		int coolingEnabledInt;

		coolingEnabledInt = endPoint.getReturnValueInteger("cooling_enabled");
		if(coolingEnabledInt == 0)
			coolingEnabled = false;
		else if(coolingEnabledInt == 1)
			coolingEnabled = true;
		else
		{
			throw new IllegalArgumentException(this.getClass().getName()+
							   ":getCoolingEnabled:cooling_enabled returned illegal integer:"+coolingEnabledInt);
		}
		return coolingEnabled;
	}
	
	/**
	 * Return the cooling status returned by the Flask getTemperature end-point.
	 * @return A string, the cooling status returned by the Flask getTemperature end-point 
	 *         (with the JSON key 'cooling_status').
	 * @see #endPoint
	 * @exception JSONException Thrown if the key is not found.
	 */
	public String getCoolingStatus() throws org.json.JSONException
	{
		return endPoint.getReturnValueString("cooling_status");
	}
	
	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		GetTemperatureCommand command = null;
		String hostname = null;
		int portNumber = 5100;

		if(args.length != 2)
		{
			System.out.println("java ngat.loci.ccd.GetTemperatureCommand <hostname> <port number>");
			System.exit(1);
		}
		try
		{
			hostname = args[0];
			portNumber = Integer.parseInt(args[1]);
			command = new GetTemperatureCommand();
			command.initialiseLogging();
			command.setAddress(hostname);
			command.setPortNumber(portNumber);
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("GetTemperatureCommand: Command failed.");
				command.getRunException().printStackTrace(System.err);
				System.exit(1);
			}
			System.out.println("Finished:"+command.getCommandFinished());
			System.out.println("Http Response Code (200 on success):"+command.getHttpResponseCode());
			System.out.println("Return Status:"+command.getReturnStatus());
			System.out.println("Is Return Status Success:"+command.isReturnStatusSuccess());
			System.out.println("Temperature:"+command.getTemperature());
			System.out.println("Cooling enabled:"+command.getCoolingEnabled());
			System.out.println("Cooling status:"+command.getCoolingStatus());
		}
		catch(Exception e)
		{
			e.printStackTrace(System.err);
			System.exit(1);
		}
		System.exit(0);
	}
}
