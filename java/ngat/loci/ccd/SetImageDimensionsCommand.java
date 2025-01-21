// SetImageDimensionsCommand.java
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
 * Invoke the Loci CCD Flask end-point 'setImageDimensions'.
 * @author Chris Mottram
 * @version $Revision$
 */
public class SetImageDimensionsCommand extends Command implements Runnable
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	
	/**
	 * Default constructor. Call's the Command super-class constructor.
	 * Then sets the Flask end-point name to "setImageDimenions" and the end-point to a "POST" end-point.
	 * @see #logger
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#setFlaskEndPointName
	 * @see ngat.flask.EndPoint#setDoPost
	 */
	public SetImageDimensionsCommand()
	{
		super();
		logger = LogManager.getLogger(this);
		endPoint.setFlaskEndPointName("setImageDimensions");
		endPoint.setDoPost();
	}

	/**
	 * Set the horizontal binning passed to the Flask setImageDimensions end-point. 
	 * @param value The horizontal binning factor as an integer. This should be at least '1'. 
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#addParameter(java.lang.String,int)
	 */
	public void setHorizontalBinning(int value)
	{
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":setHorizontalBinning:Set value to:"+value);
		endPoint.addParameter("horizontal_binning",value);
	}
	
	/**
	 * Set the vertical binning passed to the Flask setImageDimensions end-point. 
	 * @param value The vertical binning factor as an integer. This should be at least '1'. 
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#addParameter(java.lang.String,int)
	 */
	public void setVerticalBinning(int value)
	{
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":setVerticalBinning:Set value to:"+value);
		endPoint.addParameter("vertical_binning",value);
	}
	
	/**
	 * Set the horizontal/x coordinate of the start pixel of a sub-window passed to the Flask 
	 * setImageDimensions end-point. 
	 * @param value The horizontal/x coordinate of the start pixel of a sub-window as an integer. 
	 *        This should be at least '1', and less than the size of the chip ('2048'). It is in unbinned pixels. 
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#addParameter(java.lang.String,int)
	 */
	public void setHorizontalStart(int value)
	{
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":setHorizontalStart:Set value to:"+value);
		endPoint.addParameter("horizontal_start",value);
	}
	
	/**
	 * Set the vertical/y coordinate of the start pixel of a sub-window passed to the Flask 
	 * setImageDimensions end-point. 
	 * This parameter is optional.
	 * @param value The vertical/y coordinate of the start pixel of a sub-window as an integer. 
	 *        This should be at least '1', and less than the size of the chip ('2048'). It is in unbinned pixels. 
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#addParameter(java.lang.String,int)
	 */
	public void setVerticalStart(int value)
	{
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":setVerticalStart:Set value to:"+value);
		endPoint.addParameter("vertical_start",value);
	}
	
	/**
	 * Set the horizontal/x coordinate of the end pixel of a sub-window passed to the Flask 
	 * setImageDimensions end-point. 
	 * @param value The horizontal/x coordinate of the end pixel of a sub-window as an integer. 
	 *        This should be at least '1', and less than the size of the chip ('2048'). 
	 *        It should have a value larger than the horizontal start coordinate. It is in unbinned pixels. 
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#addParameter(java.lang.String,int)
	 */
	public void setHorizontalEnd(int value)
	{
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":setHorizontalEnd:Set value to:"+value);
		endPoint.addParameter("horizontal_end",value);
	}
	
	/**
	 * Set the vertical/y coordinate of the end pixel of a sub-window passed to the Flask 
	 * setImageDimensions end-point. 
	 * @param value The vertical/y coordinate of the end pixel of a sub-window as an integer. 
	 *        This should be at least '1', and less than the size of the chip ('2048'). 
	 *        It should have a value larger than the vertical start coordinate. It is in unbinned pixels. 
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#addParameter(java.lang.String,int)
	 */
	public void setVerticalEnd(int value)
	{
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":setVerticalEnd:Set value to:"+value);
		endPoint.addParameter("vertical_end",value);
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
		SetImageDimensionsCommand command = null;
		String hostname = null;
		int portNumber = 5100;
		int xBin,yBin,startX=0,startY=0,endX=0,endY=0;

		if((args.length != 4)&&(args.length != 8))
		{
			System.out.println("java ngat.loci.ccd.SetImageDimensionsCommand <hostname> <port number> <xbin> <ybin> [<startX> <startY> <endX> <endY>]");
			System.exit(1);
		}
		try
		{
			// parse arguments
			hostname = args[0];
			portNumber = Integer.parseInt(args[1]);
			xBin = Integer.parseInt(args[2]);
			yBin = Integer.parseInt(args[3]);
			if(args.length == 8)
			{
				startX = Integer.parseInt(args[4]);
				startY = Integer.parseInt(args[5]);
				endX = Integer.parseInt(args[6]);
				endY = Integer.parseInt(args[7]);
			}
			// setup command
			command = new SetImageDimensionsCommand();
			command.initialiseLogging();
			command.setAddress(hostname);
			command.setPortNumber(portNumber);
			command.setHorizontalBinning(xBin);
			command.setVerticalBinning(yBin);
			if(args.length == 8)
			{
				command.setHorizontalStart(startX);
				command.setVerticalStart(startY);
				command.setHorizontalEnd(endX);
				command.setVerticalEnd(endY);
			}
			// execute command
			command.run();
			// check for exception
			if(command.getRunException() != null)
			{
				System.err.println("SetImageDimensionsCommand: Command failed.");
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
