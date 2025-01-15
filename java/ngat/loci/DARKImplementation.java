// DARKImplementation.java
// $Id$
package ngat.loci;

import java.lang.*;
import java.text.*;
import java.util.*;

import ngat.fits.*;
import ngat.loci.ccd.*;
import ngat.message.base.*;
import ngat.message.base.*;
import ngat.message.ISS_INST.*;
import ngat.util.logging.*;

/**
 * This class provides the implementation for the DARK command sent to a server using the
 * Java Message System.
 * @author Chris Mottram
 * @version $Revision: DARKImplementation.java $
 * @see ngat.loci.CALIBRATEImplementation
 */
public class DARKImplementation extends CALIBRATEImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");

	/**
	 * Constructor.
	 */
	public DARKImplementation()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.DARK&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.DARK";
	}

	/**
	 * This method gets the DARK command's acknowledge time. This returns the server connection threads 
	 * default acknowledge time plus the status's max readout time plus the dark exposure time.
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set.
	 * @see #serverConnectionThread
	 * @see #status
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see LociStatus#getMaxReadoutTime
	 * @see LociTCPServerConnectionThread#getDefaultAcknowledgeTime
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		ACK acknowledge = null;
		DARK darkCommand = (DARK)command;
		int ackTime=0;

		ackTime = darkCommand.getExposureTime()+status.getMaxReadoutTime();
		loci.log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
			 ":calculateAcknowledgeTime:ackTime = "+ackTime);
		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(ackTime+serverConnectionThread.getDefaultAcknowledgeTime());
		return acknowledge;
	}
	
	/**
	 * This method implements the DARK command. 
	 * <ul>
	 * <li>clearFitsHeaders is called.
	 * <li>setFitsHeaders is called to get some FITS headers from the properties files and add them to loci-crtl
	 *     CCD Flask layer.
	 * <li>setFilterWheelFitsHeaderss is called to get the current filter wheel position, and set some FITS headers based on this.
	 * <li>getFitsHeadersFromISS is called to gets some FITS headers from the ISS (RCS). 
	 *     These are sent on to the loci-crtl CCD Flask layer.
	 * <li>We send a takeDarkFrame command to the loci-crtl CCD Flask layer, which returns the generated
	 *     Dark image filename.
	 * <li>The done object is setup, and the generated filename returned. 
	 * </ul>
	 * @see #testAbort
	 * @see ngat.loci.LociStatus#setExposureCount
	 * @see ngat.loci.LociStatus#setExposureNumber
	 * @see ngat.loci.CALIBRATEImplementation#sendTakeDarkFrameCommand
	 * @see ngat.loci.HardwareImplementation#clearFitsHeaders
	 * @see ngat.loci.HardwareImplementation#setFitsHeaders
	 * @see ngat.loci.HardwareImplementation#setFilterWheelFitsHeaders
	 * @see ngat.loci.HardwareImplementation#getFitsHeadersFromISS
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		DARK darkCommand = (DARK)command;
		DARK_DONE darkDone = new DARK_DONE(command.getId());
		String filename = null;
		
		loci.log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":processCommand:Started.");
		if(testAbort(darkCommand,darkDone) == true)
			return darkDone;
	// setup exposure status.
		status.setExposureCount(1);
		status.setExposureNumber(0);
		// get fits headers
		try
		{
			clearFitsHeaders();
		}
		catch(Exception e )
		{
			loci.error(this.getClass().getName()+":processCommand:clearFitsHeaders failed:",e);
			darkDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+901);
			darkDone.setErrorString(this.getClass().getName()+
						   ":processCommand:clearFitsHeaders failed:"+e);
			darkDone.setSuccessful(false);
			return darkDone;
		}			
		loci.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			   ":processCommand:getting FITS headers from properties.");
		if(setFitsHeaders(darkCommand,darkDone) == false)
			return darkDone;
		if(setFilterWheelFitsHeaders(darkCommand,darkDone) == false)
			return darkDone;
		loci.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			   ":processCommand:Setting per-frame FITS headers.");
		if(setPerFrameFitsHeaders(darkCommand,darkDone,FitsHeaderDefaults.OBSTYPE_VALUE_DARK,
					  darkCommand.getExposureTime(),1,1) == false)
				return darkDone;
		loci.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			   ":processCommand:getting FITS headers from ISS.");
		if(getFitsHeadersFromISS(darkCommand,darkDone) == false)
			return darkDone;
		if(testAbort(darkCommand,darkDone) == true)
			return darkDone;
		// call take dark frame command
		loci.log(Logging.VERBOSITY_INTERMEDIATE,this.getClass().getName()+
			   ":processCommand:Starting sendTakeDarkFrameCommand.");
		try
		{
			filename = sendTakeDarkFrameCommand(darkCommand.getExposureTime(),true);
		}
		catch(Exception e )
		{
			loci.error(this.getClass().getName()+":processCommand:sendTakeDarkFrameCommand failed:",e);
			darkDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+900);
			darkDone.setErrorString(this.getClass().getName()+
						   ":processCommand:sendTakeDarkFrameCommand failed:"+e);
			darkDone.setSuccessful(false);
			return darkDone;
		}
		// update status
		status.setExposureNumber(1);
		status.setExposureFilename(filename);
		// setup return values.
		// setup dark done
		darkDone.setFilename(filename);
		// standard success values
		darkDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_NO_ERROR);
		darkDone.setErrorString("");
		darkDone.setSuccessful(true);
	// return done object.
		loci.log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+":processCommand:finished.");
		return darkDone;
	}
}
