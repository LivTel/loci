// SetFilterPositionCommand.java
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
 * Invoke the Loci filter wheel Flask end-point 'setFilterPosition'.
 * @author Chris Mottram
 * @version $Revision$
 */
public class SetFilterPositionCommand extends Command implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Default constructor. Call's the Command super-class constructor.
	 * Then sets the Flask end-point name to "setFilterPosition" and the end-point to a "POST" end-point.
	 * @see #logger
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#setFlaskEndPointName
	 * @see ngat.flask.EndPoint#setDoPost
	 */
	public SetFilterPositionCommand()
	{
		super();
		logger = LogManager.getLogger(this);
		endPoint.setFlaskEndPointName("setFilterPosition");
		endPoint.setDoPost();
	}

	/**
	 * Set the filter wheel to move to the specified position.
	 * @param p An integer, the position to move the filter wheel to (in the range 1..5).
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#addParameter(java.lang.String,int)
	 */
	public void setFilterPosition(int p)
	{
		logger.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			   ":setFilterPosition:Set filter position to:"+p);
		endPoint.addParameter("filter_position",p);
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
		SetFilterPositionCommand command = null;
		String hostname = null;
		int portNumber = 5101;
		int position;
		
		if(args.length != 3)
		{
			System.out.println("java ngat.loci.filterwheel.SetFilterPositionCommand <hostname> <port number> <position:1..5>");
			System.exit(1);
		}
		try
		{
			hostname = args[0];
			portNumber = Integer.parseInt(args[1]);
			position = Integer.parseInt(args[2]);
			command = new SetFilterPositionCommand();
			command.initialiseLogging();
			command.setAddress(hostname);
			command.setPortNumber(portNumber);
			command.setFilterPosition(position);
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("SetFilterPositionCommand: Command failed.");
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
