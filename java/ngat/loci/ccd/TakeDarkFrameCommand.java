// TakeDarkFrameCommand.java
package ngat.loci.ccd;

import java.io.*;
import java.lang.*;
import java.net.*;

import org.json.*;

import ngat.flask.EndPoint;
import ngat.util.logging.*;
import ngat.loci.ccd.Command;

/**
 * Invoke the Loci CCD Flask end-point 'takeDarkFrame'.
 * @author Chris Mottram
 * @version $Revision$
 */
public class TakeDarkFrameCommand extends Command implements Runnable
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
	 * Then sets the Flask end-point name to "takeDarkFrame" and the end-point to a "POST" end-point.
	 * @see #logger
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#setFlaskEndPointName
	 * @see ngat.flask.EndPoint#setDoPost
	 */
	public TakeDarkFrameCommand()
	{
		super();
		logger = LogManager.getLogger(this);
		endPoint.setFlaskEndPointName("takeDarkFrame");
		endPoint.setDoPost();
	}

	/**
	 * Set the exposure length passed to the Flask takeDarkFrame end-point. 
	 * This is the length of time to do the dark frame for, in decimal seconds.
	 * @param exposureLength A double, the dark exposure length in decimal seconds.
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#addParameter(java.lang.String,double)
	 */
	public void setExposureLength(double exposureLength)
	{
		endPoint.addParameter("exposure_time",exposureLength);
	}
	
	/**
	 * Set whether the frame to be acquired is the first one in a multrun or not.
	 * @param isMultrunStart A boolean, true if this frame is the first one to be acquired in the Multrun, and
	 *        false if it is NOT the first frame in a multrun.
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#addParameter(java.lang.String,java.lang.String)
	 */
	public void setMultrun(boolean isMultrunStart)
	{
		if(isMultrunStart)
			endPoint.addParameter("multrun","start");
		else
			endPoint.addParameter("multrun","next");
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
	 * Get the filename generated by the Flask takeDarkFrame end-point command. 
	 * This is the FITS filename the read out image data was put into.
	 * @return filename A string, the FITS filename.
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#getReturnValueString(java.lang.String)
	 * @exception JSONException Thrown if the key is not found or if the value is not a string.
	 */
	public String getFilename() throws org.json.JSONException
	{
		return endPoint.getReturnValueString("filename");
	}
	
	/**
	 * Main test program.
	 * @param args The argument list.
	 */
	public static void main(String args[])
	{
		TakeDarkFrameCommand command = null;
		String hostname = null;
		String filename = null;;
		double exposureLength;
		int portNumber = 5100;
		boolean isMultrunStart = false;

		if(args.length < 3)
		{
			System.out.println("java ngat.loci.ccd.TakeDarkFrameCommand <hostname> <port number> <exposurelength s> [multrun:start|next]");
			System.out.println("\tThe optional third parameter is one of 'start' or 'next' and determines whether a new multrun is started or a new frame is acquired for an already started multrun, which affects the returned generated filename.");
			System.exit(1);
		}
		try
		{
			hostname = args[0];
			portNumber = Integer.parseInt(args[1]);
			exposureLength = Double.parseDouble(args[2]);
			if(args.length > 3)
			{
				if(args[3].equalsIgnoreCase("start"))
					isMultrunStart = true;
				else if(args[3].equalsIgnoreCase("next"))
					isMultrunStart = false;
				else
				{
					throw new Exception("TakeDarkFrameCommand:main:multrun parameter is not one of 'start' or 'next':"+
							    args[3]);
				}
			}
			command = new TakeDarkFrameCommand();
			command.initialiseLogging();
			command.setAddress(hostname);
			command.setPortNumber(portNumber);
			command.setExposureLength(exposureLength);
			if(args.length > 3)
				command.setMultrun(isMultrunStart);
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("TakeDarkFrameCommand: Command failed.");
				command.getRunException().printStackTrace(System.err);
				System.exit(1);
			}
			System.out.println("Finished:"+command.getCommandFinished());
			System.out.println("Http Response Code (200 on success):"+command.getHttpResponseCode());
			System.out.println("Return Status:"+command.getReturnStatus());
			System.out.println("Is Return Status Success:"+command.isReturnStatusSuccess());
			System.out.println("Filename:"+command.getFilename());
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
