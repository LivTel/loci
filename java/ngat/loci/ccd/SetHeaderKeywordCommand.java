// SetHeaderKeywordCommand.java
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
 * Invoke the Loci CCD Flask end-point 'setHeaderKeyword'.
 * @author Chris Mottram
 * @version $Revision$
 */
public class SetHeaderKeywordCommand extends Command implements Runnable
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
	 * Then sets the Flask end-point name to "setHeaderKeyword" and the end-point to a "POST" end-point.
	 * @see #logger
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#setFlaskEndPointName
	 * @see ngat.flask.EndPoint#setDoPost
	 */
	public SetHeaderKeywordCommand()
	{
		super();
		logger = LogManager.getLogger(this);
		endPoint.setFlaskEndPointName("setHeaderKeyword");
		endPoint.setDoPost();
	}

	/**
	 * Set the keyword of the FITS header card passed to the Flask setHeaderKeyword end-point. 
	 * This is mandatory for all FITS header keywords.
	 * @param keyword A string with the name of the keyword. This is nomally all capitals (and possibly numbers)
	 *        of a length of 8 or shorter.
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#addParameter(java.lang.String,java.lang.String)
	 */
	public void setKeyword(String keyword)
	{
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":setKeyword:Set keyword to:"+keyword);
		endPoint.addParameter("keyword",keyword);
	}
	
	/**
	 * Set the value of the FITS header card passed to the Flask setHeaderKeyword end-point to a string value. 
	 * A value is mandatory for all FITS header keywords added using this end-point.
	 * @param value A string which is the value string associated with the keyword. Only the first 69 characters
	 *        will end up in the FITS header.
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#addParameter(java.lang.String,java.lang.String)
	 */
	public void setValue(String value)
	{
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":setValue:Set value to:"+value);
		endPoint.addParameter("value",value);
	}
	
	/**
	 * Set the value of the FITS header card passed to the Flask setHeaderKeyword end-point to an integer value. 
	 * A value is mandatory for all FITS header keywords added using this end-point.
	 * @param value An integer which is the value associated with the keyword. 
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#addParameter(java.lang.String,int)
	 */
	public void setValue(int value)
	{
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":setValue:Set value to:"+value);
		endPoint.addParameter("value",value);
	}
	
	/**
	 * Set the value of the FITS header card passed to the Flask setHeaderKeyword end-point to a double float value. 
	 * A value is mandatory for all FITS header keywords added using this end-point.
	 * @param value A double which is the value associated with the keyword. 
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#addParameter(java.lang.String,double)
	 */
	public void setValue(double value)
	{
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":setValue:Set value to:"+value);
		endPoint.addParameter("value",value);
	}
	
	/**
	 * Set the value of the FITS header card passed to the Flask setHeaderKeyword end-point to a boolean value. 
	 * A value is mandatory for all FITS header keywords added using this end-point.
	 * @param value A boolean which is the value associated with the keyword. 
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#addParameter(java.lang.String,boolean)
	 */
	public void setValue(boolean value)
	{
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":setValue:Set value to:"+value);
		endPoint.addParameter("value",value);
	}
	
	/**
	 * Set the units string of the FITS header card passed to the Flask setHeaderKeyword end-point. 
	 * The units are optional for all FITS header keywords added using this end-point.
	 * @param unitsString A string representing the units of the value of the FITS header keyword.
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#addParameter(java.lang.String,java.lang.String)
	 */
	public void setUnits(String unitsString)
	{
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":setUnits:Set value to:"+unitsString);
		endPoint.addParameter("units",unitsString);
	}
	
	/**
	 * Set the comment string of the FITS header card passed to the Flask setHeaderKeyword end-point. 
	 * The comment is optional for all FITS header keywords added using this end-point.
	 * @param commentString A string representing a comment associated with the FITS header keyword.
	 * @see #endPoint
	 * @see ngat.flask.EndPoint#addParameter(java.lang.String,java.lang.String)
	 */
	public void setComment(String commentString)
	{
		logger.log(Logging.VERBOSITY_VERY_VERBOSE,this.getClass().getName()+
			   ":setComment:Set value to:"+commentString);
		endPoint.addParameter("comment",commentString);
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
		SetHeaderKeywordCommand command = null;
		String hostname = null;
		String keywordString = null;
		String valueString = null;
		String valueTypeString = null;
		String commentString = null;
		String unitsString = null;
		int portNumber = 5100;
		int valueInt = 0;
		double valueDouble = 0.0;
		boolean valueBoolean = false;
		
		if(args.length < 5)
		{
			System.out.println("java ngat.loci.ccd.SetHeaderKeywordCommand <hostname> <port number> <keyword> <value> <valuetype> [<comment> <units>]");
			System.out.println("\t<valuetype> should be one of:'string','int','float','boolean'.");
			System.exit(1);
		}
		try
		{
			hostname = args[0];
			portNumber = Integer.parseInt(args[1]);
			keywordString = args[2];
			valueString = args[3];
			valueTypeString = args[4];
			if(args.length >= 6)
				commentString = args[5];
			if(args.length >= 7)
				unitsString = args[6];
			if(valueTypeString.equals("string"))
			{
				// do nothing
			}
			else if(valueTypeString.equals("int"))
			{
				valueInt = Integer.parseInt(valueString);
			}
			else if(valueTypeString.equals("float"))
			{
				valueDouble = Double.parseDouble(valueString);
			}
			else if(valueTypeString.equals("boolean"))
			{
				valueBoolean = Boolean.parseBoolean(valueString);
			}
			else
			{
				System.err.println("SetHeaderKeywordCommand: Illegal value type:"+
						   valueTypeString);
				System.exit(1);
			}
			command = new SetHeaderKeywordCommand();
			command.initialiseLogging();
			command.setAddress(hostname);
			command.setPortNumber(portNumber);
			command.setKeyword(keywordString);
			if(commentString != null)
				command.setComment(commentString);
			if(unitsString != null)
				command.setUnits(unitsString);
			if(valueTypeString.equals("string"))
			{
				command.setValue(valueString);
			}
			else if(valueTypeString.equals("int"))
			{
				command.setValue(valueInt);
			}
			else if(valueTypeString.equals("float"))
			{
				command.setValue(valueDouble);
			}
			else if(valueTypeString.equals("boolean"))
			{
				command.setValue(valueBoolean);
			}
			else
			{
				System.err.println("SetHeaderKeywordCommand: Illegal value type:"+
						   valueTypeString);
				System.exit(1);
			}
			command.run();
			if(command.getRunException() != null)
			{
				System.err.println("SetHeaderKeywordCommand: Command failed.");
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
