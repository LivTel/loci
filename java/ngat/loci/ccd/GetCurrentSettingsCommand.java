// GetCurrentSettingsCommand.java&
package ngat.loci.ccd;

import java.io.*;
import java.lang.*;
import java.net.*;

import org.json.*;

import ngat.flask.EndPoint;
import ngat.util.logging.*;
import ngat.loci.ccd.Command;

/**
 * Invoke the Loci CCD Flask end-point 'getCurrentSettings'.
 * @author Chris Mottram
 * @version $Revision$
 */
public class GetCurrentSettingsCommand extends Command implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	
	/**
	 * Default constructor. Call's the Command super-class constructor.
	 * Then sets the Flask end-point name to "getCurrentSettings" and the end-point to a "GET" end-point.
	 * @see #logger
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#setFlaskEndPointName
	 * @see ngat.flask.EndPoint#setDoGet
	 */
	public GetCurrentSettingsCommand()
	{
		super();
		logger = LogManager.getLogger(this);
		endPoint.setFlaskEndPointName("getCurrentSettings");
		endPoint.setDoGet();
	}

	/**
	 * Return the preamp gain returned by the Flask getCurrentSettings end-point.
	 * @return An integer, the preamp gain returned by the Flask getCurrentSettings end-point 
	 *         (with the JSON key 'preamp_gain').
	 * @see #endPoint
	 * @exception JSONException Thrown if the key is not found.
	 */
	public int getPreampGain() throws org.json.JSONException
	{
		return endPoint.getReturnValueInteger("preamp_gain");
	}
	
	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		GetCurrentSettingsCommand command = null;
		String hostname = null;
		int portNumber = 5100;

		if(args.length != 2)
		{
			System.out.println("java ngat.loci.ccd.GetCurrentSettingsCommand <hostname> <port number>");
			System.exit(1);
		}
		try
		{
			hostname = args[0];
			portNumber = Integer.parseInt(args[1]);
			command = new GetCurrentSettingsCommand();
			command.initialiseLogging();
			command.setAddress(hostname);
			command.setPortNumber(portNumber);
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("GetCurrentSettingsCommand: Command failed.");
				command.getRunException().printStackTrace(System.err);
				System.exit(1);
			}
			System.out.println("Finished:"+command.getCommandFinished());
			System.out.println("Http Response Code (200 on success):"+command.getHttpResponseCode());
			//System.out.println("Return Status:"+command.getReturnStatus());
			//System.out.println("Is Return Status Success:"+command.isReturnStatusSuccess());
			System.out.println("Returned Data:"+command.endPoint.getReturnValues());
			System.out.println("Preamp Gain:"+command.getPreampGain());
		}
		catch(Exception e)
		{
			e.printStackTrace(System.err);
			System.exit(1);
		}
		System.exit(0);
	}
}
